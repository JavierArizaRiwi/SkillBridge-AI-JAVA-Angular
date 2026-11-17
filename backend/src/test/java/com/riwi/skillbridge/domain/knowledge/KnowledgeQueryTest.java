package com.riwi.skillbridge.domain.knowledge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Knowledge Domain Models Test Suite")
class KnowledgeQueryTest {

    @Nested
    @DisplayName("KnowledgeQuery Validation Tests")
    class KnowledgeQueryValidationTests {

        @ParameterizedTest(name = "Debe rechazar pregunta inválida: \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {" ", "   ", "\t", "\n", " \t \n "})
        void shouldRejectNullEmptyOrBlankQuestion(String invalidQuestion) {
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> new KnowledgeQuery(invalidQuestion, Instant.now(), "user-123")
            );
            assertTrue(ex.getMessage().contains("no puede ser nula ni estar vacía"));
        }

        @Test
        void shouldRejectQuestionWith501Characters() {
            String question501 = "a".repeat(501);
            assertEquals(501, question501.length());

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> new KnowledgeQuery(question501, Instant.now(), "user-123")
            );
            assertTrue(ex.getMessage().contains("500 caracteres"));
        }

        @Test
        void shouldAcceptQuestionWith1Character() {
            String question1 = "a";
            assertEquals(1, question1.length());

            KnowledgeQuery query = assertDoesNotThrow(
                    () -> new KnowledgeQuery(question1, Instant.now(), "user-123")
            );
            assertEquals("a", query.question());
        }

        @Test
        void shouldAcceptQuestionWith500Characters() {
            String question500 = "a".repeat(500);
            assertEquals(500, question500.length());

            KnowledgeQuery query = assertDoesNotThrow(
                    () -> new KnowledgeQuery(question500, Instant.now(), "user-123")
            );
            assertEquals(500, query.question().length());
            assertEquals(question500, query.question());
        }

        @Test
        void shouldRetainAskedAtAndUserId() {
            Instant now = Instant.parse("2026-11-10T12:00:00Z");
            KnowledgeQuery query = new KnowledgeQuery("¿Cómo cancelo una reserva?", now, "user-42");

            assertEquals("¿Cómo cancelo una reserva?", query.question());
            assertEquals(now, query.askedAt());
            assertEquals("user-42", query.userId());
        }

