package com.riwi.skillbridge.application.service.knowledge;

import com.riwi.skillbridge.infrastructure.adapter.out.knowledge.ClasspathKnowledgeCorpusSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("KnowledgeCorpusLoader path segment rules (FR-004)")
class KnowledgeCorpusLoaderPathTest {

    private static final String PUBLIC_DOC = """
            ---
            sourceId: cancellation-policy
            type: POLICY_DOC
            title: Cancelación / Cancellation
            visibility: public
            ---
            ## Español
            Contenido en español.
            ## English
            English content.
            """;

    private final ClasspathKnowledgeCorpusSource source = new ClasspathKnowledgeCorpusSource();
    private final KnowledgeCorpusLoader loader = new KnowledgeCorpusLoader(source);

    @Test
    @DisplayName("Un directorio como mi-proyecto-docs no descarta el corpus")
    void projectPathContainingDocsInsideAWordIsStillLoaded() throws IOException {
        Resource resource = resourceAt("file:/home/dev/mi-proyecto-docs/backend/target/classes/knowledge/cancellation-policy.md");

        KnowledgeCorpusLoader customLoader = new KnowledgeCorpusLoader(() -> source.loadFromResources(resource));
        assertEquals(2, customLoader.load().size());
    }

    @Test
    @DisplayName("Un documento bajo un segmento docs real se descarta")
    void documentUnderRealDocsSegmentIsDiscarded() throws IOException {
        Resource resource = resourceAt("file:/home/dev/proyecto/docs/knowledge/cancellation-policy.md");

        KnowledgeCorpusLoader customLoader = new KnowledgeCorpusLoader(() -> source.loadFromResources(resource));
        assertTrue(customLoader.load().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"classpath*:knowledge/*.md", "classpath:knowledge/cancellation-policy.md"})
    @DisplayName("Los patrones con knowledge/ como segmento se aceptan")
    void knowledgePatternsAreAccepted(String pattern) {
        assertFalse(ClasspathKnowledgeCorpusSource.hasPathSegment(pattern, "docs"));
        assertTrue(ClasspathKnowledgeCorpusSource.hasPathSegment(pattern, "knowledge"));
        ClasspathKnowledgeCorpusSource patternSource = new ClasspathKnowledgeCorpusSource(new PathMatchingResourcePatternResolver(), pattern);
        KnowledgeCorpusLoader patternLoader = new KnowledgeCorpusLoader(patternSource);
        assertTrue(patternLoader.load() != null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"classpath*:docs/*.md", "file:/x/docs/y.md"})
    @DisplayName("Los patrones con docs como segmento se rechazan")
    void docsPatternsAreRejected(String pattern) {
        assertThrows(IllegalArgumentException.class, () -> source.loadFromPattern(pattern));
    }

    private static Resource resourceAt(String uri) {
        return new ByteArrayResource(PUBLIC_DOC.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public URI getURI() {
                return URI.create(uri);
            }

            @Override
            public String getFilename() {
                return "cancellation-policy.md";
            }
        };
    }
}
