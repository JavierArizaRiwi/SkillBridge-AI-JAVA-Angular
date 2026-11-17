package com.riwi.skillbridge.infrastructure.adapter.out.ai;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
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
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Pruebas de higiene de logs para los adaptadores de conocimiento RAG (HU-26, FR-010).
 * <p>
 * Demuestra que, con una pregunta que contiene texto sensible, los logs de los adaptadores de conocimiento
 * ({@link GeminiKnowledgeAnswerAdapter}, {@link GeminiEmbeddingAdapter}):
 * <ul>
 *   <li>NO contienen el texto completo de la pregunta sensible.</li>
 *   <li>NO contienen ninguna clave de API (GEMINI_API_KEY ni GEMINI_EMBEDDING_API_KEY con su valor de prueba).</li>
 *   <li>SÍ registran el número de fragmentos (chunkCount), longitudes/conteos y el tipo de error.</li>
 * </ul>
 */
@DisplayName("Knowledge Log Hygiene Tests (HU-26, FR-010)")
class KnowledgeLogHygieneTest {

    private static final String SENSITIVE_QUESTION = "CONFIDENTIAL_USER_QUERY_CARD_9999_8888_SECRET_TOKEN";
    private static final String SENSITIVE_KEYWORD = "SECRET_TOKEN";
    private static final String TEST_GEMINI_API_KEY = "AIzaSyDummyGeminiApiKeySecret999888777";
    private static final String TEST_EMBEDDING_API_KEY = "AIzaSyEmbeddingTestSecretKey111222333";
    private static final String GEMINI_API_KEY_ENV = "GEMINI_API_KEY";
    private static final String GEMINI_EMBEDDING_API_KEY_ENV = "GEMINI_EMBEDDING_API_KEY";

    private static final KnowledgeChunk CHUNK_1 = new KnowledgeChunk(
            "source-doc-1",
            KnowledgeChunk.SourceType.POLICY_DOC,
            "Política de Reembolsos",
            "Los reembolsos se procesan dentro de 5 días hábiles.",
            KnowledgeChunk.Language.ES,
            "hash-refund-001"
    );

    private static final KnowledgeChunk CHUNK_2 = new KnowledgeChunk(
            "source-doc-2",
            KnowledgeChunk.SourceType.POLICY_DOC,
            "Términos de Servicio",
            "El acceso al servicio requiere autenticación válida.",
            KnowledgeChunk.Language.ES,
            "hash-terms-002"
    );

    private static final KnowledgeChunk CHUNK_3 = new KnowledgeChunk(
            "source-doc-3",
            KnowledgeChunk.SourceType.OFFERING,
            "Tutoría de Arquitectura",
            "Arquitectura hexagonal y diseño dirigido por el dominio.",
            KnowledgeChunk.Language.ES,
            "hash-offering-003"
    );

    private static final List<KnowledgeChunk> THREE_CHUNKS = List.of(CHUNK_1, CHUNK_2, CHUNK_3);

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec responseSpec;

    @Mock
    private EmbeddingModel embeddingModel;

    private AutoCloseable mockitoCloseable;
    private KnowledgeProperties properties;
    private KnowledgePromptBuilder promptBuilder;
    private GeminiKnowledgeAnswerAdapter answerAdapter;
    private GeminiEmbeddingAdapter embeddingAdapter;

