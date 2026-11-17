package com.riwi.skillbridge.application.service.knowledge;

import com.riwi.skillbridge.application.port.out.CategoryRepositoryPort;
import com.riwi.skillbridge.application.port.out.EmbeddingPort;
import com.riwi.skillbridge.application.port.out.KnowledgeIndexPort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.domain.knowledge.KnowledgeChunk;
import com.riwi.skillbridge.domain.model.Category;
import com.riwi.skillbridge.domain.model.Offering;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Servicio de aplicación para la gestión, carga inicial y sincronización del índice
 * de conocimiento vectorial (HU-26, FR-015, FR-018).
 * <p>
 * Clase de aplicación pura sin dependencias de org.springframework ni jakarta.
 * El enganche al arranque del ciclo de vida se realiza exclusivamente desde infraestructura.
 */
public class KnowledgeIndexService {

    private static final System.Logger LOGGER = System.getLogger(KnowledgeIndexService.class.getName());

    private final KnowledgeCorpusLoader corpusLoader;
    private final OfferingRepositoryPort offeringRepository;
    private final CategoryRepositoryPort categoryRepository;
    private final EmbeddingPort embeddingPort;
    private final KnowledgeIndexPort indexPort;

    public KnowledgeIndexService(
            KnowledgeCorpusLoader corpusLoader,
            OfferingRepositoryPort offeringRepository,
            CategoryRepositoryPort categoryRepository,
            EmbeddingPort embeddingPort,
            KnowledgeIndexPort indexPort
    ) {
        this.corpusLoader = Objects.requireNonNull(corpusLoader, "corpusLoader no puede ser nulo");
        this.offeringRepository = Objects.requireNonNull(offeringRepository, "offeringRepository no puede ser nulo");
        this.categoryRepository = Objects.requireNonNull(categoryRepository, "categoryRepository no puede ser nulo");
        this.embeddingPort = Objects.requireNonNull(embeddingPort, "embeddingPort no puede ser nulo");
        this.indexPort = Objects.requireNonNull(indexPort, "indexPort no puede ser nulo");
    }

    /** Máximo de textos por llamada de embeddings (Gemini limita el tamaño del lote). */
    static final int EMBEDDING_BATCH_SIZE = 50;

    /**
     * Sincronización de arranque (FR-015, FR-018): deja el índice igual al contenido público actual
     * (corpus .md, ofertas activas y categorías activas) <b>sin recalcular lo que no cambió</b>.
     * <ul>
     *   <li>Calcula embeddings solo de fragmentos nuevos o con {@code contentHash} distinto, en lotes de
     *       {@link #EMBEDDING_BATCH_SIZE}.</li>
     *   <li>Elimina del índice lo que ya no existe (ofertas desactivadas, categorías borradas, secciones retiradas).</li>
     *   <li>Aplica todo en una única transacción: si falla el proveedor de embeddings, el índice anterior queda intacto.</li>
     * </ul>
     *
     * @return número total de fragmentos que quedan indexados
     */
    public int loadInitialIndex() {
        java.util.Map<KnowledgeIndexPort.IndexKey, KnowledgeChunk> desired = new java.util.LinkedHashMap<>();

        List<KnowledgeChunk> corpusChunks = corpusLoader.load();
        if (corpusChunks != null) {
            corpusChunks.forEach(c -> desired.put(keyOf(c), c));
        }

        List<Offering> activeOfferings = offeringRepository.findAllActive();
        if (activeOfferings != null) {
            for (Offering offering : activeOfferings) {
                if (offering != null && offering.active()) {
                    KnowledgeChunk c = toChunk(offering);
                    desired.put(keyOf(c), c);
                }
            }
        }

        List<Category> activeCategories = categoryRepository.findAllActive();
        if (activeCategories != null) {
            for (Category category : activeCategories) {
                if (category != null && category.active()) {
                    KnowledgeChunk c = toChunk(category);
                    desired.put(keyOf(c), c);
                }
            }
        }

        java.util.Map<KnowledgeIndexPort.IndexKey, String> indexed = indexPort.findIndexedHashes();
        if (indexed == null) {
            indexed = java.util.Map.of();
        }

        List<KnowledgeChunk> toEmbed = new ArrayList<>();
        for (java.util.Map.Entry<KnowledgeIndexPort.IndexKey, KnowledgeChunk> e : desired.entrySet()) {
            if (!e.getValue().contentHash().equals(indexed.get(e.getKey()))) {
                toEmbed.add(e.getValue());
            }
        }

        List<KnowledgeIndexPort.IndexedChunk> upserts = new ArrayList<>(toEmbed.size());
        for (int from = 0; from < toEmbed.size(); from += EMBEDDING_BATCH_SIZE) {
            List<KnowledgeChunk> batch = toEmbed.subList(from, Math.min(from + EMBEDDING_BATCH_SIZE, toEmbed.size()));
            List<double[]> vectors = embeddingPort.embed(batch.stream().map(KnowledgeChunk::content).toList());
            if (vectors == null || vectors.size() != batch.size()) {
                throw new IllegalStateException("El proveedor de embeddings devolvió una cantidad de vectores distinta a la solicitada");
            }
            for (int i = 0; i < batch.size(); i++) {
                upserts.add(new KnowledgeIndexPort.IndexedChunk(batch.get(i), vectors.get(i)));
            }
        }

        List<KnowledgeIndexPort.IndexKey> deletions = indexed.keySet().stream()
                .filter(k -> !desired.containsKey(k))
                .toList();

        if (!upserts.isEmpty() || !deletions.isEmpty()) {
            indexPort.applyChanges(upserts, deletions);
        }
        LOGGER.log(System.Logger.Level.INFO,
                "Índice de conocimiento sincronizado: {0} fragmentos ({1} recalculados, {2} reutilizados, {3} eliminados)",
                desired.size(), upserts.size(), desired.size() - upserts.size(), deletions.size());
        return desired.size();
    }

