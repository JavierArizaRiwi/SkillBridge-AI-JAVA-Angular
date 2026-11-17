package com.riwi.skillbridge.application.service.knowledge;

import com.riwi.skillbridge.domain.knowledge.KnowledgeChunk;
import com.riwi.skillbridge.domain.knowledge.KnowledgeSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SourceCitationValidator Test Suite (HU-26, FR-007, SC-003)")
class SourceCitationValidatorTest {

    private SourceCitationValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SourceCitationValidator();
    }

    @Nested
    @DisplayName("Hallucination & Injected Sources Validation (SC-003)")
    class HallucinationTests {

        @Test
        @DisplayName("Un sourceId alucinado por el LLM se descarta y solo se devuelven los válidos inyectados")
        void shouldDiscardHallucinatedSourceIdFromResponse() {
            // Respuesta donde el LLM inventa identificadores no inyectados en el contexto
            String answer = "De acuerdo con [cancellation-policy], puedes cancelar con 24 horas de antelación. "
                    + "Para más información, consulta [hallucinated-refund-policy] y [fake-source-999].";

            List<String> injectedSources = List.of("cancellation-policy", "platform-faq");

            List<String> validated = validator.validateCitations(answer, injectedSources);

            // Debe devolver únicamente el sourceId válido inyectado y citado
            assertEquals(List.of("cancellation-policy"), validated);
            assertFalse(validated.contains("hallucinated-refund-policy"), "El ID alucinado debe ser descartado");
            assertFalse(validated.contains("fake-source-999"), "El ID alucinado debe ser descartado");
            assertFalse(validated.contains("platform-faq"), "El ID no citado debe ser excluido");
        }

        @Test
        @DisplayName("Si el LLM cita solo identificadores inventados, se retorna lista vacía")
        void shouldReturnEmptyWhenAllCitationsAreHallucinated() {
            String answer = "Información según [invented-source-1] y [hallucinated-policy].";
            List<String> injected = List.of("cancellation-policy", "mentorships-how-it-works");

            List<String> validated = validator.validateCitations(answer, injected);

            assertNotNull(validated);
            assertTrue(validated.isEmpty(), "No debe retornar ninguna cita si todas fueron alucinadas");
        }
    }

    @Nested
    @DisplayName("Deduplication & Order Preservation")
    class DeduplicationTests {

        @Test
        @DisplayName("Los sourceId citados válidos se devuelven una sola vez aunque aparezcan repetidos en la respuesta")
        void shouldReturnValidCitationsOnlyOnceWhenCitedMultipleTimes() {
            String answer = "Según [cancellation-policy], el plazo es de 24 horas. "
                    + "Además, como reitera [cancellation-policy], se devuelve el importe íntegro. "
                    + "En caso de dudas, ver [platform-faq] y volver a revisar [cancellation-policy].";

            List<String> injected = List.of("cancellation-policy", "platform-faq", "mentorships-how-it-works");

            List<String> validated = validator.validateCitations(answer, injected);

            // Se devuelven exactamente una sola vez cada uno, en orden de primera aparición
            assertEquals(2, validated.size(), "Cada fuente citada debe devolverse exactamente una sola vez");
            assertEquals(List.of("cancellation-policy", "platform-faq"), validated);
        }

        @Test
        @DisplayName("Debe preservar el orden de citación según su primera aparición en la respuesta")
        void shouldPreserveCitationOrderBasedOnFirstAppearance() {
            String answer = "Primero consultamos [mentorships-how-it-works] y luego [cancellation-policy].";
            List<String> injected = List.of("cancellation-policy", "mentorships-how-it-works");

            List<String> validated = validator.validateCitations(answer, injected);

            assertEquals(List.of("mentorships-how-it-works", "cancellation-policy"), validated);
        }
    }

    @Nested
    @DisplayName("Citation Format Variations & Boundary Matching")
    class FormatAndBoundaryTests {

        @Test
        @DisplayName("Debe reconocer citas en diversos formatos (corchetes, texto directo, signos de puntuación)")
        void shouldRecognizeCitationsInVariousFormats() {
            String answer = "Basado en cancellation-policy, también en (platform-faq) y \"offering:123\".";
            List<String> injected = List.of("cancellation-policy", "platform-faq", "offering:123");

            List<String> validated = validator.validateCitations(answer, injected);

            assertEquals(3, validated.size());
            assertTrue(validated.contains("cancellation-policy"));
            assertTrue(validated.contains("platform-faq"));
            assertTrue(validated.contains("offering:123"));
        }

        @Test
        @DisplayName("No debe coincidir falsamente si un sourceId es subcadena de otro identificador")
        void shouldNotFalselyMatchSubstringsOfIdentifiers() {
            // "policy" no debe coincidir si en el texto aparece "cancellation-policy"
            // "offering:1" no debe coincidir si en el texto aparece "offering:100"
            String answer = "Consulte [cancellation-policy] y la oferta [offering:100].";
            List<String> injected = List.of("policy", "offering:1", "cancellation-policy", "offering:100");

            List<String> validated = validator.validateCitations(answer, injected);

            assertEquals(List.of("cancellation-policy", "offering:100"), validated);
            assertFalse(validated.contains("policy"), "'policy' no fue citado como token independiente");
            assertFalse(validated.contains("offering:1"), "'offering:1' no debe coincidir con 'offering:100'");
        }

        @Test
        @DisplayName("Debe ser insensible a mayúsculas al detectar la cita pero devolver el sourceId canónico")
        void shouldMatchCaseInsensitivelyAndReturnCanonicalSourceId() {
            String answer = "Según [Cancellation-Policy] y [PLATFORM-FAQ].";
            List<String> injected = List.of("cancellation-policy", "platform-faq");

            List<String> validated = validator.validateCitations(answer, injected);

            assertEquals(List.of("cancellation-policy", "platform-faq"), validated);
        }
    }

    @Nested
    @DisplayName("Edge Cases & Null Safety")
    class EdgeCasesTests {

        @ParameterizedTest(name = "Debe retornar lista vacía con respuesta inválida: \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {" ", "   ", "\t", "\n"})
        void shouldReturnEmptyListForNullOrBlankAnswer(String blankAnswer) {
            List<String> validated = validator.validateCitations(blankAnswer, List.of("cancellation-policy"));
            assertNotNull(validated);
            assertTrue(validated.isEmpty());
        }

        @Test
        @DisplayName("Debe retornar lista vacía si la lista de fuentes inyectadas es nula o vacía")
        void shouldReturnEmptyListForNullOrEmptyInjectedSources() {
            List<String> validatedWithNull = validator.validateCitations("Respuesta [cancellation-policy]", (List<String>) null);
            assertNotNull(validatedWithNull);
            assertTrue(validatedWithNull.isEmpty());

            List<String> validatedWithEmpty = validator.validateCitations("Respuesta [cancellation-policy]", List.of());
            assertNotNull(validatedWithEmpty);
            assertTrue(validatedWithEmpty.isEmpty());
        }

        @Test
        @DisplayName("Debe filtrar elementos nulos o vacíos dentro de la colección de fuentes inyectadas")
        void shouldIgnoreNullOrBlankElementsInInjectedSources() {
            String answer = "Texto [cancellation-policy].";
            List<String> injectedWithNulls = java.util.Arrays.asList(null, "", "   ", "cancellation-policy");

            List<String> validated = validator.validateCitations(answer, injectedWithNulls);

            assertEquals(List.of("cancellation-policy"), validated);
        }

        @Test
        @DisplayName("Debe funcionar con el alias validate() y con validateCitationsAsSet()")
        void shouldSupportConvenienceMethods() {
            String answer = "Texto con [cancellation-policy].";
            List<String> injected = List.of("cancellation-policy");

            List<String> asList = validator.validate(answer, injected);
            Set<String> asSet = validator.validateCitationsAsSet(answer, injected);

            assertEquals(List.of("cancellation-policy"), asList);
            assertEquals(Set.of("cancellation-policy"), asSet);
        }

        @Test
        @DisplayName("Debe validar citas directamente desde objetos KnowledgeChunk")
        void shouldValidateFromKnowledgeChunks() {
            KnowledgeChunk chunk1 = new KnowledgeChunk(
                    "cancellation-policy",
                    KnowledgeChunk.SourceType.POLICY_DOC,
                    "Política",
                    "Contenido",
                    KnowledgeChunk.Language.ES,
                    "hash-1"
            );
            KnowledgeChunk chunk2 = new KnowledgeChunk(
                    "unused-policy",
                    KnowledgeChunk.SourceType.POLICY_DOC,
                    "No usada",
                    "Contenido",
                    KnowledgeChunk.Language.ES,
                    "hash-2"
            );

            String answer = "Cita a [cancellation-policy] y a [hallucinated-chunk].";
            List<String> validated = validator.validateChunkCitations(answer, List.of(chunk1, chunk2));

            assertEquals(List.of("cancellation-policy"), validated);
        }

        @Test
        @DisplayName("Debe filtrar y devolver lista de KnowledgeSource citados")
        void shouldFilterKnowledgeSourceList() {
            KnowledgeSource s1 = new KnowledgeSource("cancellation-policy", "Política", "Snippet 1", 0.95);
            KnowledgeSource s2 = new KnowledgeSource("unused-source", "No usada", "Snippet 2", 0.80);

            String answer = "Según [cancellation-policy], el plazo es 24h.";
            List<KnowledgeSource> filtered = validator.filterValidSources(answer, List.of(s1, s2));

            assertEquals(1, filtered.size());
            assertEquals("cancellation-policy", filtered.get(0).sourceId());
        }
    }
}
