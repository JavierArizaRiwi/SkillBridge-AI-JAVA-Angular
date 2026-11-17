package com.riwi.skillbridge.application.service.knowledge;

import com.riwi.skillbridge.application.port.out.KnowledgeSourceDocument;
import com.riwi.skillbridge.domain.knowledge.KnowledgeChunk;
import com.riwi.skillbridge.infrastructure.adapter.out.knowledge.ClasspathKnowledgeCorpusSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("KnowledgeCorpusLoader Unit & Integration Tests")
class KnowledgeCorpusLoaderTest {

    private KnowledgeCorpusLoader loader;

    @BeforeEach
    void setUp() {
        loader = new KnowledgeCorpusLoader(new ClasspathKnowledgeCorpusSource());
    }

    @Nested
    @DisplayName("Real Knowledge Corpus Ingestion Tests (HU-26, FR-004, FR-014)")
    class RealCorpusTests {

        @Test
        @DisplayName("Should successfully load all 3 real documents into 6 bilingual chunks")
        void shouldLoadAllRealDocumentsSuccessfully() {
            List<KnowledgeChunk> chunks = loader.load();

            assertNotNull(chunks, "La lista de fragmentos no debe ser nula");
            assertEquals(6, chunks.size(), "Los 3 documentos reales bilingües deben generar exactamente 6 fragmentos (ES y EN para cada uno)");

            // Agrupar fragmentos por sourceId
            Map<String, List<KnowledgeChunk>> chunksBySource = chunks.stream()
                    .collect(Collectors.groupingBy(KnowledgeChunk::sourceId));

            // Verificar que los tres documentos reales están presentes
            assertTrue(chunksBySource.containsKey("policy-cancellation"), "Debe contener policy-cancellation");
            assertTrue(chunksBySource.containsKey("policy-mentorships"), "Debe contener policy-mentorships");
            assertTrue(chunksBySource.containsKey("policy-faq"), "Debe contener policy-faq");

            // Verificar cada fragmento individual
            for (KnowledgeChunk chunk : chunks) {
                assertNotNull(chunk.sourceId(), "sourceId no debe ser nulo");
                assertFalse(chunk.sourceId().isBlank(), "sourceId no debe estar vacío");
                assertEquals(KnowledgeChunk.SourceType.POLICY_DOC, chunk.sourceType(), "El tipo debe ser POLICY_DOC");
                assertNotNull(chunk.title(), "title no debe ser nulo");
                assertFalse(chunk.title().isBlank(), "title no debe estar vacío");
                assertNotNull(chunk.content(), "content no debe ser nulo");
                assertFalse(chunk.content().isBlank(), "content no debe estar vacío");

                // FR-014: Idioma debe ser ES o EN
                assertTrue(chunk.language() == KnowledgeChunk.Language.ES || chunk.language() == KnowledgeChunk.Language.EN,
                        "El idioma debe ser ES o EN, encontrado: " + chunk.language());

                // Hash SHA-256 debe tener 64 caracteres hexadecimales
                assertNotNull(chunk.contentHash(), "contentHash no debe ser nulo");
                assertEquals(64, chunk.contentHash().length(), "contentHash debe ser un hash SHA-256 de 64 caracteres");
                assertTrue(chunk.contentHash().matches("^[0-9a-f]{64}$"), "contentHash debe ser hexadecimal en minúsculas");

                // Campos específicos de oferta deben ser nulos para documentos de política
                assertNull(chunk.mode(), "mode debe ser nulo para documentos de política");
                assertNull(chunk.durationHours(), "durationHours debe ser nulo para documentos de política");
            }

            // Verificar específicamente policy-cancellation
            List<KnowledgeChunk> cancellationChunks = chunksBySource.get("policy-cancellation");
            assertEquals(2, cancellationChunks.size(), "policy-cancellation debe tener 2 fragmentos");

            KnowledgeChunk cancelEs = cancellationChunks.stream()
                    .filter(c -> c.language() == KnowledgeChunk.Language.ES)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Falta fragmento ES de policy-cancellation"));
            assertEquals("Política de cancelación de reservas", cancelEs.title());
            assertTrue(cancelEs.content().contains("24 horas de anticipación"));
            assertTrue(cancelEs.content().contains("### Cancelar una reserva"));

            KnowledgeChunk cancelEn = cancellationChunks.stream()
                    .filter(c -> c.language() == KnowledgeChunk.Language.EN)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Falta fragmento EN de policy-cancellation"));
            assertEquals("Booking cancellation policy", cancelEn.title());
            assertTrue(cancelEn.content().contains("24 hours before"));
            assertTrue(cancelEn.content().contains("### Cancelling a booking"));

            // Verificar específicamente policy-mentorships
            List<KnowledgeChunk> mentorshipChunks = chunksBySource.get("policy-mentorships");
            assertEquals(2, mentorshipChunks.size(), "policy-mentorships debe tener 2 fragmentos");

            KnowledgeChunk mentorEs = mentorshipChunks.stream()
                    .filter(c -> c.language() == KnowledgeChunk.Language.ES)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Falta fragmento ES de policy-mentorships"));
            assertEquals("Cómo funcionan las mentorías", mentorEs.title());
            assertTrue(mentorEs.content().contains("Una mentoría por pago"));

            KnowledgeChunk mentorEn = mentorshipChunks.stream()
                    .filter(c -> c.language() == KnowledgeChunk.Language.EN)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Falta fragmento EN de policy-mentorships"));
            assertEquals("How mentorships work", mentorEn.title());
            assertTrue(mentorEn.content().contains("One mentorship per payment"));

            // Verificar específicamente policy-faq
            List<KnowledgeChunk> faqChunks = chunksBySource.get("policy-faq");
            assertEquals(2, faqChunks.size(), "policy-faq debe tener 2 fragmentos");

            KnowledgeChunk faqEs = faqChunks.stream()
                    .filter(c -> c.language() == KnowledgeChunk.Language.ES)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Falta fragmento ES de policy-faq"));
            assertEquals("Preguntas frecuentes de la plataforma", faqEs.title());
            assertTrue(faqEs.content().contains("¿Qué es SkillBridge AI?"));

            KnowledgeChunk faqEn = faqChunks.stream()
                    .filter(c -> c.language() == KnowledgeChunk.Language.EN)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Falta fragmento EN de policy-faq"));
            assertEquals("Platform FAQ", faqEn.title());
            assertTrue(faqEn.content().contains("What is SkillBridge AI?"));
        }
    }

