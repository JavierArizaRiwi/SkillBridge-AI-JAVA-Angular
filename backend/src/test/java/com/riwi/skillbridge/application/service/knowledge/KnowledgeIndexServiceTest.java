package com.riwi.skillbridge.application.service.knowledge;

import com.riwi.skillbridge.application.port.out.CategoryRepositoryPort;
import com.riwi.skillbridge.application.port.out.EmbeddingPort;
import com.riwi.skillbridge.application.port.out.KnowledgeIndexPort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.domain.knowledge.KnowledgeChunk;
import com.riwi.skillbridge.domain.model.Category;
import com.riwi.skillbridge.domain.model.MentorshipMode;
import com.riwi.skillbridge.domain.model.Offering;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("KnowledgeIndexService Unit Tests (T022, FR-015, FR-018)")
class KnowledgeIndexServiceTest {

    private KnowledgeCorpusLoader corpusLoader;
    private OfferingRepositoryPort offeringRepository;
    private CategoryRepositoryPort categoryRepository;
    private EmbeddingPort embeddingPort;
    private KnowledgeIndexPort indexPort;
    private KnowledgeIndexService service;

    @BeforeEach
    void setUp() {
        corpusLoader = mock(KnowledgeCorpusLoader.class);
        offeringRepository = mock(OfferingRepositoryPort.class);
        categoryRepository = mock(CategoryRepositoryPort.class);
        embeddingPort = mock(EmbeddingPort.class);
        indexPort = mock(KnowledgeIndexPort.class);

        service = new KnowledgeIndexService(
                corpusLoader,
                offeringRepository,
                categoryRepository,
                embeddingPort,
                indexPort
        );
    }

    private static double[] sampleVector(int dim) {
        double[] vec = new double[dim];
        vec[0] = 1.0;
        return vec;
    }

    @Nested
    @DisplayName("Initial Batch Load & Atomic Replacement (FR-015, FR-018)")
    class InitialLoadTests {

        @Test
        @DisplayName("Carga inicial al arrancar por lotes: corpus .md, ofertas activas y categorías activas reemplaza el índice atómicamente")
        void shouldLoadInitialIndexInBatchAndReplaceIndexAtomically() {
            // Arrange
            KnowledgeChunk policy1 = new KnowledgeChunk(
                    "policy-cancel",
                    KnowledgeChunk.SourceType.POLICY_DOC,
                    "Política de Cancelación",
                    "Texto de la política de cancelación.",
                    KnowledgeChunk.Language.ES,
                    "hash-policy-cancel-123"
            );
            KnowledgeChunk policy2 = new KnowledgeChunk(
                    "policy-faq",
                    KnowledgeChunk.SourceType.POLICY_DOC,
                    "Preguntas Frecuentes",
                    "Texto de preguntas frecuentes.",
                    KnowledgeChunk.Language.ES,
                    "hash-policy-faq-456"
            );
            when(corpusLoader.load()).thenReturn(List.of(policy1, policy2));

            UUID providerId = UUID.randomUUID();
            Offering offering1 = Offering.create(
                    providerId,
                    "Curso de Spring Boot",
                    "Aprende Spring Boot desde cero.",
                    "BACKEND",
                    BigDecimal.valueOf(150),
                    MentorshipMode.LIVE,
                    4
            );
            Offering inactiveOffering = new Offering(
                    UUID.randomUUID(),
                    providerId,
                    "Curso Inactivo",
                    "Descripción inactiva",
                    "BACKEND",
                    BigDecimal.valueOf(100),
                    false,
                    MentorshipMode.ASYNCHRONOUS,
                    1
            );
            when(offeringRepository.findAllActive()).thenReturn(List.of(offering1, inactiveOffering));

            Category category1 = new Category(UUID.randomUUID(), "ARQUITECTURA", true, Instant.now());
            when(categoryRepository.findAllActive()).thenReturn(List.of(category1));

            // 2 políticas + 1 oferta activa (la inactiva se descarta) + 1 categoría = 4 fragmentos
            List<double[]> mockEmbeddings = List.of(
                    sampleVector(768),
                    sampleVector(768),
                    sampleVector(768),
                    sampleVector(768)
            );
            when(embeddingPort.embed(anyList())).thenReturn(mockEmbeddings);

            // Act
            int count = service.loadInitialIndex();

            // Assert
            assertEquals(4, count, "Deben haberse indexado exactamente 4 fragmentos");

            // Verifica que EmbeddingPort se invocó por lotes con una sola llamada para los 4 textos
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<String>> textsCaptor = ArgumentCaptor.forClass(List.class);
            verify(embeddingPort, times(1)).embed(textsCaptor.capture());

            List<String> passedTexts = textsCaptor.getValue();
            assertEquals(4, passedTexts.size());
            assertEquals("Texto de la política de cancelación.", passedTexts.get(0));
            assertEquals("Texto de preguntas frecuentes.", passedTexts.get(1));
            assertTrue(passedTexts.get(2).startsWith("Mentoría: Curso de Spring Boot"));
            assertTrue(passedTexts.get(2).endsWith("Descripción: Aprende Spring Boot desde cero."));
            assertEquals("ARQUITECTURA", passedTexts.get(3));

            // Verifica que el índice se actualiza en una sola operación atómica con los 4 fragmentos y sus vectores
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<KnowledgeIndexPort.IndexedChunk>> upsertsCaptor = ArgumentCaptor.forClass(List.class);
            @SuppressWarnings("unchecked")
            ArgumentCaptor<java.util.Collection<KnowledgeIndexPort.IndexKey>> deletionsCaptor = ArgumentCaptor.forClass(java.util.Collection.class);
            verify(indexPort, times(1)).applyChanges(upsertsCaptor.capture(), deletionsCaptor.capture());

            assertEquals(4, upsertsCaptor.getValue().size());
            assertTrue(deletionsCaptor.getValue().isEmpty());
            assertEquals("offering:" + offering1.id(), upsertsCaptor.getValue().get(2).chunk().sourceId());
            assertEquals("category:" + category1.id(), upsertsCaptor.getValue().get(3).chunk().sourceId());
        }

