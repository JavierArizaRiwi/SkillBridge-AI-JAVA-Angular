package com.riwi.skillbridge.infrastructure.adapter.out.ai;

import com.riwi.skillbridge.application.common.CorrelationIdHolder;
import com.riwi.skillbridge.application.service.knowledge.KnowledgePromptBuilder;
import com.riwi.skillbridge.domain.exception.AiProviderException;
import com.riwi.skillbridge.domain.exception.AiProviderException.ErrorType;
import com.riwi.skillbridge.domain.knowledge.KnowledgeChunk;
import com.riwi.skillbridge.domain.knowledge.KnowledgeQuery;
import com.riwi.skillbridge.infrastructure.config.KnowledgeProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Pruebas de verificación de ausencia de mocks silenciosos en producción para RAG (Task T036, HU-26, FR-009, SC-005).
 * <p>
 * Demuestra que, sin credenciales válidas y sin {@code app.ai.provider=mock} explícito:
 * 1. {@link GeminiEmbeddingAdapter} sin clave (apiKey vacía) lanza {@link AiProviderException} con {@link ErrorType#UNAVAILABLE}
 *    y nunca devuelve vectores simulados o fijos.
 * 2. {@link GeminiKnowledgeAnswerAdapter} con un {@link ChatClient} que falla por excepción de red lanza {@link AiProviderException}
 *    con {@link ErrorType#UNAVAILABLE} o {@link ErrorType#TIMEOUT} y nunca devuelve texto simulado o fijo.
 */
@DisplayName("KnowledgeNoSilentMockTest — Ausencia de mocks silenciosos en producción para RAG")
class KnowledgeNoSilentMockTest {

    private static final KnowledgeChunk SAMPLE_CHUNK = new KnowledgeChunk(
            "policy-cancellation-01",
            KnowledgeChunk.SourceType.POLICY_DOC,
            "Política de Cancelación",
            "Las reservas pueden cancelarse con al menos 24 horas de antelación.",
            KnowledgeChunk.Language.ES,
            "hash-abc-123"
    );

    @Nested
    @DisplayName("Caso 1: GeminiEmbeddingAdapter sin API key lanza AiProviderException UNAVAILABLE sin devolver vectores")
    class GeminiEmbeddingAdapterNoSilentMock {

        @Mock
        private EmbeddingModel embeddingModel;

        private KnowledgeProperties properties;
        private GeminiEmbeddingAdapter adapterEmptyKey;
        private GeminiEmbeddingAdapter adapterBlankKey;
        private GeminiEmbeddingAdapter adapterNullKey;

        @BeforeEach
        void setUp() {
            MockitoAnnotations.openMocks(this);
            properties = new KnowledgeProperties();
            properties.setTimeoutSeconds(5);

            adapterEmptyKey = new GeminiEmbeddingAdapter(embeddingModel, properties, "");
            adapterBlankKey = new GeminiEmbeddingAdapter(embeddingModel, properties, "   ");
            adapterNullKey = new GeminiEmbeddingAdapter(embeddingModel, properties, null);
        }

        @AfterEach
        void tearDown() {
            if (adapterEmptyKey != null) adapterEmptyKey.close();
            if (adapterBlankKey != null) adapterBlankKey.close();
            if (adapterNullKey != null) adapterNullKey.close();
        }

        @Test
        @DisplayName("Sin clave (apiKey vacía): embed(List<String>) lanza AiProviderException UNAVAILABLE y no devuelve vectores")
        void givenEmptyApiKey_whenEmbedList_thenThrowsUnavailableAndNoVectorsReturned() {
            List<String> texts = List.of("¿Cómo agendar una asesoría?", "¿Cuál es el costo?");

            AiProviderException exception = assertThrows(
                    AiProviderException.class,
                    () -> adapterEmptyKey.embed(texts),
                    "Debe lanzar AiProviderException cuando apiKey está vacía"
            );

            assertEquals(ErrorType.UNAVAILABLE, exception.getErrorType(),
                    "El ErrorType debe ser UNAVAILABLE indicando proveedor no configurado");
            assertTrue(exception.getMessage().contains("not configured") || exception.getMessage().contains("unavailable"),
                    "El mensaje debe indicar falta de configuración o indisponibilidad: " + exception.getMessage());

            verifyNoInteractions(embeddingModel);
        }

        @Test
        @DisplayName("Sin clave (apiKey con espacios en blanco): embed(List<String>) lanza AiProviderException UNAVAILABLE")
        void givenBlankApiKey_whenEmbedList_thenThrowsUnavailable() {
            List<String> texts = List.of("Consulta de prueba");

            AiProviderException exception = assertThrows(
                    AiProviderException.class,
                    () -> adapterBlankKey.embed(texts)
            );

            assertEquals(ErrorType.UNAVAILABLE, exception.getErrorType());
            verifyNoInteractions(embeddingModel);
        }

        @Test
        @DisplayName("Sin clave (apiKey nula): embed(List<String>) lanza AiProviderException UNAVAILABLE")
        void givenNullApiKey_whenEmbedList_thenThrowsUnavailable() {
            List<String> texts = List.of("Consulta de prueba");

            AiProviderException exception = assertThrows(
                    AiProviderException.class,
                    () -> adapterNullKey.embed(texts)
            );

            assertEquals(ErrorType.UNAVAILABLE, exception.getErrorType());
            verifyNoInteractions(embeddingModel);
        }

        @Test
        @DisplayName("Sin clave: sobrecarga embed(String) lanza AiProviderException UNAVAILABLE y no devuelve vector")
        void givenEmptyApiKey_whenEmbedSingleText_thenThrowsUnavailableAndNoVectorReturned() {
            AiProviderException exception = assertThrows(
                    AiProviderException.class,
                    () -> adapterEmptyKey.embed("Texto individual")
            );

            assertEquals(ErrorType.UNAVAILABLE, exception.getErrorType());
            verifyNoInteractions(embeddingModel);
        }
    }

    @Nested
    @DisplayName("Caso 2: GeminiKnowledgeAnswerAdapter ante fallos de red lanza AiProviderException UNAVAILABLE o TIMEOUT")
    class GeminiKnowledgeAnswerAdapterNoSilentMock {

        @Mock
        private ChatClient chatClient;

        @Mock
        private ChatClient.ChatClientRequestSpec requestSpec;

        @Mock
        private ChatClient.CallResponseSpec responseSpec;

        private KnowledgeProperties properties;
        private KnowledgePromptBuilder promptBuilder;
        private GeminiKnowledgeAnswerAdapter answerAdapter;

        @BeforeEach
        void setUp() {
            MockitoAnnotations.openMocks(this);
            properties = new KnowledgeProperties();
            properties.setTimeoutSeconds(5);
            properties.setMaxContextChars(6000);

            promptBuilder = new KnowledgePromptBuilder(properties.getMaxContextChars());

            when(chatClient.prompt()).thenReturn(requestSpec);
            when(requestSpec.user(anyString())).thenReturn(requestSpec);

            answerAdapter = new GeminiKnowledgeAnswerAdapter(chatClient, properties, promptBuilder);
        }

        @AfterEach
        void tearDown() {
            if (answerAdapter != null) {
                answerAdapter.destroy();
            }
            CorrelationIdHolder.clear();
        }

        @Test
        @DisplayName("Excepción de conexión de red (ConnectException): lanza AiProviderException UNAVAILABLE y no devuelve texto")
        void givenNetworkConnectionRefused_whenGenerateAnswer_thenThrowsUnavailableAndNoTextReturned() {
            // Simulamos fallo de red: Connection refused
            ConnectException rootCause = new ConnectException("Connection refused (Connection refused)");
            RuntimeException networkException = new RuntimeException("Error al conectar con la API de Gemini", rootCause);
            when(requestSpec.call()).thenThrow(networkException);

            AiProviderException exception = assertThrows(
                    AiProviderException.class,
                    () -> answerAdapter.generateAnswer("¿Cuál es la política de cancelación?", List.of(SAMPLE_CHUNK)),
                    "Debe propagar AiProviderException en vez de retornar texto simulado"
            );

            assertEquals(ErrorType.UNAVAILABLE, exception.getErrorType(),
                    "El ErrorType debe ser UNAVAILABLE ante fallo de conexión de red");
            assertNotNull(exception.getMessage(), "El mensaje de excepción no debe ser nulo");
            assertFalse(exception.getMessage().isBlank());
        }

        @Test
        @DisplayName("Excepción de timeout de red (SocketTimeoutException): lanza AiProviderException TIMEOUT y no devuelve texto")
        void givenNetworkSocketTimeout_whenGenerateAnswer_thenThrowsTimeoutAndNoTextReturned() {
            // Simulamos timeout de red a nivel de socket
            SocketTimeoutException rootCause = new SocketTimeoutException("connect timed out");
            RuntimeException networkException = new RuntimeException("Timeout esperando respuesta de red", rootCause);
            when(requestSpec.call()).thenThrow(networkException);

            AiProviderException exception = assertThrows(
                    AiProviderException.class,
                    () -> answerAdapter.generateAnswer("¿Cuál es el tiempo límite para cancelar?", List.of(SAMPLE_CHUNK)),
                    "Debe propagar AiProviderException en vez de retornar texto simulado"
            );

            assertEquals(ErrorType.TIMEOUT, exception.getErrorType(),
                    "El ErrorType debe ser TIMEOUT ante fallo de timeout de red");
        }

        @Test
        @DisplayName("Fallo de red al invocar con KnowledgeQuery: propaga AiProviderException UNAVAILABLE sin devolver texto")
        void givenNetworkFailure_whenGenerateAnswerWithKnowledgeQuery_thenThrowsUnavailable() {
            when(requestSpec.call()).thenThrow(new RuntimeException("Host unreachable: api.google.generativelanguage"));

            KnowledgeQuery query = new KnowledgeQuery("¿Cómo reprogramo mi cita?");

            AiProviderException exception = assertThrows(
                    AiProviderException.class,
                    () -> answerAdapter.generateAnswer(query, List.of(SAMPLE_CHUNK))
            );

            assertEquals(ErrorType.UNAVAILABLE, exception.getErrorType());
        }

        @Test
        @DisplayName("Respuesta vacía o en blanco del ChatClient se rechaza como UNAVAILABLE sin retornar contenido simulado")
        void givenEmptyResponseFromChatClient_whenGenerateAnswer_thenThrowsUnavailable() {
            when(requestSpec.call()).thenReturn(responseSpec);
            when(responseSpec.content()).thenReturn("   ");

            AiProviderException exception = assertThrows(
                    AiProviderException.class,
                    () -> answerAdapter.generateAnswer("¿Pregunta?", List.of(SAMPLE_CHUNK))
            );

            assertEquals(ErrorType.UNAVAILABLE, exception.getErrorType());
            assertTrue(exception.getMessage().contains("empty response"));
        }
    }
}