    @Nested
    @DisplayName("Visibility & Filtering Tests (FR-004)")
    class VisibilityFilteringTests {

        @ParameterizedTest
        @ValueSource(strings = {"private", "draft", "internal", "restricted", "confidential", ""})
        @DisplayName("Should discard documents when visibility is not public")
        void shouldDiscardNonPublicDocuments(String visibility) {
            String markdown = """
                    ---
                    sourceId: policy-internal-security
                    type: POLICY_DOC
                    title: Seguridad interna / Internal security
                    visibility: %s
                    ---

                    ## Español
                    Contenido confidencial no apto para indexación pública.

                    ## English
                    Confidential content not suitable for public indexing.
                    """.formatted(visibility);

            List<KnowledgeChunk> chunks = loader.parse(markdown);
            assertTrue(chunks.isEmpty(), "Documentos sin visibility: public deben ser descartados");
        }

        @Test
        @DisplayName("Should discard document when visibility attribute is missing")
        void shouldDiscardDocumentWhenVisibilityIsMissing() {
            String markdown = """
                    ---
                    sourceId: policy-missing-vis
                    type: POLICY_DOC
                    title: Documento sin visibilidad
                    ---

                    ## Español
                    Texto sin declarar visibilidad.
                    """;

            List<KnowledgeChunk> chunks = loader.parse(markdown);
            assertTrue(chunks.isEmpty(), "Documentos sin visibilidad declarada deben ser descartados");
        }

        @Test
        @DisplayName("Should accept documents when visibility is public case-insensitively")
        void shouldAcceptPublicVisibilityCaseInsensitively() {
            String markdown = """
                    ---
                    sourceId: policy-case-test
                    type: POLICY_DOC
                    title: Documento Público / Public Document
                    visibility: PUBLIC
                    ---

                    ## Español
                    Texto en español público.

                    ## English
                    Public english text.
                    """;

            List<KnowledgeChunk> chunks = loader.parse(markdown);
            assertEquals(2, chunks.size(), "Debe aceptar visibility: PUBLIC");
        }
    }

    @Nested
    @DisplayName("Path Validation & Security Restrictions (FR-004)")
    class SecurityRestrictionsTests {

        private final ClasspathKnowledgeCorpusSource source = new ClasspathKnowledgeCorpusSource();