        @Test
        @DisplayName("Carga inicial con fuentes vacías y un índice vacío no hace nada ni invoca embeddings")
        void shouldHandleEmptySourcesGracefully() {
            when(corpusLoader.load()).thenReturn(List.of());
            when(offeringRepository.findAllActive()).thenReturn(List.of());
            when(categoryRepository.findAllActive()).thenReturn(List.of());

            int count = service.loadInitialIndex();

            assertEquals(0, count);
            verify(indexPort, never()).applyChanges(any(), any());
            verifyNoInteractions(embeddingPort);
        }

        private KnowledgeChunk policy(String id, KnowledgeChunk.Language lang, String content) {
            return new KnowledgeChunk(id, KnowledgeChunk.SourceType.POLICY_DOC, "T " + id, content, lang,
                    KnowledgeIndexService.computeContentHash(content));
        }

        @Test
        @DisplayName("Reinicio sin cambios: reutiliza los vectores guardados y no llama al proveedor de embeddings (FR-018)")
        void shouldReuseStoredVectorsWhenNothingChanged() {
            KnowledgeChunk p1 = policy("policy-1", KnowledgeChunk.Language.ES, "Contenido uno");
            KnowledgeChunk p2 = policy("policy-1", KnowledgeChunk.Language.EN, "Content one");
            when(corpusLoader.load()).thenReturn(List.of(p1, p2));
            when(offeringRepository.findAllActive()).thenReturn(List.of());
            when(categoryRepository.findAllActive()).thenReturn(List.of());
            when(indexPort.findIndexedHashes()).thenReturn(java.util.Map.of(
                    new KnowledgeIndexPort.IndexKey("policy-1", KnowledgeChunk.Language.ES), p1.contentHash(),
                    new KnowledgeIndexPort.IndexKey("policy-1", KnowledgeChunk.Language.EN), p2.contentHash()));

            int count = service.loadInitialIndex();

            assertEquals(2, count);
            verifyNoInteractions(embeddingPort);
            verify(indexPort, never()).applyChanges(any(), any());
        }

