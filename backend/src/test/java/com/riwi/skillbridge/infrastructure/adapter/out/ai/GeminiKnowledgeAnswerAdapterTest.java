package com.riwi.skillbridge.infrastructure.adapter.out.ai;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("GeminiKnowledgeAnswerAdapter Unit Tests")
class GeminiKnowledgeAnswerAdapterTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec responseSpec;

    @Mock
    private ChatClient.Builder chatClientBuilder;

    private KnowledgeProperties properties;
    private KnowledgePromptBuilder promptBuilder;
    private GeminiKnowledgeAnswerAdapter adapter;

    private ListAppender<ILoggingEvent> logAppender;
    private Logger adapterLogger;

    private static final KnowledgeChunk SAMPLE_CHUNK = new KnowledgeChunk(
            "cancellation-policy",
            KnowledgeChunk.SourceType.POLICY_DOC,
            "Política de Cancelación",
            "Las reservas se cancelan con al menos 24 horas de anticipación.",
            KnowledgeChunk.Language.ES,
            "hash-1234"
    );

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        properties = new KnowledgeProperties();
        properties.setTimeoutSeconds(5);
        properties.setMaxContextChars(6000);

        promptBuilder = new KnowledgePromptBuilder(properties.getMaxContextChars());

        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);

        adapter = new GeminiKnowledgeAnswerAdapter(chatClient, properties, promptBuilder);

        adapterLogger = (Logger) LoggerFactory.getLogger(GeminiKnowledgeAnswerAdapter.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        adapterLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        adapter.destroy();
        if (adapterLogger != null && logAppender != null) {
            adapterLogger.detachAppender(logAppender);
        }
        CorrelationIdHolder.clear();
    }

    @Test
    @DisplayName("(a) Should generate correct answer with ChatClient double")
    void shouldGenerateCorrectAnswerWithChatClientDouble() {
        String expectedAnswer = "Puedes cancelar hasta con 24 horas de anticipación [cancellation-policy].";
        when(responseSpec.content()).thenReturn(expectedAnswer);

        String answer = adapter.generateAnswer("¿Cómo cancelo mi reserva?", List.of(SAMPLE_CHUNK));

        assertEquals(expectedAnswer, answer);
        verify(chatClient).prompt();

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).user(promptCaptor.capture());
        String capturedPrompt = promptCaptor.getValue();
        assertTrue(capturedPrompt.contains("¿Cómo cancelo mi reserva?"));
        assertTrue(capturedPrompt.contains("cancellation-policy"));
        assertTrue(capturedPrompt.contains(KnowledgePromptBuilder.SYSTEM_INSTRUCTIONS));

        verify(requestSpec).call();
        verify(responseSpec).content();
    }

    @Test
    @DisplayName("(b) Slow double should trigger TIMEOUT within timeout + 1 s")
    void slowDoubleShouldTriggerTimeoutWithinTimeoutPlusOneSecond() {
        properties.setTimeoutSeconds(1);
        when(responseSpec.content()).thenAnswer(invocation -> {
            Thread.sleep(3000);
            return "Respuesta tardía";
        });

        long start = System.currentTimeMillis();
        AiProviderException ex = assertThrows(
                AiProviderException.class,
                () -> adapter.generateAnswer("¿Cuál es la política?", List.of(SAMPLE_CHUNK))
        );
        long elapsed = System.currentTimeMillis() - start;

        assertEquals(ErrorType.TIMEOUT, ex.getErrorType());
        assertTrue(elapsed >= 900, "Should have waited around the configured timeout (elapsed: " + elapsed + "ms)");
        assertTrue(elapsed < 2000, "Should abort within timeout + 1 s (elapsed: " + elapsed + "ms)");
    }

    @Test
    @DisplayName("(c) Provider failure should map to UNAVAILABLE")
    void providerFailureShouldMapToUnavailable() {
        when(requestSpec.call()).thenThrow(new RuntimeException("Connection refused: api.google.generativelanguage..."));

        AiProviderException ex = assertThrows(
                AiProviderException.class,
                () -> adapter.generateAnswer("¿Cómo funciona?", List.of(SAMPLE_CHUNK))
        );

        assertEquals(ErrorType.UNAVAILABLE, ex.getErrorType());
        assertTrue(ex.getMessage().contains("UNAVAILABLE"));
    }

    @Test
    @DisplayName("(d) Logs must never contain the question text or secrets")
    void logsMustNeverContainQuestionTextOrSecrets() {
        String sensitiveQuestion = "SUPER_SECRET_QUESTION_PASS_KEY_987654";
        CorrelationIdHolder.set("corr-audit-test-999");

        try {
            // 1. Successful call
            when(responseSpec.content()).thenReturn("Respuesta válida [cancellation-policy].");
            adapter.generateAnswer(sensitiveQuestion, List.of(SAMPLE_CHUNK));

            // 2. Failure call
            doThrow(new RuntimeException("Error inesperado en servicio")).when(requestSpec).call();
            assertThrows(AiProviderException.class, () -> adapter.generateAnswer(sensitiveQuestion, List.of(SAMPLE_CHUNK)));

            // 3. Timeout call
            properties.setTimeoutSeconds(1);
            doAnswer(inv -> {
                Thread.sleep(2500);
                return responseSpec;
            }).when(requestSpec).call();
            assertThrows(AiProviderException.class, () -> adapter.generateAnswer(sensitiveQuestion, List.of(SAMPLE_CHUNK)));

            // Check log events
            List<ILoggingEvent> events = logAppender.list;
            assertFalse(events.isEmpty(), "Logs should have been recorded");

            boolean foundCorrelationId = false;
            boolean foundChunkCount = false;

            for (ILoggingEvent event : events) {
                String message = event.getFormattedMessage();

                assertFalse(message.contains(sensitiveQuestion),
                        "Log message leaked sensitive question text: " + message);

                assertFalse(message.contains("GEMINI_API_KEY"),
                        "Log message leaked GEMINI_API_KEY: " + message);

                assertFalse(message.contains("GEMINI_EMBEDDING_API_KEY"),
                        "Log message leaked GEMINI_EMBEDDING_API_KEY: " + message);

                if (message.contains("corr-audit-test-999")) {
                    foundCorrelationId = true;
                }
                if (message.contains("chunkCount=1")) {
                    foundChunkCount = true;
                }
            }

            assertTrue(foundCorrelationId, "Logs should record correlationId when present");
            assertTrue(foundChunkCount, "Logs should record chunk count");
        } finally {
            CorrelationIdHolder.clear();
        }
    }

    @Test
    @DisplayName("Provider 429 / quota error should map to RATE_LIMITED")
    void rateLimitErrorShouldMapToRateLimited() {
        when(requestSpec.call()).thenThrow(new RuntimeException("429 Too Many Requests: Resource has been exhausted (e.g. check quota)"));

        AiProviderException ex = assertThrows(
                AiProviderException.class,
                () -> adapter.generateAnswer("¿Cómo funciona?", List.of(SAMPLE_CHUNK))
        );

        assertEquals(ErrorType.RATE_LIMITED, ex.getErrorType());
    }

    @Test
    @DisplayName("Empty or blank response from provider should throw UNAVAILABLE")
    void emptyResponseShouldThrowUnavailable() {
        when(responseSpec.content()).thenReturn("   ");

        AiProviderException ex = assertThrows(
                AiProviderException.class,
                () -> adapter.generateAnswer("¿Cómo funciona?", List.of(SAMPLE_CHUNK))
        );

        assertEquals(ErrorType.UNAVAILABLE, ex.getErrorType());
        assertTrue(ex.getMessage().contains("empty response"));
    }

    @Test
    @DisplayName("Null or blank question should throw IllegalArgumentException")
    void nullOrBlankQuestionShouldThrowIllegalArgumentException() {
        String nullStr = null;
        assertThrows(IllegalArgumentException.class, () -> adapter.generateAnswer(nullStr, List.of(SAMPLE_CHUNK)));
        assertThrows(IllegalArgumentException.class, () -> adapter.generateAnswer("   ", List.of(SAMPLE_CHUNK)));
    }

    @Test
    @DisplayName("Should delegate from KnowledgeQuery overload")
    void shouldDelegateFromKnowledgeQuery() {
        when(responseSpec.content()).thenReturn("Respuesta con query");
        KnowledgeQuery query = new KnowledgeQuery("¿Cómo cancelo?");

        String answer = adapter.generateAnswer(query, List.of(SAMPLE_CHUNK));
        assertEquals("Respuesta con query", answer);

        KnowledgeQuery nullQuery = null;
        assertThrows(IllegalArgumentException.class, () -> adapter.generateAnswer(nullQuery, List.of(SAMPLE_CHUNK)));
    }

    @Test
    @DisplayName("Spring context registers adapter with ChatClient.Builder and properties by default")
    void springRegistersAdapterByDefault() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ChatClient.Builder.class, () -> chatClientBuilder);
            context.registerBean(KnowledgeProperties.class, () -> properties);
            context.register(GeminiKnowledgeAnswerAdapter.class);
            context.refresh();

            assertNotNull(context.getBean(GeminiKnowledgeAnswerAdapter.class));
        }
    }

    @Test
    @DisplayName("Adapter is not registered when another ai provider is configured")
    void adapterIsNotRegisteredWhenOtherProviderConfigured() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("test-provider", java.util.Map.of("app.ai.provider", "openai")));
            context.register(GeminiKnowledgeAnswerAdapter.class);
            context.refresh();

            assertFalse(context.containsBean("geminiKnowledgeAnswerAdapter"));
        }
    }
}
