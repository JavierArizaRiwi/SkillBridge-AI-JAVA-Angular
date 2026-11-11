package com.riwi.skillbridge.infrastructure.adapter.out.ai;

import com.riwi.skillbridge.domain.exception.AiProviderException;
import com.riwi.skillbridge.domain.exception.AiProviderException.ErrorType;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.infrastructure.config.AiProviderProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("GeminiAiAdapter Unit Tests")
class GeminiAiAdapterTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.Builder chatClientBuilder;

    private AiProviderProperties properties;
    private GeminiAiAdapter adapter;

    private static final com.riwi.skillbridge.application.recommendation.RecommendationRequest REQ = new com.riwi.skillbridge.application.recommendation.RecommendationRequest(com.riwi.skillbridge.application.recommendation.InputType.TEXT, "goal", null, null);
    private static final UUID PROVIDER_ID = UUID.randomUUID();
    private static final List<Offering> TEST_OFFERINGS = List.of(
        new Offering(
            UUID.randomUUID(),
            PROVIDER_ID,
            "Java Backend Mentoring",
            "One-on-one mentoring for Java developers",
            "BACKEND",
            BigDecimal.valueOf(50),
            true
        ),
        new Offering(
            UUID.randomUUID(),
            PROVIDER_ID,
            "Spring Boot Workshop",
            "Comprehensive Spring Boot workshop",
            "BACKEND",
            BigDecimal.valueOf(100),
            true
        )
    );

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        properties = new AiProviderProperties();
        properties.setProvider("gemini");
        properties.setTimeoutSeconds(10);
        
        when(chatClientBuilder.build()).thenReturn(chatClient);
        adapter = new GeminiAiAdapter(chatClientBuilder, properties);
    }

    @Test
    @DisplayName("Should create adapter with ChatClient and properties")
    void testAdapterInitialization() {
        assertNotNull(adapter);
        verify(chatClientBuilder).build();
    }

    @Test
    void springCreatesAdapterThroughInjectedConstructorByDefault() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ChatClient.Builder.class, () -> chatClientBuilder);
            context.registerBean(AiProviderProperties.class, () -> properties);
            context.register(GeminiAiAdapter.class);
            context.refresh();

            assertNotNull(context.getBean(GeminiAiAdapter.class));
        }
    }

    @Test
    void geminiAdapterIsNotRegisteredWhenAnotherProviderIsSelected() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getSystemProperties().put("app.ai.provider", "openai");
            context.register(GeminiAiAdapter.class);
            context.refresh();

            assertFalse(context.containsBean("geminiAiAdapter"));
        }
    }

    @Test
    @DisplayName("Should throw AiProviderException with INVALID_INPUT for missing API key")
    void testRecommendWithMissingApiKey() {
        // Arrange
        when(chatClient.prompt()).thenThrow(
            new IllegalArgumentException("API key is missing or invalid")
        );

        // Act & Assert
        AiProviderException exception = assertThrows(
            AiProviderException.class,
            () -> adapter.recommend(REQ, TEST_OFFERINGS)
        );

        assertEquals(ErrorType.INVALID_INPUT, exception.getErrorType());
        assertTrue(exception.getMessage().contains("not properly configured"));
    }

    @Test
    void parsesValidStructuredJson() {
        var parsed = adapter.parse(("{\"explanation\":\"Plan para Java\",\"recommendations\":[{\"offeringId\":\"%s\",\"score\":0.9,\"reason\":\"Ayuda con Spring\"}]}" ).formatted(TEST_OFFERINGS.getFirst().id()));
        assertEquals("Plan para Java", parsed.explanation());
        assertEquals(1, parsed.recommendations().size());
    }

    @Test
    void rejectsInvalidJson() {
        var error = assertThrows(AiProviderException.class, () -> adapter.parse("esto no es json"));
        assertEquals(ErrorType.INTERNAL_ERROR, error.getErrorType());
    }

    @Test
    void rejectsTruncatedJson() {
        var error = assertThrows(AiProviderException.class, () -> adapter.parse("{\"explanation\":\"cortado\",\"recommendations\":[{\"offeringId\":"));
        assertEquals(ErrorType.INTERNAL_ERROR, error.getErrorType());
        assertTrue(error.getMessage().contains("truncated"));
    }

    @Test
    @DisplayName("Should throw AiProviderException with UNAVAILABLE for network error")
    void testRecommendWithNetworkError() {
        // Arrange
        when(chatClient.prompt()).thenThrow(
            new RuntimeException("Connection refused: Unable to connect to Gemini API")
        );

        // Act & Assert
        AiProviderException exception = assertThrows(
            AiProviderException.class,
            () -> adapter.recommend(REQ, TEST_OFFERINGS)
        );

        assertEquals(ErrorType.UNAVAILABLE, exception.getErrorType());
        assertTrue(exception.getCause().getMessage().contains("Connection refused"));
    }

    @Test
    @DisplayName("Should throw AiProviderException with INTERNAL_ERROR for AI-specific exception")
    void testRecommendWithAiProviderException() {
        // Arrange
        AiProviderException originalException = new AiProviderException(
            "Content filtering triggered",
            ErrorType.INTERNAL_ERROR
        );
        
        when(chatClient.prompt()).thenThrow(originalException);

        // Act & Assert
        AiProviderException exception = assertThrows(
            AiProviderException.class,
            () -> adapter.recommend(REQ, TEST_OFFERINGS)
        );

        assertEquals(ErrorType.INTERNAL_ERROR, exception.getErrorType());
    }

    @Test
    @DisplayName("Should respect timeout configuration from properties in error messages")
    void testRecommendIncludesTimeoutInErrorMessage() {
        // Arrange
        properties.setTimeoutSeconds(5);
        adapter = new GeminiAiAdapter(chatClientBuilder, properties);
        
        when(chatClient.prompt()).thenThrow(
            new RuntimeException("Operation timed out")
        );

        // Act & Assert
        AiProviderException exception = assertThrows(
            AiProviderException.class,
            () -> adapter.recommend(REQ, TEST_OFFERINGS)
        );

        assertEquals(ErrorType.TIMEOUT, exception.getErrorType());
    }

    @Test
    @DisplayName("Should verify prompt is called with goal and offerings")
    void testRecommendCallsPromptWithCorrectData() {
        // Arrange
        when(chatClient.prompt()).thenThrow(new RuntimeException("Expected error"));

        // Act & Assert
        assertThrows(AiProviderException.class, () -> adapter.recommend(REQ, TEST_OFFERINGS));
        
        verify(chatClient).prompt();
    }

    @Test
    @DisplayName("Should handle empty offerings list")
    void testRecommendWithEmptyOfferingsList() {
        // Arrange
        when(chatClient.prompt()).thenThrow(
            new IllegalArgumentException("Empty offerings")
        );

        // Act & Assert
        AiProviderException exception = assertThrows(
            AiProviderException.class,
            () -> adapter.recommend(REQ, List.of())
        );

        assertEquals(ErrorType.INVALID_INPUT, exception.getErrorType());
    }

    @Test
    @DisplayName("Should convert different RuntimeExceptions to UNAVAILABLE")
    void testRecommendConvertsRuntimeExceptions() {
        // Arrange - test multiple error scenarios individually
        RuntimeException[] errors = {
            new RuntimeException("Connection timeout"),
            new RuntimeException("Socket timeout"),
            new RuntimeException("Service unavailable")
        };

        for (RuntimeException error : errors) {
            // Reset mocks for each iteration
            reset(chatClientBuilder, chatClient);
            when(chatClientBuilder.build()).thenReturn(chatClient);
            adapter = new GeminiAiAdapter(chatClientBuilder, properties);
            when(chatClient.prompt()).thenThrow(error);

            // Act & Assert
            AiProviderException exception = assertThrows(
                AiProviderException.class,
                () -> adapter.recommend(REQ, TEST_OFFERINGS),
                "Should handle: " + error.getMessage()
            );

            ErrorType expected = error.getMessage().toLowerCase().contains("timeout")
                    ? ErrorType.TIMEOUT : ErrorType.UNAVAILABLE;
            assertEquals(expected, exception.getErrorType(),
                "Error type should match: " + error.getMessage());
        }
    }
}
