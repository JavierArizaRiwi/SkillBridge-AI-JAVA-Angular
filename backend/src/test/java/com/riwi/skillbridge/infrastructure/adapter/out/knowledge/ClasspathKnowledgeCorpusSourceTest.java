package com.riwi.skillbridge.infrastructure.adapter.out.knowledge;

import com.riwi.skillbridge.application.port.out.KnowledgeSourceDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClasspathKnowledgeCorpusSource Unit & Integration Tests")
class ClasspathKnowledgeCorpusSourceTest {

    private static final String SAMPLE_CONTENT = """
            ---
            sourceId: policy-sample
            type: POLICY_DOC
            title: Política de Prueba / Sample Policy
            visibility: public
            ---
            ## Español
            Texto con caracteres especiales: ñ, á, é, í, ó, ú, ¿, ¡.
            ## English
            Sample English content.
            """;

    private ClasspathKnowledgeCorpusSource source;

    @BeforeEach
    void setUp() {
        source = new ClasspathKnowledgeCorpusSource();
    }

    @Nested
    @DisplayName("Real Classpath Documents Ingestion Tests (HU-26, FR-004)")
    class RealClasspathTests {

        @Test
        @DisplayName("Should successfully load all 3 real Markdown documents from classpath")
        void shouldLoadRealClasspathDocuments() {
            List<KnowledgeSourceDocument> documents = source.loadDocuments();

            assertNotNull(documents, "La lista de documentos no debe ser nula");
            assertEquals(3, documents.size(), "Debe cargar exactamente los 3 documentos reales de knowledge/*.md");

            Set<String> names = documents.stream()
                    .map(KnowledgeSourceDocument::name)
                    .collect(Collectors.toSet());

            assertTrue(names.contains("cancellation-policy.md"), "Debe incluir cancellation-policy.md");
            assertTrue(names.contains("mentorships-how-it-works.md"), "Debe incluir mentorships-how-it-works.md");
            assertTrue(names.contains("platform-faq.md"), "Debe incluir platform-faq.md");

            for (KnowledgeSourceDocument doc : documents) {
                assertNotNull(doc.name(), "El nombre no debe ser nulo");
                assertFalse(doc.name().isBlank(), "El nombre no debe estar vacío");
                assertNotNull(doc.uri(), "El URI no debe ser nulo");
                assertFalse(doc.uri().isBlank(), "El URI no debe estar vacío");
                assertNotNull(doc.content(), "El contenido no debe ser nulo");
                assertFalse(doc.content().isBlank(), "El contenido no debe estar vacío");
                assertTrue(doc.content().startsWith("---"), "El contenido debe comenzar con frontmatter ---");
            }
        }
    }

    @Nested
    @DisplayName("Path Validation & Security Restrictions (FR-004)")
    class SecurityRestrictionsTests {

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
    @DisplayName("Path Segment Rules (FR-004)")
    class PathSegmentRulesTests {

        @Test
        @DisplayName("A directory like mi-proyecto-docs does not discard the corpus")
        void projectPathContainingDocsInsideAWordIsStillLoaded() {
            Resource resource = resourceAt("file:/home/dev/mi-proyecto-docs/backend/target/classes/knowledge/cancellation-policy.md", "cancellation-policy.md");

            List<KnowledgeSourceDocument> docs = source.loadFromResources(resource);
            assertEquals(1, docs.size());
            assertEquals("cancellation-policy.md", docs.getFirst().name());
        }

        @Test
        @DisplayName("A document under a real docs segment is discarded")
        void documentUnderRealDocsSegmentIsDiscarded() {
            Resource resource = resourceAt("file:/home/dev/proyecto/docs/knowledge/cancellation-policy.md", "cancellation-policy.md");

            List<KnowledgeSourceDocument> docs = source.loadFromResources(resource);
            assertTrue(docs.isEmpty(), "Documentos bajo ruta con segmento docs deben descartarse");
        }