        @Test
        @DisplayName("Reinicio con un fragmento cambiado: solo ese se recalcula; lo reutilizable no se envía al proveedor")
        void shouldRecomputeOnlyChangedChunks() {
            KnowledgeChunk same = policy("policy-1", KnowledgeChunk.Language.ES, "Contenido uno");
            KnowledgeChunk changed = policy("policy-2", KnowledgeChunk.Language.ES, "Contenido dos NUEVO");
            when(corpusLoader.load()).thenReturn(List.of(same, changed));
            when(offeringRepository.findAllActive()).thenReturn(List.of());
            when(categoryRepository.findAllActive()).thenReturn(List.of());
            when(indexPort.findIndexedHashes()).thenReturn(java.util.Map.of(
                    new KnowledgeIndexPort.IndexKey("policy-1", KnowledgeChunk.Language.ES), same.contentHash(),
                    new KnowledgeIndexPort.IndexKey("policy-2", KnowledgeChunk.Language.ES), "hash-viejo"));
            when(embeddingPort.embed(anyList())).thenReturn(List.of(sampleVector(768)));

            service.loadInitialIndex();

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<String>> texts = ArgumentCaptor.forClass(List.class);
            verify(embeddingPort, times(1)).embed(texts.capture());
            assertEquals(List.of("Contenido dos NUEVO"), texts.getValue());
        }

        @Test
        @DisplayName("Reinicio: lo que ya no existe (oferta desactivada, categoría borrada) se elimina del índice")
        void shouldRemoveStaleEntriesFromTheIndex() {
            when(corpusLoader.load()).thenReturn(List.of());
            when(offeringRepository.findAllActive()).thenReturn(List.of());
            when(categoryRepository.findAllActive()).thenReturn(List.of());
            KnowledgeIndexPort.IndexKey stale = new KnowledgeIndexPort.IndexKey("offering:viejo", KnowledgeChunk.Language.ES);
            when(indexPort.findIndexedHashes()).thenReturn(java.util.Map.of(stale, "h"));

            service.loadInitialIndex();

            @SuppressWarnings("unchecked")
            ArgumentCaptor<java.util.Collection<KnowledgeIndexPort.IndexKey>> deletions = ArgumentCaptor.forClass(java.util.Collection.class);
            verify(indexPort).applyChanges(eq(List.of()), deletions.capture());
            assertEquals(List.of(stale), List.copyOf(deletions.getValue()));
            verifyNoInteractions(embeddingPort);
        }

        @Test
        @DisplayName("Más de 50 fragmentos se envían al proveedor en varios lotes de máximo 50")
        void shouldSplitEmbeddingCallsIntoBatches() {
            List<KnowledgeChunk> many = new java.util.ArrayList<>();
            for (int i = 0; i < 120; i++) {
                many.add(policy("policy-" + i, KnowledgeChunk.Language.ES, "Contenido " + i));
            }
            when(corpusLoader.load()).thenReturn(many);
            when(offeringRepository.findAllActive()).thenReturn(List.of());
            when(categoryRepository.findAllActive()).thenReturn(List.of());
            when(embeddingPort.embed(anyList())).thenAnswer(inv -> {
                List<?> in = inv.getArgument(0);
                return in.stream().map(x -> sampleVector(768)).toList();
            });

            service.loadInitialIndex();

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<String>> texts = ArgumentCaptor.forClass(List.class);
            verify(embeddingPort, times(3)).embed(texts.capture());
            assertEquals(List.of(50, 50, 20), texts.getAllValues().stream().map(List::size).toList());
        }

        @Test
        @DisplayName("Si el proveedor de embeddings falla, no se toca el índice existente")
        void shouldLeaveIndexUntouchedWhenEmbeddingProviderFails() {
            when(corpusLoader.load()).thenReturn(List.of(policy("policy-1", KnowledgeChunk.Language.ES, "Nuevo")));
            when(offeringRepository.findAllActive()).thenReturn(List.of());
            when(categoryRepository.findAllActive()).thenReturn(List.of());
            when(embeddingPort.embed(anyList())).thenThrow(new IllegalStateException("proveedor caído"));

            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> service.loadInitialIndex());