        @Test
        void shouldSupportSingleArgumentConstructor() {
            KnowledgeQuery query = new KnowledgeQuery("¿Cuál es el costo del curso?");

            assertEquals("¿Cuál es el costo del curso?", query.question());
            assertNotNull(query.askedAt());
            assertNull(query.userId());
        }
    }

    @Nested
    @DisplayName("KnowledgeAnswer Immutability & Defensive Copy Tests")
    class KnowledgeAnswerTests {

        @Test
        void shouldDefensivelyCopySourcesListOnCreation() {
            List<KnowledgeSource> mutableList = new ArrayList<>();
            mutableList.add(new KnowledgeSource("policy-cancellation", "Política de cancelación", "Snippet 1", 0.95));

            KnowledgeAnswer answer = new KnowledgeAnswer("Respuesta generada", true, mutableList);
            assertEquals(1, answer.sources().size());

            // Modificar la lista externa original no debe impactar las fuentes de la respuesta
            mutableList.add(new KnowledgeSource("offering:123", "Java Avanzado", "Snippet 2", 0.82));
            assertEquals(1, answer.sources().size(), "La lista interna debe ser una copia inmutable independiente");
        }

        @Test
        void shouldReturnUnmodifiableSourcesList() {
            KnowledgeSource source = new KnowledgeSource("category:dev", "Desarrollo", "Snippet", 0.77);
            KnowledgeAnswer answer = new KnowledgeAnswer("Respuesta", true, List.of(source));

            assertThrows(
                    UnsupportedOperationException.class,
                    () -> answer.sources().add(new KnowledgeSource("offering:456", "Spring Boot", "Snippet", 0.65))
            );
        }

        @Test
        void shouldHandleNullSourcesAsEmptyImmutableList() {
            KnowledgeAnswer answer = new KnowledgeAnswer("Sin contexto", false, null);

            assertNotNull(answer.sources());
            assertTrue(answer.sources().isEmpty());
            assertThrows(
                    UnsupportedOperationException.class,
                    () -> answer.sources().add(new KnowledgeSource("s", "T", "snip", 0.5))
            );
        }

        @Test
        void shouldRejectNullAnswer() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new KnowledgeAnswer(null, true, List.of())
            );
        }

        @Test
        void shouldSupportTwoArgumentConstructor() {
            KnowledgeAnswer answer = new KnowledgeAnswer("No tengo suficiente información.", false);

            assertEquals("No tengo suficiente información.", answer.answer());
            assertFalse(answer.hasSufficientContext());
            assertNotNull(answer.sources());
            assertTrue(answer.sources().isEmpty());
        }
    }

    @Nested
    @DisplayName("KnowledgeChunk Validation Tests")
    class KnowledgeChunkTests {

        @Test
        void shouldCreateValidKnowledgeChunk() {
            KnowledgeChunk chunk = new KnowledgeChunk(
                    "offering:101",
                    KnowledgeChunk.SourceType.OFFERING,
                    "Taller de Microservicios",
                    "Contenido completo del taller de microservicios.",
                    KnowledgeChunk.Language.ES,
                    "abc123hash",
                    "REMOTE",
                    40
            );

            assertEquals("offering:101", chunk.sourceId());
            assertEquals(KnowledgeChunk.SourceType.OFFERING, chunk.sourceType());
            assertEquals("Taller de Microservicios", chunk.title());
            assertEquals("Contenido completo del taller de microservicios.", chunk.content());
            assertEquals(KnowledgeChunk.Language.ES, chunk.language());
            assertEquals("abc123hash", chunk.contentHash());
            assertEquals("REMOTE", chunk.mode());
            assertEquals(40, chunk.durationHours());
        }

        @Test
        void shouldAllowNullModeAndDurationHours() {
            KnowledgeChunk chunk = new KnowledgeChunk(
                    "policy-cancellation",
                    KnowledgeChunk.SourceType.POLICY_DOC,
                    "Política de cancelación",
                    "Las cancelaciones deben realizarse con 24 horas de anticipación.",
                    KnowledgeChunk.Language.ES,
                    "hashpolicy456",
                    null,
                    null
            );

            assertNull(chunk.mode());
            assertNull(chunk.durationHours());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "   "})
        void shouldRejectNullOrBlankSourceId(String invalidSourceId) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new KnowledgeChunk(
                            invalidSourceId,
                            KnowledgeChunk.SourceType.POLICY_DOC,
                            "Título",
                            "Contenido",
                            KnowledgeChunk.Language.ES,
                            "hash",
                            null,
                            null
                    )
            );
        }

        @Test
        void shouldRejectNullSourceType() {
            assertThrows(
                    NullPointerException.class,
                    () -> new KnowledgeChunk(
                            "policy-1",
                            null,
                            "Título",
                            "Contenido",
                            KnowledgeChunk.Language.ES,
                            "hash",
                            null,
                            null
                    )
            );
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "   "})
        void shouldRejectNullOrBlankTitle(String invalidTitle) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new KnowledgeChunk(
                            "policy-1",
                            KnowledgeChunk.SourceType.POLICY_DOC,
                            invalidTitle,
                            "Contenido",
                            KnowledgeChunk.Language.ES,
                            "hash",
                            null,
                            null
                    )
            );
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "   "})
        void shouldRejectNullOrBlankContent(String invalidContent) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new KnowledgeChunk(
                            "policy-1",
                            KnowledgeChunk.SourceType.POLICY_DOC,
                            "Título",
                            invalidContent,
                            KnowledgeChunk.Language.ES,
                            "hash",
                            null,
                            null
                    )
            );
        }

        @Test
        void shouldRejectNullLanguage() {
            assertThrows(
                    NullPointerException.class,
                    () -> new KnowledgeChunk(
                            "policy-1",
                            KnowledgeChunk.SourceType.POLICY_DOC,
                            "Título",
                            "Contenido",
                            null,
                            "hash",
                            null,
                            null
                    )
            );
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "   "})
        void shouldRejectNullOrBlankContentHash(String invalidHash) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new KnowledgeChunk(
                            "policy-1",
                            KnowledgeChunk.SourceType.POLICY_DOC,
                            "Título",
                            "Contenido",
                            KnowledgeChunk.Language.ES,
                            invalidHash,
                            null,
                            null
                    )
            );
        }

        @Test
        void shouldSupportConstructorWithoutModeAndDuration() {
            KnowledgeChunk chunk = new KnowledgeChunk(
                    "category:cloud",
                    KnowledgeChunk.SourceType.CATEGORY,
                    "Cloud Computing",
                    "Todo lo referente a arquitecturas en la nube.",
                    KnowledgeChunk.Language.ES,
                    "hashcloud789"
            );

            assertEquals("category:cloud", chunk.sourceId());
            assertNull(chunk.mode());
            assertNull(chunk.durationHours());
        }
    }

    @Nested
    @DisplayName("KnowledgeSource Validation Tests")
    class KnowledgeSourceTests {

        @Test
        void shouldCreateValidKnowledgeSource() {
            KnowledgeSource source = new KnowledgeSource(
                    "policy-cancellation",
                    "Política de cancelación",
                    "Extracto relevante sobre las 24 horas previas.",
                    0.88
            );

            assertEquals("policy-cancellation", source.sourceId());
            assertEquals("Política de cancelación", source.title());
            assertEquals("Extracto relevante sobre las 24 horas previas.", source.snippet());
            assertEquals(0.88, source.score(), 0.0001);
        }

        @Test
        void shouldAcceptBoundaryScores() {
            KnowledgeSource minSource = new KnowledgeSource("s-min", "Title", "Snippet", 0.0);
            KnowledgeSource maxSource = new KnowledgeSource("s-max", "Title", "Snippet", 1.0);

            assertEquals(0.0, minSource.score(), 0.0001);
            assertEquals(1.0, maxSource.score(), 0.0001);
        }

        @Test
        void shouldRejectScoreLessThanZero() {
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> new KnowledgeSource("s1", "Title", "Snippet", -0.01)
            );
            assertTrue(ex.getMessage().contains("rango [0.0, 1.0]"));
        }

        @Test
        void shouldRejectScoreGreaterThanOne() {
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> new KnowledgeSource("s1", "Title", "Snippet", 1.0001)
            );
            assertTrue(ex.getMessage().contains("rango [0.0, 1.0]"));
        }

        @Test
        void shouldRejectNaNScore() {
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> new KnowledgeSource("s1", "Title", "Snippet", Double.NaN)
            );
            assertTrue(ex.getMessage().contains("rango [0.0, 1.0]"));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "   "})
        void shouldRejectNullOrBlankSourceId(String invalidSourceId) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new KnowledgeSource(invalidSourceId, "Title", "Snippet", 0.5)
            );
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "   "})
        void shouldRejectNullOrBlankTitle(String invalidTitle) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new KnowledgeSource("s1", invalidTitle, "Snippet", 0.5)
            );
        }

        @Test
        void shouldRejectNullSnippet() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new KnowledgeSource("s1", "Title", null, 0.5)
            );
        }
    }
}