        @ParameterizedTest
        @ValueSource(strings = {"classpath*:knowledge/*.md", "classpath:knowledge/cancellation-policy.md"})
        @DisplayName("Patterns with knowledge as path segment are accepted")
        void knowledgePatternsAreAccepted(String pattern) {
            assertFalse(ClasspathKnowledgeCorpusSource.hasPathSegment(pattern, "docs"));
            assertTrue(ClasspathKnowledgeCorpusSource.hasPathSegment(pattern, "knowledge"));
            assertNotNull(new ClasspathKnowledgeCorpusSource().loadFromPattern(pattern));
        }

        @ParameterizedTest
        @ValueSource(strings = {"classpath*:docs/*.md", "file:/x/docs/y.md"})
        @DisplayName("Patterns with docs as path segment are rejected")
        void docsPatternsAreRejected(String pattern) {
            assertThrows(IllegalArgumentException.class, () -> source.loadFromPattern(pattern));
        }

        @Test
        @DisplayName("hasPathSegment should correctly identify complete path segments only")
        void hasPathSegmentBehavior() {
            assertTrue(ClasspathKnowledgeCorpusSource.hasPathSegment("classpath*:knowledge/*.md", "knowledge"));
            assertFalse(ClasspathKnowledgeCorpusSource.hasPathSegment("classpath*:knowledge/*.md", "docs"));

            assertTrue(ClasspathKnowledgeCorpusSource.hasPathSegment("/home/user/docs/policy.md", "docs"));
            assertFalse(ClasspathKnowledgeCorpusSource.hasPathSegment("/home/user/my-docs-backup/policy.md", "docs"));
            assertFalse(ClasspathKnowledgeCorpusSource.hasPathSegment("/home/user/docs_v2/policy.md", "docs"));
        }
    }

    @Nested
    @DisplayName("Resource Loading Edge Cases")
    class ResourceLoadingEdgeCasesTests {

        @Test
        @DisplayName("Should return empty list for null or empty resources array")
        void shouldReturnEmptyForNullOrEmpty() {
            assertTrue(source.loadFromResources((Resource[]) null).isEmpty());
            assertTrue(source.loadFromResources().isEmpty());
        }

        @Test
        @DisplayName("Should ignore non-existent and non-md resources")
        void shouldIgnoreNonExistentAndNonMdResources() {
            Resource nonMdResource = new ByteArrayResource(SAMPLE_CONTENT.getBytes(StandardCharsets.UTF_8), "knowledge/test.txt") {
                @Override
                public String getFilename() {
                    return "test.txt";
                }

                @Override
                public boolean exists() {
                    return true;
                }

                @Override
                public URI getURI() {
                    return URI.create("file:/knowledge/test.txt");
                }
            };

            Resource nonExistentResource = new ByteArrayResource(SAMPLE_CONTENT.getBytes(StandardCharsets.UTF_8), "knowledge/missing.md") {
                @Override
                public String getFilename() {
                    return "missing.md";
                }

                @Override
                public boolean exists() {
                    return false;
                }
            };

            List<KnowledgeSourceDocument> docs = source.loadFromResources(nonMdResource, nonExistentResource, null);
            assertTrue(docs.isEmpty(), "Recursos inexistentes, nulos o que no terminen en .md deben ignorarse");
        }

        @Test
        @DisplayName("Should preserve UTF-8 encoding correctly")
        void shouldPreserveUtf8Encoding() {
            Resource resource = resourceAt("file:/home/dev/knowledge/sample.md", "sample.md");

            List<KnowledgeSourceDocument> docs = source.loadFromResources(resource);
            assertEquals(1, docs.size());
            assertTrue(docs.getFirst().content().contains("ñ, á, é, í, ó, ú, ¿, ¡"), "Debe preservar caracteres UTF-8");
        }
    }

    private static Resource resourceAt(String uri, String filename) {
        return new ByteArrayResource(SAMPLE_CONTENT.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public URI getURI() {
                return URI.create(uri);
            }

            @Override
            public String getFilename() {
                return filename;
            }

            @Override
            public boolean exists() {
                return true;
            }
        };
    }
}
