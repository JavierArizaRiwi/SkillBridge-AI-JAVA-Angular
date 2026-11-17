package com.riwi.skillbridge.infrastructure.adapter.out.persistence.knowledge;

import com.riwi.skillbridge.application.port.out.EmbeddingPort;
import com.riwi.skillbridge.application.port.out.KnowledgeCandidate;
import com.riwi.skillbridge.application.port.out.KnowledgeIndexPort;
import com.riwi.skillbridge.domain.knowledge.KnowledgeChunk;
import com.riwi.skillbridge.domain.knowledge.KnowledgeQuery;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

/**
 * Pruebas de integración de persistencia pgvector sobre PostgreSQL real con Testcontainers (HU-26).
 * Verifica los puntos 1 a 5 de la especificación técnica T016.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PgVectorKnowledgeAdapter.class, PgVectorKnowledgeAdapterTest.TestConfig.class})
class PgVectorKnowledgeAdapterTest {

    private static final Logger log = LoggerFactory.getLogger(PgVectorKnowledgeAdapterTest.class);

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg17")
            .withDatabaseName("skillbridge_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("logging.level.org.hibernate.tool.schema", () -> "DEBUG");
        registry.add("logging.level.org.hibernate.orm.schema", () -> "DEBUG");
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        public EmbeddingPort embeddingPort() {
            return new DeterministicEmbeddingPort();
        }
    }

    static class DeterministicEmbeddingPort implements EmbeddingPort {
        private final Map<String, double[]> registry = new HashMap<>();

        public void register(String text, double[] vector) {
            registry.put(text, vector);
        }

        @Override
        public List<double[]> embed(List<String> texts) {
            return texts.stream().map(this::embed).toList();
        }

        @Override
        public double[] embed(String text) {
            if (registry.containsKey(text)) {
                return registry.get(text);
            }
            double[] vector = new double[768];
            Arrays.fill(vector, 0.0);
            int idx = Math.abs(text.hashCode()) % 768;
            vector[idx] = 1.0;
            return vector;
        }
    }

    @Autowired
    private PgVectorKnowledgeAdapter adapter;

    @Autowired
    private JpaKnowledgeChunkRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EmbeddingPort embeddingPort;

    @Test
    @DisplayName("Punto 1: El contexto JPA arranca con spring.jpa.hibernate.ddl-auto: validate y V14 aplicada")
    void shouldStartWithDdlAutoValidateAndValidateSchema() {
        assertThat(adapter).isNotNull();
        assertThat(repository).isNotNull();
        assertThat(entityManager).isNotNull();

        // Verificar que KnowledgeChunkEntity está presente en el metamodelo de Hibernate
        boolean foundKnowledgeChunkEntity = entityManager.getMetamodel().getEntities().stream()
                .anyMatch(e -> e.getJavaType().equals(KnowledgeChunkEntity.class));
        assertThat(foundKnowledgeChunkEntity).isTrue();

        for (var entity : entityManager.getMetamodel().getEntities()) {
            log.info("Entidad JPA gestionada y validada contra Postgres/Flyway: {}", entity.getName());
        }

        // Verificar que la tabla knowledge.knowledge_chunks existe y es accesible
        Object count = entityManager.createNativeQuery("SELECT count(*) FROM knowledge.knowledge_chunks")
                .getSingleResult();
        assertThat(count).isNotNull();
        log.info("Verificación schema validation exitosa. Count actual en knowledge.knowledge_chunks: {}", count);
    }

    @Test
    @DisplayName("Punto 2: upsert por source_id es idempotente y actualiza el content_hash (una sola fila)")
    void shouldUpsertIdempotentlyAndMaintainSingleRow() {
        // Arrange
        String sourceId = "policy-cancellation-01";
        KnowledgeChunk chunkV1 = new KnowledgeChunk(
                sourceId,
                KnowledgeChunk.SourceType.POLICY_DOC,
                "Política de Cancelación v1",
                "Las cancelaciones deben realizarse con al menos 24 horas de antelación.",
                KnowledgeChunk.Language.ES,
                "hash-v1-abc12345",
                "ONLINE",
                1
        );
        double[] vecV1 = createVector(1.0, 0.0);

        // Act 1: Primer upsert (inserción inicial)
        adapter.upsert(chunkV1, vecV1);
        entityManager.flush();

        // Assert 1: Una fila insertada
        assertThat(repository.count()).isEqualTo(1);
        KnowledgeChunkEntity entityV1 = repository.findBySourceId(sourceId).orElseThrow();
        assertThat(entityV1.getSourceId()).isEqualTo(sourceId);
        assertThat(entityV1.getContentHash()).isEqualTo("hash-v1-abc12345");
        assertThat(entityV1.getTitle()).isEqualTo("Política de Cancelación v1");

        // Act 2: Segundo upsert con el mismo source_id pero diferente content_hash y contenido
        KnowledgeChunk chunkV2 = new KnowledgeChunk(
                sourceId,
                KnowledgeChunk.SourceType.POLICY_DOC,
                "Política de Cancelación v2",
                "Las cancelaciones deben realizarse con al menos 48 horas de antelación.",
                KnowledgeChunk.Language.ES,
                "hash-v2-xyz98765",
                "PRESENTIAL",
                2
        );
        double[] vecV2 = createVector(0.8, 0.6);

        adapter.upsert(chunkV2, vecV2);
        entityManager.flush();
        entityManager.clear();

        // Assert 2: Idempotencia - Sigue habiendo exactamente una fila con el ID preservado
        assertThat(repository.count()).isEqualTo(1);
        KnowledgeChunkEntity entityV2 = repository.findBySourceId(sourceId).orElseThrow();
        assertThat(entityV2.getId()).isEqualTo(entityV1.getId());
        assertThat(entityV2.getSourceId()).isEqualTo(sourceId);
        assertThat(entityV2.getContentHash()).isEqualTo("hash-v2-xyz98765");
        assertThat(entityV2.getTitle()).isEqualTo("Política de Cancelación v2");
        assertThat(entityV2.getContent()).isEqualTo("Las cancelaciones deben realizarse con al menos 48 horas de antelación.");
        assertThat(entityV2.getMode()).isEqualTo("PRESENTIAL");
        assertThat(entityV2.getDurationHours()).isEqualTo(2);

        // Verificar index port existsByContentHash
        assertThat(adapter.existsByContentHash("hash-v2-xyz98765")).isTrue();
        assertThat(adapter.existsByContentHash("hash-v1-abc12345")).isFalse();
    }

    @Test
    @DisplayName("Punto 3: replaceIndex reemplaza el índice completo en una transacción dejando solo fragmentos nuevos")
    void shouldReplaceIndexAtomicallyLeavingOnlyNewChunks() {
        // Arrange: Fragmentos iniciales existentes en la base de datos
        KnowledgeChunk initial1 = new KnowledgeChunk(
                "old-doc-1",
                KnowledgeChunk.SourceType.OFFERING,
                "Old Offering 1",
                "Old content 1",
                KnowledgeChunk.Language.ES,
                "hash-old-1"
        );
        KnowledgeChunk initial2 = new KnowledgeChunk(
                "old-doc-2",
                KnowledgeChunk.SourceType.CATEGORY,
                "Old Category 2",
                "Old content 2",
                KnowledgeChunk.Language.EN,
                "hash-old-2"
        );
        adapter.upsert(initial1, createVector(1.0, 0.0));
        adapter.upsert(initial2, createVector(0.0, 1.0));
        entityManager.flush();
        assertThat(repository.count()).isEqualTo(2);

        // Act: Reemplazo atómico con 3 fragmentos completamente nuevos
        KnowledgeChunk new1 = new KnowledgeChunk(
                "new-doc-1",
                KnowledgeChunk.SourceType.OFFERING,
                "New Offering A",
                "New content A",
                KnowledgeChunk.Language.ES,
                "hash-new-1"
        );
        KnowledgeChunk new2 = new KnowledgeChunk(
                "new-doc-2",
                KnowledgeChunk.SourceType.OFFERING,
                "New Offering B",
                "New content B",
                KnowledgeChunk.Language.EN,
                "hash-new-2"
        );
        KnowledgeChunk new3 = new KnowledgeChunk(
                "new-doc-3",
                KnowledgeChunk.SourceType.POLICY_DOC,
                "New Policy C",
                "New content C",
                KnowledgeChunk.Language.MIXED,
                "hash-new-3"
        );

        List<KnowledgeIndexPort.IndexedChunk> newChunks = List.of(
                new KnowledgeIndexPort.IndexedChunk(new1, createVector(0.5, 0.5)),
                new KnowledgeIndexPort.IndexedChunk(new2, createVector(0.7, 0.3)),
                new KnowledgeIndexPort.IndexedChunk(new3, createVector(0.2, 0.8))
        );

        adapter.replaceIndex(newChunks);
        entityManager.clear();

        // Assert: Solo quedan los nuevos fragmentos en la BD
        assertThat(repository.count()).isEqualTo(3);
        assertThat(repository.findBySourceId("old-doc-1")).isEmpty();
        assertThat(repository.findBySourceId("old-doc-2")).isEmpty();
        assertThat(repository.findBySourceId("new-doc-1")).isPresent();
        assertThat(repository.findBySourceId("new-doc-2")).isPresent();
        assertThat(repository.findBySourceId("new-doc-3")).isPresent();

        Set<String> existingHashes = adapter.findExistingHashes();
        assertThat(existingHashes).containsExactlyInAnyOrder("hash-new-1", "hash-new-2", "hash-new-3");
    }

    @Test
    @DisplayName("Punto 4: search devuelve fragmentos ordenados por similitud coseno descendente en [-1, 1]")
    void shouldReturnChunksOrderedByCosineSimilarityWithinRange() {
        // Arrange
        String queryText = "quasar nebula";
        double[] queryVector = createVector(1.0, 0.0);
        ((DeterministicEmbeddingPort) embeddingPort).register(queryText, queryVector);

        // Chunk 1: Idéntica dirección -> cos(0°) = 1.0 -> similitud coseno = 1.0
        KnowledgeChunk chunkIdentical = new KnowledgeChunk(
                "chunk-identical",
                KnowledgeChunk.SourceType.OFFERING,
                "Data Science Specialist",
                "Learn machine learning and data science concepts.",
                KnowledgeChunk.Language.EN,
                "hash-ml-1"
        );
        double[] vecIdentical = createVector(1.0, 0.0);

        // Chunk 2: Ángulo de 60 grados -> cos(60°) = 0.5 -> similitud coseno = 0.5
        KnowledgeChunk chunkModerate = new KnowledgeChunk(
                "chunk-moderate",
                KnowledgeChunk.SourceType.OFFERING,
                "Python Backend",
                "General Python programming course.",
                KnowledgeChunk.Language.EN,
                "hash-py-2"
        );
        double[] vecModerate = createVector(0.5, Math.sqrt(3.0) / 2.0);

        // Chunk 3: Ortogonal (90 grados) -> cos(90°) = 0.0 -> similitud coseno = 0.0
        KnowledgeChunk chunkOrthogonal = new KnowledgeChunk(
                "chunk-orthogonal",
                KnowledgeChunk.SourceType.CATEGORY,
                "Culinary Arts",
                "Master culinary arts and cooking.",
                KnowledgeChunk.Language.ES,
                "hash-cook-3"
        );
        double[] vecOrthogonal = createVector(0.0, 1.0);

        // Chunk 4: Dirección opuesta (180 grados) -> cos(180°) = -1.0 -> similitud coseno = -1.0
        KnowledgeChunk chunkOpposite = new KnowledgeChunk(
                "chunk-opposite",
                KnowledgeChunk.SourceType.POLICY_DOC,
                "Antipodal Domain",
                "Completely unrelated domain chunk.",
                KnowledgeChunk.Language.EN,
                "hash-anti-4"
        );
        double[] vecOpposite = createVector(-1.0, 0.0);

        adapter.upsert(chunkOpposite, vecOpposite);
        adapter.upsert(chunkModerate, vecModerate);
        adapter.upsert(chunkOrthogonal, vecOrthogonal);
        adapter.upsert(chunkIdentical, vecIdentical);
        entityManager.flush();
        entityManager.clear();

        // Act: Búsqueda top 4
        List<KnowledgeCandidate> candidates = adapter.search(queryText, 4);

        // Assert
        assertThat(candidates).hasSize(4);

        // Orden descendente estricto por similitud coseno
        assertThat(candidates.get(0).chunk().sourceId()).isEqualTo("chunk-identical");
        assertThat(candidates.get(0).cosineSimilarity()).isCloseTo(1.0, offset(0.001));

        assertThat(candidates.get(1).chunk().sourceId()).isEqualTo("chunk-moderate");
        assertThat(candidates.get(1).cosineSimilarity()).isCloseTo(0.5, offset(0.001));

        assertThat(candidates.get(2).chunk().sourceId()).isEqualTo("chunk-orthogonal");
        assertThat(candidates.get(2).cosineSimilarity()).isCloseTo(0.0, offset(0.001));

        assertThat(candidates.get(3).chunk().sourceId()).isEqualTo("chunk-opposite");
        assertThat(candidates.get(3).cosineSimilarity()).isCloseTo(-1.0, offset(0.001));

        // Verificar que cada similitud está en [-1, 1] y orden decreciente monótono
        for (int i = 0; i < candidates.size(); i++) {
            double sim = candidates.get(i).cosineSimilarity();
            assertThat(sim).isBetween(-1.0, 1.0);
            if (i > 0) {
                assertThat(sim).isLessThanOrEqualTo(candidates.get(i - 1).cosineSimilarity());
            }
        }

        // Probar sobrecarga con KnowledgeQuery
        List<KnowledgeCandidate> top1 = adapter.search(new KnowledgeQuery(queryText), 1);
        assertThat(top1).hasSize(1);
        assertThat(top1.get(0).chunk().sourceId()).isEqualTo("chunk-identical");
    }

    @Test
    @DisplayName("Híbrida: un término raro que solo aparece en un fragmento lo promueve sobre uno con mayor coseno (RRF)")
    void shouldPromoteLexicalMatchOverHigherCosineWhenFusingWithRrf() {
        String queryText = "zorgblatt";
        ((DeterministicEmbeddingPort) embeddingPort).register(queryText, createVector(1.0, 0.0));

        KnowledgeChunk closer = new KnowledgeChunk("hy-closer", KnowledgeChunk.SourceType.POLICY_DOC,
                "Documento A", "Texto sin relacion lexica alguna.", KnowledgeChunk.Language.ES, "hash-hy-a");
        KnowledgeChunk lexical = new KnowledgeChunk("hy-lexical", KnowledgeChunk.SourceType.POLICY_DOC,
                "Documento B", "Contiene el termino zorgblatt de forma literal.", KnowledgeChunk.Language.ES, "hash-hy-b");
        adapter.upsert(closer, createVector(0.9, Math.sqrt(1 - 0.81)));
        adapter.upsert(lexical, createVector(0.8, 0.6));
        entityManager.flush();
        entityManager.clear();

        List<KnowledgeCandidate> candidates = adapter.search(queryText, 2);

        assertThat(candidates).hasSize(2);
        assertThat(candidates.get(0).chunk().sourceId()).isEqualTo("hy-lexical");
        // La similitud devuelta sigue siendo el coseno real, no la puntuación fusionada (FR-006)
        assertThat(candidates.get(0).cosineSimilarity()).isCloseTo(0.8, offset(0.001));
        assertThat(candidates.get(1).cosineSimilarity()).isCloseTo(0.9, offset(0.001));
    }

    @Test
    @DisplayName("Un mismo sourceId admite un fragmento ES y otro EN (UNIQUE source_id + language)")
    void shouldStoreSpanishAndEnglishChunksOfTheSameSource() {
        KnowledgeChunk es = new KnowledgeChunk("policy-bilingual", KnowledgeChunk.SourceType.POLICY_DOC,
                "Politica", "Contenido en espanol.", KnowledgeChunk.Language.ES, "hash-bi-es");
        KnowledgeChunk en = new KnowledgeChunk("policy-bilingual", KnowledgeChunk.SourceType.POLICY_DOC,
                "Policy", "Content in english.", KnowledgeChunk.Language.EN, "hash-bi-en");
        adapter.replaceIndex(List.of(
                new com.riwi.skillbridge.application.port.out.KnowledgeIndexPort.IndexedChunk(es, createVector(1.0, 0.0)),
                new com.riwi.skillbridge.application.port.out.KnowledgeIndexPort.IndexedChunk(en, createVector(0.0, 1.0))));
        entityManager.clear();

        assertThat(repository.count()).isEqualTo(2);
        // upsert del mismo (sourceId, idioma) actualiza en lugar de duplicar
        adapter.upsert(es, createVector(0.5, 0.5));
        entityManager.flush();
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("searchByType solo devuelve fragmentos del tipo pedido aunque otros tipos estén más cerca (preselección de ofertas)")
    void shouldRestrictSearchToTheRequestedSourceType() {
        String queryText = "quasar nebula";
        ((DeterministicEmbeddingPort) embeddingPort).register(queryText, createVector(1.0, 0.0));

        KnowledgeChunk policyClose = new KnowledgeChunk("typ-policy", KnowledgeChunk.SourceType.POLICY_DOC,
                "Politica", "Texto de politica.", KnowledgeChunk.Language.ES, "hash-typ-1");
        KnowledgeChunk offeringFar = new KnowledgeChunk("offering:typ-1", KnowledgeChunk.SourceType.OFFERING,
                "Curso A", "Texto de oferta A.", KnowledgeChunk.Language.ES, "hash-typ-2");
        KnowledgeChunk offeringFarther = new KnowledgeChunk("offering:typ-2", KnowledgeChunk.SourceType.OFFERING,
                "Curso B", "Texto de oferta B.", KnowledgeChunk.Language.ES, "hash-typ-3");
        adapter.upsert(policyClose, createVector(1.0, 0.0));
        adapter.upsert(offeringFar, createVector(0.6, 0.8));
        adapter.upsert(offeringFarther, createVector(0.0, 1.0));
        entityManager.flush();
        entityManager.clear();

        List<KnowledgeCandidate> offerings =
                adapter.searchByType(queryText, 5, KnowledgeChunk.SourceType.OFFERING);

        assertThat(offerings).extracting(c -> c.chunk().sourceId())
                .containsExactly("offering:typ-1", "offering:typ-2");
        assertThat(adapter.searchByType(queryText, 1, KnowledgeChunk.SourceType.OFFERING)).hasSize(1);
        assertThat(adapter.searchByType(queryText, 5, KnowledgeChunk.SourceType.CATEGORY)).isEmpty();
    }

    @Test
    @DisplayName("Sincronización diferencial: hashes por (sourceId, idioma), applyChanges atómico y deleteBySourceId")
    void shouldSupportDifferentialSyncOperations() {
        KnowledgeChunk keep = new KnowledgeChunk("sync-keep", KnowledgeChunk.SourceType.POLICY_DOC,
                "Se queda", "Contenido que no cambia.", KnowledgeChunk.Language.ES, "hash-keep");
        KnowledgeChunk change = new KnowledgeChunk("sync-change", KnowledgeChunk.SourceType.POLICY_DOC,
                "Cambia", "Contenido viejo.", KnowledgeChunk.Language.ES, "hash-old");
        KnowledgeChunk gone = new KnowledgeChunk("sync-gone", KnowledgeChunk.SourceType.OFFERING,
                "Se va", "Oferta que se desactiva.", KnowledgeChunk.Language.ES, "hash-gone");
        adapter.applyChanges(List.of(
                new KnowledgeIndexPort.IndexedChunk(keep, createVector(1.0, 0.0)),
                new KnowledgeIndexPort.IndexedChunk(change, createVector(0.0, 1.0)),
                new KnowledgeIndexPort.IndexedChunk(gone, createVector(0.6, 0.8))), List.of());
        entityManager.flush();
        entityManager.clear();

        java.util.Map<KnowledgeIndexPort.IndexKey, String> hashes = adapter.findIndexedHashes();
        assertThat(hashes).hasSize(3)
                .containsEntry(new KnowledgeIndexPort.IndexKey("sync-keep", KnowledgeChunk.Language.ES), "hash-keep")
                .containsEntry(new KnowledgeIndexPort.IndexKey("sync-change", KnowledgeChunk.Language.ES), "hash-old");

        // Un cambio y una eliminación en la misma operación; "keep" no se toca
        KnowledgeChunk changed = new KnowledgeChunk("sync-change", KnowledgeChunk.SourceType.POLICY_DOC,
                "Cambia", "Contenido NUEVO.", KnowledgeChunk.Language.ES, "hash-new");
        adapter.applyChanges(
                List.of(new KnowledgeIndexPort.IndexedChunk(changed, createVector(0.5, 0.5))),
                List.of(new KnowledgeIndexPort.IndexKey("sync-gone", KnowledgeChunk.Language.ES)));
        entityManager.flush();
        entityManager.clear();

        java.util.Map<KnowledgeIndexPort.IndexKey, String> after = adapter.findIndexedHashes();
        assertThat(after).hasSize(2)
                .containsEntry(new KnowledgeIndexPort.IndexKey("sync-keep", KnowledgeChunk.Language.ES), "hash-keep")
                .containsEntry(new KnowledgeIndexPort.IndexKey("sync-change", KnowledgeChunk.Language.ES), "hash-new");

        // deleteBySourceId borra todos los idiomas de la fuente y no falla si no existe
        KnowledgeChunk en = new KnowledgeChunk("sync-keep", KnowledgeChunk.SourceType.POLICY_DOC,
                "Stays", "Content that stays.", KnowledgeChunk.Language.EN, "hash-keep-en");
        adapter.upsert(en, createVector(1.0, 0.0));
        entityManager.flush();
        assertThat(adapter.findIndexedHashes()).hasSize(3);
        adapter.deleteBySourceId("sync-keep");
        adapter.deleteBySourceId("no-existe");
        entityManager.clear();
        assertThat(adapter.findIndexedHashes()).containsOnlyKeys(
                new KnowledgeIndexPort.IndexKey("sync-change", KnowledgeChunk.Language.ES));
    }

    @Test
    @DisplayName("Punto 5: Plan de consulta EXPLAIN para búsqueda vectorial y registro de uso de índice HNSW")
    void shouldExplainSearchQueryPlanAndRecordHnswIndexUsage() {
        // Arrange: Insertar fragmentos para la consulta EXPLAIN
        for (int i = 0; i < 10; i++) {
            KnowledgeChunk chunk = new KnowledgeChunk(
                    "explain-chunk-" + i,
                    KnowledgeChunk.SourceType.OFFERING,
                    "Title " + i,
                    "Content " + i,
                    KnowledgeChunk.Language.ES,
                    "hash-explain-" + i
            );
            double[] vec = createVector(Math.cos(i * 0.1), Math.sin(i * 0.1));
            adapter.upsert(chunk, vec);
        }
        entityManager.flush();

        double[] queryVec = createVector(1.0, 0.0);
        String queryVectorLiteral = toVectorString(queryVec);

        // Consulta nativa coincidente con JpaKnowledgeChunkRepository.searchNearest
        String querySql = """
                SELECT c.id AS id,
                       c.source_id AS sourceId,
                       c.source_type AS sourceType,
                       c.title AS title,
                       c.content AS content,
                       c.language AS language,
                       c.content_hash AS contentHash,
                       c.mode AS mode,
                       c.duration_hours AS durationHours,
                       c.updated_at AS updatedAt,
                       (c.embedding <=> CAST(:queryEmbedding AS vector)) AS distance,
                       (1.0 - (c.embedding <=> CAST(:queryEmbedding AS vector))) AS similarity
                FROM knowledge.knowledge_chunks c
                ORDER BY c.embedding <=> CAST(:queryEmbedding AS vector) ASC
                LIMIT :topK
                """;

        // 1. EXPLAIN estándar (decisión del planificador de PostgreSQL según volumen de datos)
        @SuppressWarnings("unchecked")
        List<String> explainOutput = entityManager.createNativeQuery("EXPLAIN " + querySql)
                .setParameter("queryEmbedding", queryVectorLiteral)
                .setParameter("topK", 5)
                .getResultList();

        String defaultPlan = String.join("\n", explainOutput);
        log.info("========== PLAN DE CONSULTA EXPLAIN (por defecto, volumen de prueba = 10 filas) ==========\n{}", defaultPlan);

        boolean usesHnswByDefault = defaultPlan.contains("knowledge_chunks_embedding_hnsw")
                || defaultPlan.toLowerCase().contains("index scan");
        log.info("¿Usa índice HNSW con el volumen de prueba por defecto? {} (Plan: {})",
                usesHnswByDefault,
                usesHnswByDefault ? "Index Scan" : "Seq Scan (comportamiento normal del planificador con pocas filas)");

        // 2. Comprobar existencia y configuración del índice HNSW en pg_indexes
        @SuppressWarnings("unchecked")
        List<Object[]> indexes = entityManager.createNativeQuery("""
                SELECT indexname, indexdef
                FROM pg_indexes
                WHERE schemaname = 'knowledge' AND tablename = 'knowledge_chunks'
                """).getResultList();

        boolean hnswIndexExists = indexes.stream()
                .anyMatch(row -> "knowledge_chunks_embedding_hnsw".equals(row[0])
                        && row[1].toString().contains("hnsw")
                        && row[1].toString().contains("vector_cosine_ops"));

        log.info("Verificación en pg_indexes: índice knowledge_chunks_embedding_hnsw presente = {}", hnswIndexExists);
        assertThat(hnswIndexExists).as("El índice HNSW knowledge_chunks_embedding_hnsw debe existir en la BD").isTrue();

        // 3. EXPLAIN con enable_seqscan = off para verificar que PostgreSQL puede utilizar el índice HNSW
        entityManager.createNativeQuery("SET LOCAL enable_seqscan = off").executeUpdate();

        @SuppressWarnings("unchecked")
        List<String> forcedIndexExplain = entityManager.createNativeQuery("EXPLAIN " + querySql)
                .setParameter("queryEmbedding", queryVectorLiteral)
                .setParameter("topK", 5)
                .getResultList();

        String forcedPlan = String.join("\n", forcedIndexExplain);
        log.info("========== PLAN DE CONSULTA EXPLAIN (con enable_seqscan = off) ==========\n{}", forcedPlan);

        boolean usesHnswWhenSeqScanDisabled = forcedPlan.contains("knowledge_chunks_embedding_hnsw")
                || forcedPlan.toLowerCase().contains("index scan");
        log.info("¿Usa índice HNSW cuando enable_seqscan = off? {}", usesHnswWhenSeqScanDisabled);

        assertThat(defaultPlan).isNotEmpty();
        assertThat(forcedPlan).isNotEmpty();
    }

    private static double[] createVector(double val0, double val1) {
        double[] vec = new double[768];
        vec[0] = val0;
        vec[1] = val1;
        return vec;
    }

    private static String toVectorString(double[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 12);
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}