    private ListAppender<ILoggingEvent> logAppender;
    private Logger answerLogger;
    private Logger embeddingLogger;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);

        properties = new KnowledgeProperties();
        properties.setTimeoutSeconds(2);
        properties.setMaxContextChars(4000);

        promptBuilder = new KnowledgePromptBuilder(properties.getMaxContextChars());

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("Respuesta válida generada [source-doc-1].");

        when(embeddingModel.embed(anyList())).thenAnswer(inv -> {
            List<?> list = inv.getArgument(0);
            return list.stream().map(t -> new float[768]).toList();
        });

        answerAdapter = new GeminiKnowledgeAnswerAdapter(chatClient, properties, promptBuilder);
        embeddingAdapter = new GeminiEmbeddingAdapter(embeddingModel, properties, TEST_EMBEDDING_API_KEY);

        answerLogger = (Logger) LoggerFactory.getLogger(GeminiKnowledgeAnswerAdapter.class);
        embeddingLogger = (Logger) LoggerFactory.getLogger(GeminiEmbeddingAdapter.class);

        logAppender = new ListAppender<>();
        logAppender.start();
        answerLogger.addAppender(logAppender);
        embeddingLogger.addAppender(logAppender);

        CorrelationIdHolder.clear();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (answerAdapter != null) {
            answerAdapter.destroy();
        }
        if (embeddingAdapter != null) {
            embeddingAdapter.close();
        }
        if (answerLogger != null && logAppender != null) {
            answerLogger.detachAppender(logAppender);
        }
        if (embeddingLogger != null && logAppender != null) {
            embeddingLogger.detachAppender(logAppender);
        }
        if (logAppender != null) {
            logAppender.stop();
        }
        CorrelationIdHolder.clear();
        if (mockitoCloseable != null) {
            mockitoCloseable.close();
        }
    }

    @Test
    @DisplayName("Prueba: la pregunta con texto sensible no aparece en ningún log capturado")
    void sensitiveQuestionMustNotAppearInAnyCapturedLog() {
        // 1. Ejecución exitosa de respuesta con texto sensible
        String answer = answerAdapter.generateAnswer(SENSITIVE_QUESTION, THREE_CHUNKS);
        assertNotNull(answer);

        // 2. Ejecución con sobrecarga KnowledgeQuery
        String answerQuery = answerAdapter.generateAnswer(new KnowledgeQuery(SENSITIVE_QUESTION), THREE_CHUNKS);
        assertNotNull(answerQuery);

        // 3. Fallo por error de proveedor en generación de respuesta
        doThrow(new RuntimeException("503 Service Unavailable: temporary outage"))
                .when(requestSpec).call();
        assertThrows(AiProviderException.class, () -> answerAdapter.generateAnswer(SENSITIVE_QUESTION, THREE_CHUNKS));

        // 4. Fallo por timeout en generación de respuesta
        properties.setTimeoutSeconds(1);
        doAnswer(inv -> {
            Thread.sleep(2500);
            return responseSpec;
        }).when(requestSpec).call();
        assertThrows(AiProviderException.class, () -> answerAdapter.generateAnswer(SENSITIVE_QUESTION, THREE_CHUNKS));

        // 5. Fallo por respuesta vacía
        doReturn(responseSpec).when(requestSpec).call();
        when(responseSpec.content()).thenReturn("   ");
        assertThrows(AiProviderException.class, () -> answerAdapter.generateAnswer(SENSITIVE_QUESTION, THREE_CHUNKS));

        // 6. Ejecución exitosa de embeddings con texto sensible
        List<double[]> embeddings = embeddingAdapter.embed(List.of(SENSITIVE_QUESTION));
        assertEquals(1, embeddings.size());

        // 7. Ejecución con sobrecarga de un solo texto
        double[] singleEmbedding = embeddingAdapter.embed(SENSITIVE_QUESTION);
        assertEquals(768, singleEmbedding.length);

        // 8. Fallo por error de proveedor en embeddings
        doThrow(new RuntimeException("Connection refused to gemini endpoint"))
                .when(embeddingModel).embed(anyList());
        assertThrows(AiProviderException.class, () -> embeddingAdapter.embed(List.of(SENSITIVE_QUESTION)));

        // 9. Fallo por timeout en embeddings
        doAnswer(inv -> {
            Thread.sleep(2500);
            return List.of(new float[768]);
        }).when(embeddingModel).embed(anyList());
        assertThrows(AiProviderException.class, () -> embeddingAdapter.embed(List.of(SENSITIVE_QUESTION)));

        // 10. Fallo por conteo inválido en embeddings
        doReturn(Collections.emptyList()).when(embeddingModel).embed(anyList());
        assertThrows(AiProviderException.class, () -> embeddingAdapter.embed(List.of(SENSITIVE_QUESTION)));

        // Verificación de higiene sobre todos los logs capturados
        List<ILoggingEvent> events = logAppender.list;
        assertFalse(events.isEmpty(), "Se deben haber capturado eventos de log durante las ejecuciones");

        for (ILoggingEvent event : events) {
            String formattedMessage = event.getFormattedMessage();

            assertFalse(formattedMessage.contains(SENSITIVE_QUESTION),
                    "El log contiene el texto completo de la pregunta sensible: " + formattedMessage);

            assertFalse(formattedMessage.contains(SENSITIVE_KEYWORD),
                    "El log contiene la palabra clave sensible de la pregunta: " + formattedMessage);

            IThrowableProxy throwableProxy = event.getThrowableProxy();
            if (throwableProxy != null && throwableProxy.getMessage() != null) {
                assertFalse(throwableProxy.getMessage().contains(SENSITIVE_QUESTION),
                        "La excepción adjunta en log contiene la pregunta sensible: " + throwableProxy.getMessage());
            }
        }
    }

    @Test
    @DisplayName("Prueba: el valor de la clave de prueba no aparece en ningún log capturado")
    void apiKeyValueMustNotAppearInAnyCapturedLog() {
        CorrelationIdHolder.set("corr-api-key-test");

        // 1. Ejecución con respuesta normal
        answerAdapter.generateAnswer("¿Cuál es la política?", THREE_CHUNKS);

        // 2. Ejecución con error de proveedor
        doThrow(new RuntimeException("401 Unauthorized: Access denied"))
                .when(requestSpec).call();
        assertThrows(AiProviderException.class, () -> answerAdapter.generateAnswer("Pregunta normal", THREE_CHUNKS));

        // 3. Ejecución de embeddings con clave configurada
        embeddingAdapter.embed(List.of("Texto para indexar"));

        // 4. Error en embeddings
        doThrow(new RuntimeException("403 Forbidden: Access denied"))
                .when(embeddingModel).embed(anyList());
        assertThrows(AiProviderException.class, () -> embeddingAdapter.embed(List.of("Texto para indexar")));

        // 5. Adaptador de embeddings sin clave configurada (rechazo temprano)
        GeminiEmbeddingAdapter unconfiguredAdapter = new GeminiEmbeddingAdapter(embeddingModel, properties, "");
        try {
            assertThrows(AiProviderException.class, () -> unconfiguredAdapter.embed(List.of("Texto para indexar")));
        } finally {
            unconfiguredAdapter.close();
        }

        // Verificación de higiene sobre todos los logs capturados
        List<ILoggingEvent> events = logAppender.list;
        assertFalse(events.isEmpty(), "Se deben haber capturado eventos de log");

        for (ILoggingEvent event : events) {
            String message = event.getFormattedMessage();

            assertFalse(message.contains(TEST_GEMINI_API_KEY),
                    "El log reveló el valor de la clave GEMINI_API_KEY de prueba: " + message);

            assertFalse(message.contains(TEST_EMBEDDING_API_KEY),
                    "El log reveló el valor de la clave GEMINI_EMBEDDING_API_KEY de prueba: " + message);

            assertFalse(message.contains(GEMINI_API_KEY_ENV),
                    "El log contiene el nombre de variable GEMINI_API_KEY: " + message);

            assertFalse(message.contains(GEMINI_EMBEDDING_API_KEY_ENV),
                    "El log contiene el nombre de variable GEMINI_EMBEDDING_API_KEY: " + message);
        }
    }

    @Test
    @DisplayName("Prueba: el log de error incluye el tipo de error y el número de fragmentos")
    void errorLogsMustIncludeErrorTypeAndChunkCount() {
        CorrelationIdHolder.set("corr-error-verification");

        // 1. Error de proveedor UNAVAILABLE
        doThrow(new RuntimeException("java.net.ConnectException: Connection refused"))
                .when(requestSpec).call();
        AiProviderException ex1 = assertThrows(AiProviderException.class,
                () -> answerAdapter.generateAnswer(SENSITIVE_QUESTION, THREE_CHUNKS));
        assertEquals(ErrorType.UNAVAILABLE, ex1.getErrorType());

        // 2. Error de cuota / rate limit RATE_LIMITED
        doThrow(new RuntimeException("429 Too Many Requests: Resource quota exhausted"))
                .when(requestSpec).call();
        AiProviderException ex2 = assertThrows(AiProviderException.class,
                () -> answerAdapter.generateAnswer(SENSITIVE_QUESTION, THREE_CHUNKS));
        assertEquals(ErrorType.RATE_LIMITED, ex2.getErrorType());

        // 3. Error por Timeout TIMEOUT
        properties.setTimeoutSeconds(1);
        doAnswer(inv -> {
            Thread.sleep(2500);
            return responseSpec;
        }).when(requestSpec).call();
        AiProviderException ex3 = assertThrows(AiProviderException.class,
                () -> answerAdapter.generateAnswer(SENSITIVE_QUESTION, THREE_CHUNKS));
        assertEquals(ErrorType.TIMEOUT, ex3.getErrorType());

        // 4. Error por respuesta vacía UNAVAILABLE
        doReturn(responseSpec).when(requestSpec).call();
        when(responseSpec.content()).thenReturn("   ");
        AiProviderException ex4 = assertThrows(AiProviderException.class,
                () -> answerAdapter.generateAnswer(SENSITIVE_QUESTION, THREE_CHUNKS));
        assertEquals(ErrorType.UNAVAILABLE, ex4.getErrorType());

        // 5. Error en embedding adapter con conteo / longitud y tipo de error
        doThrow(new RuntimeException("503 Service Unavailable"))
                .when(embeddingModel).embed(anyList());
        AiProviderException ex5 = assertThrows(AiProviderException.class,
                () -> embeddingAdapter.embed(List.of("doc1", "doc2")));
        assertEquals(ErrorType.UNAVAILABLE, ex5.getErrorType());

        // 6. Error en embedding adapter por conteo inválido (esperado vs real)
        doReturn(List.of(new float[768]))
                .when(embeddingModel).embed(anyList());
        AiProviderException ex6 = assertThrows(AiProviderException.class,
                () -> embeddingAdapter.embed(List.of("doc1", "doc2")));
        assertEquals(ErrorType.UNAVAILABLE, ex6.getErrorType());

        // Validar eventos de log de nivel ERROR
        List<ILoggingEvent> errorEvents = logAppender.list.stream()
                .filter(e -> e.getLevel() == Level.ERROR)
                .toList();

        assertFalse(errorEvents.isEmpty(), "Deben existir eventos de log de nivel ERROR");

        boolean foundUnavailableWithChunks = false;
        boolean foundRateLimitedWithChunks = false;
        boolean foundTimeoutWithChunks = false;
        boolean foundEmptyResponseWithChunks = false;
        boolean foundEmbeddingErrorType = false;
        boolean foundEmbeddingLengthMismatch = false;

        for (ILoggingEvent event : errorEvents) {
            String msg = event.getFormattedMessage();

            if (msg.contains("chunkCount=3") && msg.contains("errorType=UNAVAILABLE")) {
                foundUnavailableWithChunks = true;
            }
            if (msg.contains("chunkCount=3") && msg.contains("errorType=RATE_LIMITED")) {
                foundRateLimitedWithChunks = true;
            }
            if (msg.contains("chunkCount=3") && msg.contains("errorType=TIMEOUT")) {
                foundTimeoutWithChunks = true;
            }
            if (msg.contains("Gemini devolvió respuesta vacía") && msg.contains("chunkCount=3") && msg.contains("errorType=UNAVAILABLE")) {
                foundEmptyResponseWithChunks = true;
            }
            if (msg.contains("type=UNAVAILABLE")) {
                foundEmbeddingErrorType = true;
            }
            if (msg.contains("expected=2") && msg.contains("actual=1")) {
                foundEmbeddingLengthMismatch = true;
            }
        }

        assertTrue(foundUnavailableWithChunks,
                "El log de error debe registrar errorType=UNAVAILABLE y chunkCount=3");
        assertTrue(foundRateLimitedWithChunks,
                "El log de error debe registrar errorType=RATE_LIMITED y chunkCount=3");
        assertTrue(foundTimeoutWithChunks,
                "El log de error debe registrar errorType=TIMEOUT y chunkCount=3");
        assertTrue(foundEmptyResponseWithChunks,
                "El log de error de respuesta vacía debe registrar errorType=UNAVAILABLE y chunkCount=3");
        assertTrue(foundEmbeddingErrorType,
                "El log de error de embeddings debe registrar el tipo de error (type=UNAVAILABLE)");
        assertTrue(foundEmbeddingLengthMismatch,
                "El log de error de embeddings debe registrar las longitudes/conteos (expected=2, actual=1)");
    }

    @Test
    @DisplayName("Los logs exitosos registran correlationId y número de fragmentos sin incluir el texto sensible")
    void successLogsMustRecordCorrelationIdAndChunkCount() {
        CorrelationIdHolder.set("corr-audit-success-777");

        String response = answerAdapter.generateAnswer(SENSITIVE_QUESTION, THREE_CHUNKS);
        assertNotNull(response);

        List<ILoggingEvent> infoEvents = logAppender.list.stream()
                .filter(e -> e.getLevel() == Level.INFO)
                .toList();

        assertFalse(infoEvents.isEmpty(), "Deben existir eventos de log de nivel INFO");

        boolean foundStartLog = false;
        boolean foundCompletionLog = false;

        for (ILoggingEvent event : infoEvents) {
            String msg = event.getFormattedMessage();

            assertFalse(msg.contains(SENSITIVE_QUESTION),
                    "Log INFO no debe contener el texto de la pregunta");

            if (msg.contains("Iniciando generación de respuesta de conocimiento")
                    && msg.contains("correlationId=corr-audit-success-777")
                    && msg.contains("chunkCount=3")) {
                foundStartLog = true;
            }

            if (msg.contains("Respuesta de conocimiento generada exitosamente")
                    && msg.contains("correlationId=corr-audit-success-777")
                    && msg.contains("chunkCount=3")) {
                foundCompletionLog = true;
            }
        }

        assertTrue(foundStartLog, "El log inicial debe incluir correlationId y chunkCount=3");
        assertTrue(foundCompletionLog, "El log de éxito debe incluir correlationId y chunkCount=3");
    }
}