            verify(indexPort, never()).applyChanges(any(), any());
        }
    }

    @Nested
    @DisplayName("Reindexing & Hash Change Detection (FR-018)")
    class ReindexingHashDetectionTests {

        @Test
        @DisplayName("Reindexado de lote solo recalcula fragmentos cuyo contentHash cambió (FR-018)")
        void shouldRecalculateOnlyChangedHashesInBatch() {
            // Arrange
            String existingHash1 = "hash-unchanged-1";
            String existingHash2 = "hash-unchanged-2";
            when(indexPort.findExistingHashes()).thenReturn(Set.of(existingHash1, existingHash2));

            KnowledgeChunk chunk1 = new KnowledgeChunk(
                    "policy-1",
                    KnowledgeChunk.SourceType.POLICY_DOC,
                    "Política 1",
                    "Contenido 1 sin cambios",
                    KnowledgeChunk.Language.ES,
                    existingHash1
            );
            KnowledgeChunk chunk2 = new KnowledgeChunk(
                    "policy-2",
                    KnowledgeChunk.SourceType.POLICY_DOC,
                    "Política 2",
                    "Contenido 2 sin cambios",
                    KnowledgeChunk.Language.ES,
                    existingHash2
            );
            KnowledgeChunk chunk3Changed = new KnowledgeChunk(
                    "offering:new-123",
                    KnowledgeChunk.SourceType.OFFERING,
                    "Nueva Oferta",
                    "Contenido nuevo o modificado",
                    KnowledgeChunk.Language.ES,
                    "hash-modified-333"
            );

            when(embeddingPort.embed(List.of("Contenido nuevo o modificado")))
                    .thenReturn(List.of(sampleVector(768)));

            // Act
            int recalculatedCount = service.reindexChunks(List.of(chunk1, chunk2, chunk3Changed));

            // Assert: solo 1 fragmento recalculado
            assertEquals(1, recalculatedCount, "Solo el fragmento con hash cambiado debió recalcularse");

            // EmbeddingPort solo recibió el texto cambiado
            verify(embeddingPort, times(1)).embed(List.of("Contenido nuevo o modificado"));

            // Solo se upsertó el fragmento cambiado
            verify(indexPort, times(1)).upsert(chunk3Changed, sampleVector(768));
            verify(indexPort, never()).upsert(org.mockito.ArgumentMatchers.eq(chunk1), any());
            verify(indexPort, never()).upsert(org.mockito.ArgumentMatchers.eq(chunk2), any());
        }

        @Test
        @DisplayName("Reindexado de lote no invoca EmbeddingPort si ningún hash cambió")
        void shouldNotCallEmbeddingPortIfNoHashesChanged() {
            String hashA = "hash-a";
            String hashB = "hash-b";
            when(indexPort.findExistingHashes()).thenReturn(Set.of(hashA, hashB));

            KnowledgeChunk chunkA = new KnowledgeChunk(
                    "doc-a",
                    KnowledgeChunk.SourceType.POLICY_DOC,
                    "Doc A",
                    "Texto A",
                    KnowledgeChunk.Language.ES,
                    hashA
            );
            KnowledgeChunk chunkB = new KnowledgeChunk(
                    "doc-b",
                    KnowledgeChunk.SourceType.CATEGORY,
                    "Doc B",
                    "Texto B",
                    KnowledgeChunk.Language.ES,
                    hashB
            );

            int recalculated = service.reindexChunks(List.of(chunkA, chunkB));

            assertEquals(0, recalculated);
            verifyNoInteractions(embeddingPort);
            verify(indexPort, never()).upsert(any(), any());
        }

        @Test
        @DisplayName("Reindexado de fragmento individual recalcula solo si el hash no existe")
        void shouldReindexSingleChunkOnlyWhenHashDoesNotExist() {
            KnowledgeChunk chunkNew = new KnowledgeChunk(
                    "offering:test-1",
                    KnowledgeChunk.SourceType.OFFERING,
                    "Test",
                    "Texto Test",
                    KnowledgeChunk.Language.ES,
                    "hash-new-123"
            );
            when(indexPort.existsByContentHash("hash-new-123")).thenReturn(false);
            when(embeddingPort.embed("Texto Test")).thenReturn(sampleVector(768));

            boolean updated = service.reindexChunk(chunkNew);

            assertTrue(updated);
            verify(embeddingPort).embed("Texto Test");
            verify(indexPort).upsert(chunkNew, sampleVector(768));
        }

        @Test
        @DisplayName("Reindexado de fragmento individual omite recálculo si el hash ya existe")
        void shouldSkipSingleChunkWhenHashAlreadyExists() {
            KnowledgeChunk chunkExisting = new KnowledgeChunk(
                    "offering:test-2",
                    KnowledgeChunk.SourceType.OFFERING,
                    "Test 2",
                    "Texto Test 2",
                    KnowledgeChunk.Language.ES,
                    "hash-existing-456"
            );
            when(indexPort.existsByContentHash("hash-existing-456")).thenReturn(true);

            boolean updated = service.reindexChunk(chunkExisting);

            assertFalse(updated);
            verifyNoInteractions(embeddingPort);
            verify(indexPort, never()).upsert(any(), any());
        }
    }

    @Nested
    @DisplayName("Offering & Category Synchronization (T023, T024, FR-015)")
    class OfferingCategorySyncTests {

        @Test
        @DisplayName("reindexOffering sincroniza oferta activa recalculando fragmento")
        void shouldReindexActiveOffering() {
            Offering offering = Offering.create(
                    UUID.randomUUID(),
                    "Java Performance Tuning",
                    "Optimización avanzada de JVM y código.",
                    "BACKEND",
                    BigDecimal.valueOf(200)
            );

            when(indexPort.existsByContentHash(anyString())).thenReturn(false);
            when(embeddingPort.embed(anyString())).thenReturn(sampleVector(768));

            boolean result = service.reindexOffering(offering);

            assertTrue(result);
            verify(indexPort).upsert(org.mockito.ArgumentMatchers.argThat(chunk ->
                    chunk.sourceId().equals("offering:" + offering.id()) &&
                    chunk.title().equals("Java Performance Tuning") &&
                    chunk.sourceType() == KnowledgeChunk.SourceType.OFFERING
            ), any());
        }

        @Test
        @DisplayName("reindexOffering omite oferta inactiva (FR-004)")
        void shouldSkipInactiveOffering() {
            Offering inactiveOffering = new Offering(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "Oferta Desactivada",
                    "Desc",
                    "BACKEND",
                    BigDecimal.TEN,
                    false
            );

            boolean result = service.reindexOffering(inactiveOffering);

            assertFalse(result);
            verifyNoInteractions(embeddingPort);
            verify(indexPort, never()).upsert(any(), any());
            // y se retira del índice si estaba indexada (antes quedaba hasta el siguiente reinicio)
            verify(indexPort).deleteBySourceId("offering:" + inactiveOffering.id());
        }

        @Test
        @DisplayName("reindexCategory sincroniza categoría activa recalculando fragmento")
        void shouldReindexActiveCategory() {
            Category category = new Category(UUID.randomUUID(), "DEVOPS", true, Instant.now());
            when(indexPort.existsByContentHash(anyString())).thenReturn(false);
            when(embeddingPort.embed("DEVOPS")).thenReturn(sampleVector(768));

            boolean result = service.reindexCategory(category);

            assertTrue(result);
            verify(indexPort).upsert(org.mockito.ArgumentMatchers.argThat(chunk ->
                    chunk.sourceId().equals("category:" + category.id()) &&
                    chunk.title().equals("DEVOPS") &&
                    chunk.content().equals("DEVOPS") &&
                    chunk.sourceType() == KnowledgeChunk.SourceType.CATEGORY
            ), any());
        }

        @Test
        @DisplayName("deleteCategory retira la categoría del índice")
        void shouldHandleDeleteCategoryNotification() {
            UUID catId = UUID.randomUUID();
            assertDoesNotThrow(() -> service.deleteCategory(catId));
            assertDoesNotThrow(() -> service.onCategoryDeleted(catId));
            verify(indexPort, times(2)).deleteBySourceId("category:" + catId);
        }
    }

    @Nested
    @DisplayName("Chunk Mapping & Invariants")
    class ChunkMappingTests {
        private static final String NL = "\n";


        @Test
        @DisplayName("toChunk mapea Offering con mode y durationHours correctamente")
        void shouldMapOfferingWithModeAndDuration() {
            UUID providerId = UUID.randomUUID();
            Offering offering = Offering.create(
                    providerId,
                    "Mentoría Docker",
                    "Contenedores desde cero",
                    "DEVOPS",
                    BigDecimal.valueOf(80),
                    MentorshipMode.LIVE,
                    2
            );

            KnowledgeChunk chunk = service.toChunk(offering);

            assertEquals("offering:" + offering.id(), chunk.sourceId());
            assertEquals(KnowledgeChunk.SourceType.OFFERING, chunk.sourceType());
            assertEquals("Mentoría Docker", chunk.title());
            assertEquals("Mentoría: Mentoría Docker"+NL+"Categoría: DEVOPS"+NL+"Modalidad: en vivo (LIVE)"+NL+"Duración: 2 horas"+NL+"Precio: 80"+NL+"Descripción: Contenedores desde cero", chunk.content());
            assertEquals(KnowledgeChunk.Language.ES, chunk.language());
            assertEquals("LIVE", chunk.mode());
            assertEquals(2, chunk.durationHours());
            assertNotNull(chunk.contentHash());
            assertEquals(64, chunk.contentHash().length());
            assertTrue(chunk.contentHash().matches("^[0-9a-f]{64}$"));
        }

        @Test
        @DisplayName("toChunk mapea Offering con descripción nula usando el título como contenido")
        void shouldFallbackToTitleWhenOfferingDescriptionIsMissing() {
            Offering offering = new Offering(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "Título Sin Descripción",
                    null,
                    "DEVOPS",
                    BigDecimal.valueOf(50),
                    true
            );

            KnowledgeChunk chunk = service.toChunk(offering);

            assertTrue(chunk.content().startsWith("Mentoría: Título Sin Descripción"));
            assertTrue(chunk.content().contains("Categoría: DEVOPS"));
            assertFalse(chunk.content().contains("Descripción:"));
            assertEquals(KnowledgeIndexService.computeContentHash(chunk.content()), chunk.contentHash());
        }

        @Test
        @DisplayName("toChunk mapea Category con name como título y contenido")
        void shouldMapCategoryCorrectly() {
            UUID catId = UUID.randomUUID();
            Category category = new Category(catId, "FRONTEND", true, Instant.now());

            KnowledgeChunk chunk = service.toChunk(category);

            assertEquals("category:" + catId, chunk.sourceId());
            assertEquals(KnowledgeChunk.SourceType.CATEGORY, chunk.sourceType());
            assertEquals("FRONTEND", chunk.title());
            assertEquals("FRONTEND", chunk.content());
            assertEquals(KnowledgeChunk.Language.ES, chunk.language());
            assertNull(chunk.mode());
            assertNull(chunk.durationHours());
            assertEquals(64, chunk.contentHash().length());
        }

        @Test
        @DisplayName("Constructor requiere dependencias no nulas")
        void constructorShouldRequireNonNullDependencies() {
            assertThrows(NullPointerException.class, () -> new KnowledgeIndexService(null, offeringRepository, categoryRepository, embeddingPort, indexPort));
            assertThrows(NullPointerException.class, () -> new KnowledgeIndexService(corpusLoader, null, categoryRepository, embeddingPort, indexPort));
            assertThrows(NullPointerException.class, () -> new KnowledgeIndexService(corpusLoader, offeringRepository, null, embeddingPort, indexPort));
            assertThrows(NullPointerException.class, () -> new KnowledgeIndexService(corpusLoader, offeringRepository, categoryRepository, null, indexPort));
            assertThrows(NullPointerException.class, () -> new KnowledgeIndexService(corpusLoader, offeringRepository, categoryRepository, embeddingPort, null));
        }

        @Test
        @DisplayName("KnowledgeIndexService debe ser clase pura sin anotaciones de Spring ni Jakarta")
        void shouldBePureClassWithoutSpringOrJakartaAnnotations() {
            assertEquals(0, KnowledgeIndexService.class.getAnnotations().length,
                    "KnowledgeIndexService no debe tener anotaciones de framework en la clase");
        }
    }
}