        @Test
        @DisplayName("Should reject path patterns pointing to docs/")
        void shouldRejectPatternsContainingDocs() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> source.loadFromPattern("classpath*:docs/*.md"));
            assertTrue(ex.getMessage().contains("docs/"), "El mensaje debe indicar la prohibición de docs/");
        }

        @Test
        @DisplayName("Should reject patterns not containing knowledge/")
        void shouldRejectPatternsNotContainingKnowledge() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> source.loadFromPattern("classpath*:other/*.md"));
            assertTrue(ex.getMessage().contains("knowledge/"), "El mensaje debe indicar la restricción a knowledge/");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        @DisplayName("Should reject empty or blank patterns")
        void shouldRejectEmptyOrBlankPatterns(String blankPattern) {
            assertThrows(IllegalArgumentException.class, () -> source.loadFromPattern(blankPattern));
        }

        @Test
        @DisplayName("Constructor should reject invalid initial resource pattern")
        void constructorShouldValidatePattern() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ClasspathKnowledgeCorpusSource(new PathMatchingResourcePatternResolver(), "classpath*:docs/*.md"));
        }
    }

    @Nested
    @DisplayName("Markdown Parsing & Edge Cases")
    class ParsingEdgeCasesTests {

        @Test
        @DisplayName("Should return empty list for null or blank input")
        void shouldReturnEmptyForNullOrBlankInput() {
            assertTrue(loader.parse(null).isEmpty());
            assertTrue(loader.parse("").isEmpty());
            assertTrue(loader.parse("    \n\t   ").isEmpty());
        }

        @Test
        @DisplayName("Should return empty list when frontmatter delimiters are missing")
        void shouldReturnEmptyWhenNoFrontmatter() {
            String markdown = """
                    # Política sin frontmatter
                    ## Español
                    Texto sin encabezado YAML.
                    """;
            assertTrue(loader.parse(markdown).isEmpty());
        }

        @Test
        @DisplayName("Should return empty list when frontmatter is not closed")
        void shouldReturnEmptyWhenFrontmatterNotClosed() {
            String markdown = """
                    ---
                    sourceId: policy-broken
                    visibility: public
                    ## Español
                    Sin cierre de frontmatter.
                    """;
            assertTrue(loader.parse(markdown).isEmpty());
        }

        @Test
        @DisplayName("Should return empty list when sourceId is blank or missing")
        void shouldReturnEmptyWhenSourceIdMissing() {
            String markdown = """
                    ---
                    type: POLICY_DOC
                    title: Sin sourceId
                    visibility: public
                    ---

                    ## Español
                    Contenido sin sourceId.
                    """;
            assertTrue(loader.parse(markdown).isEmpty());
        }

        @Test
        @DisplayName("Should default to ES when no language headings are present")
        void shouldDefaultToEsWhenNoLanguageHeadingsPresent() {
            String markdown = """
                    ---
                    sourceId: policy-unilingual
                    title: Política Unilingüe
                    visibility: public
                    ---

                    Texto informativo general de la plataforma sin secciones explícitas de idioma.
                    """;

            List<KnowledgeChunk> chunks = loader.parse(markdown);
            assertEquals(1, chunks.size());
            assertEquals(KnowledgeChunk.Language.ES, chunks.getFirst().language());
            assertEquals("policy-unilingual", chunks.getFirst().sourceId());
            assertEquals("Política Unilingüe", chunks.getFirst().title());
            assertEquals("Texto informativo general de la plataforma sin secciones explícitas de idioma.", chunks.getFirst().content());
        }

        @Test
        @DisplayName("Should handle quoted frontmatter values")
        void shouldHandleQuotedFrontmatterValues() {
            String markdown = """
                    ---
                    sourceId: "policy-quoted"
                    type: "POLICY_DOC"
                    title: 'Política con comillas / Quoted Policy'
                    visibility: "public"
                    ---

                    ## Español
                    Texto de prueba.

                    ## English
                    Test text.
                    """;

            List<KnowledgeChunk> chunks = loader.parse(markdown);
            assertEquals(2, chunks.size());
            assertEquals("policy-quoted", chunks.getFirst().sourceId());
            assertEquals("Política con comillas", chunks.get(0).title());
            assertEquals("Quoted Policy", chunks.get(1).title());
        }

        @Test
        @DisplayName("Should load from custom Resource array while ignoring non-matching resources")
        void shouldLoadFromCustomResources() {
            String mdContent = """
                    ---
                    sourceId: policy-mock
                    visibility: public
                    title: Mock Policy
                    ---

                    ## Español
                    Mock en español.
                    """;

            ByteArrayResource resource = new ByteArrayResource(mdContent.getBytes(StandardCharsets.UTF_8), "knowledge/mock-policy.md") {
                @Override
                public String getFilename() {
                    return "mock-policy.md";
                }
            };

            ClasspathKnowledgeCorpusSource source = new ClasspathKnowledgeCorpusSource();
            List<KnowledgeSourceDocument> docs = source.loadFromResources(resource);
            KnowledgeCorpusLoader customLoader = new KnowledgeCorpusLoader(() -> docs);
            List<KnowledgeChunk> chunks = customLoader.load();
            assertEquals(1, chunks.size());
            assertEquals("policy-mock", chunks.getFirst().sourceId());
            assertEquals("Mock Policy", chunks.getFirst().title());
        }

        @Test
        @DisplayName("Constructor should reject null KnowledgeCorpusSourcePort and have single constructor")
        void constructorShouldRequireSourcePort() {
            assertThrows(NullPointerException.class, () -> new KnowledgeCorpusLoader(null));
            assertEquals(1, KnowledgeCorpusLoader.class.getConstructors().length,
                    "KnowledgeCorpusLoader debe tener un único constructor que recibe KnowledgeCorpusSourcePort");
        }
    }
}