    private static KnowledgeIndexPort.IndexKey keyOf(KnowledgeChunk chunk) {
        return new KnowledgeIndexPort.IndexKey(chunk.sourceId(), chunk.language());
    }

    /**
     * Alias conveniente para {@link #loadInitialIndex()}.
     */
    public int initializeIndex() {
        return loadInitialIndex();
    }

    /**
     * Reindexa una colección de fragmentos recalculando los embeddings ÚNICAMENTE
     * para aquellos fragmentos cuyo contentHash haya cambiado o sea nuevo (FR-018).
     *
     * @param chunks fragmentos a sincronizar
     * @return número de fragmentos cuyos embeddings fueron recalculados e indexados
     */
    public int reindexChunks(List<KnowledgeChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return 0;
        }

        Set<String> existingHashes = indexPort.findExistingHashes();
        if (existingHashes == null) {
            existingHashes = Set.of();
        }

        List<KnowledgeChunk> changedChunks = new ArrayList<>();
        for (KnowledgeChunk chunk : chunks) {
            if (chunk != null && !existingHashes.contains(chunk.contentHash())) {
                changedChunks.add(chunk);
            }
        }

        if (changedChunks.isEmpty()) {
            LOGGER.log(System.Logger.Level.INFO, "Reindexación completada: 0 fragmentos modificados");
            return 0;
        }

        List<String> textsToEmbed = changedChunks.stream()
                .map(KnowledgeChunk::content)
                .toList();

        List<double[]> embeddings = embeddingPort.embed(textsToEmbed);

        for (int i = 0; i < changedChunks.size(); i++) {
            indexPort.upsert(changedChunks.get(i), embeddings.get(i));
        }

        LOGGER.log(System.Logger.Level.INFO, "Reindexación completada: {0} fragmentos actualizados", changedChunks.size());
        return changedChunks.size();
    }

    /**
     * Reindexa un fragmento individual recalculando su embedding únicamente
     * si su contentHash no existe aún en el índice (FR-018).
     *
     * @param chunk fragmento a sincronizar
     * @return true si el embedding fue recalculado e indexado; false si el hash ya existía
     */
    public boolean reindexChunk(KnowledgeChunk chunk) {
        if (chunk == null) {
            return false;
        }

        if (indexPort.existsByContentHash(chunk.contentHash())) {
            LOGGER.log(System.Logger.Level.DEBUG, "Fragmento {0} sin cambios (hash={1}), omitiendo recálculo",
                    chunk.sourceId(), chunk.contentHash());
            return false;
        }

        double[] embedding = embeddingPort.embed(chunk.content());
        indexPort.upsert(chunk, embedding);
        LOGGER.log(System.Logger.Level.INFO, "Fragmento {0} reindexado exitosamente", chunk.sourceId());
        return true;
    }

    /**
     * Sincroniza una oferta de mentoría individual en el índice (FR-015, FR-018).
     * Si la oferta no está activa, no se indexa.
     *
     * @param offering oferta a sincronizar
     * @return true si se recalculó y persistió el fragmento; false de lo contrario
     */
    public boolean reindexOffering(Offering offering) {
        if (offering == null) {
            return false;
        }
        if (!offering.active()) {
            indexPort.deleteBySourceId("offering:" + offering.id());
            LOGGER.log(System.Logger.Level.INFO, "Oferta {0} no activa: se retira del índice público", offering.id());
            return false;
        }
        KnowledgeChunk chunk = toChunk(offering);
        return reindexChunk(chunk);
    }

    /**
     * Reindexa todas las ofertas activas en lote, recalculando únicamente hashes cambiados (FR-018).
     *
     * @return cantidad de ofertas recalculadas
     */
    public int reindexOfferings() {
        List<Offering> active = offeringRepository.findAllActive();
        if (active == null || active.isEmpty()) {
            return 0;
        }
        List<KnowledgeChunk> chunks = active.stream()
                .filter(Offering::active)
                .map(this::toChunk)
                .toList();
        return reindexChunks(chunks);
    }

    /**
     * Sincroniza una categoría individual en el índice (FR-015, FR-018).
     * Si la categoría no está activa, no se indexa.
     *
     * @param category categoría a sincronizar
     * @return true si se recalculó y persistió el fragmento; false de lo contrario
     */
    public boolean reindexCategory(Category category) {
        if (category == null) {
            return false;
        }
        if (!category.active()) {
            indexPort.deleteBySourceId("category:" + category.id());
            LOGGER.log(System.Logger.Level.INFO, "Categoría {0} no activa: se retira del índice público", category.id());
            return false;
        }
        KnowledgeChunk chunk = toChunk(category);
        return reindexChunk(chunk);
    }

    /**
     * Reindexa todas las categorías activas en lote, recalculando únicamente hashes cambiados (FR-018).
     *
     * @return cantidad de categorías recalculadas
     */
    public int reindexCategories() {
        List<Category> active = categoryRepository.findAllActive();
        if (active == null || active.isEmpty()) {
            return 0;
        }
        List<KnowledgeChunk> chunks = active.stream()
                .filter(Category::active)
                .map(this::toChunk)
                .toList();
        return reindexChunks(chunks);
    }

    /**
     * Notificación de eliminación de categoría (FR-015).
     *
     * @param id identificador de la categoría eliminada
     */
    public void deleteCategory(UUID id) {
        indexPort.deleteBySourceId("category:" + id);
        LOGGER.log(System.Logger.Level.INFO, "Categoría {0} eliminada: se retira del índice", id);
    }

    /**
     * Alias conveniente para notificación de categoría eliminada.
     */
    public void onCategoryDeleted(UUID id) {
        deleteCategory(id);
    }

    /**
     * Convierte una entidad {@link Offering} en su correspondiente {@link KnowledgeChunk} de dominio.
     *
     * @param offering oferta a transformar
     * @return fragmento de conocimiento normalizado
     */
    public KnowledgeChunk toChunk(Offering offering) {
        Objects.requireNonNull(offering, "offering no puede ser nulo");
        String sourceId = "offering:" + offering.id();
        String title = offering.title();
        String content = offeringContent(offering);
        String hash = computeContentHash(content);
        String mode = offering.mode() != null ? offering.mode().name() : null;
        Integer durationHours = offering.durationHours() > 0 ? offering.durationHours() : null;
        return new KnowledgeChunk(
                sourceId,
                KnowledgeChunk.SourceType.OFFERING,
                title,
                content,
                KnowledgeChunk.Language.ES,
                hash,
                mode,
                durationHours
        );
    }

    /**
     * Texto indexado de una oferta: título, categoría, modalidad, duración, precio y descripción.
     * Enriquecerlo (no solo la descripción) mejora el emparejamiento léxico y semántico de preguntas como
     * "mentoría en vivo de cloud" o "algo corto y barato de frontend". Es también lo que ve el LLM y el extracto citado.
     * Solo incluye datos públicos del catálogo (FR-004).
     */
    static String offeringContent(Offering offering) {
        StringBuilder sb = new StringBuilder();
        sb.append("Mentoría: ").append(offering.title().trim());
        if (offering.category() != null && !offering.category().isBlank()) {
            sb.append("\nCategoría: ").append(offering.category().trim());
        }
        if (offering.mode() != null) {
            sb.append("\nModalidad: ")
                    .append(offering.mode() == com.riwi.skillbridge.domain.model.MentorshipMode.LIVE
                            ? "en vivo (LIVE)" : "asíncrona (ASYNCHRONOUS)");
        }
        if (offering.durationHours() > 0) {
            sb.append("\nDuración: ").append(offering.durationHours())
                    .append(offering.durationHours() == 1 ? " hora" : " horas");
        }
        if (offering.price() != null) {
            sb.append("\nPrecio: ").append(offering.price().stripTrailingZeros().toPlainString());
        }
        if (offering.description() != null && !offering.description().isBlank()) {
            sb.append("\nDescripción: ").append(offering.description().trim());
        }
        return sb.toString();
    }

    /**
     * Convierte una entidad {@link Category} en su correspondiente {@link KnowledgeChunk} de dominio.
     *
     * @param category categoría a transformar
     * @return fragmento de conocimiento normalizado
     */
    public KnowledgeChunk toChunk(Category category) {
        Objects.requireNonNull(category, "category no puede ser nulo");
        String sourceId = "category:" + category.id();
        String title = category.name();
        String content = category.name().trim();
        String hash = computeContentHash(content);
        return new KnowledgeChunk(
                sourceId,
                KnowledgeChunk.SourceType.CATEGORY,
                title,
                content,
                KnowledgeChunk.Language.ES,
                hash,
                null,
                null
        );
    }

    /**
     * Calcula el hash SHA-256 en formato hexadecimal en minúsculas del contenido textual.
     *
     * @param content texto a procesar
     * @return hash SHA-256 de 64 caracteres hexadecimales
     */
    public static String computeContentHash(String content) {
        if (content == null) {
            throw new IllegalArgumentException("content no puede ser nulo");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(64);
            for (byte b : hash) {
                hexString.append(String.format("%02x", b & 0xff));
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo de hash SHA-256 no disponible", e);
        }
    }
}
