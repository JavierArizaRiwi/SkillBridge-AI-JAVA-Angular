package com.riwi.skillbridge.infrastructure.adapter.out.ai;

import com.riwi.skillbridge.domain.exception.AiProviderException;
import com.riwi.skillbridge.domain.exception.AiProviderException.ErrorType;
import com.riwi.skillbridge.infrastructure.config.KnowledgeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiEmbeddingConnectionAutoConfiguration;
import org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiTextEmbeddingAutoConfiguration;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@DisplayName("GeminiEmbeddingAdapter Unit Tests")
class GeminiEmbeddingAdapterTest {

    @Mock
    private EmbeddingModel embeddingModel;

    private KnowledgeProperties properties;
    private GeminiEmbeddingAdapter adapter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        properties = new KnowledgeProperties();
        properties.setTimeoutSeconds(10);
        adapter = new GeminiEmbeddingAdapter(embeddingModel, properties, "test-api-key");
    }

    @Test
    @DisplayName("(a) Debe devolver un vector por texto con las dimensiones del doble")
    void testEmbedReturnsOneVectorPerTextWithExpectedDimensions() {
        // Arrange: vector con 768 dimensiones
        float[] vector1 = new float[768];
        float[] vector2 = new float[768];
        for (int i = 0; i < 768; i++) {
            vector1[i] = i * 0.001f;
            vector2[i] = -i * 0.001f;
        }

        when(embeddingModel.embed(List.of("texto 1", "texto 2"))).thenReturn(List.of(vector1, vector2));

        // Act
        List<double[]> result = adapter.embed(List.of("texto 1", "texto 2"));

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(768, result.get(0).length);
        assertEquals(768, result.get(1).length);
        assertEquals(vector1[0], (float) result.get(0)[0], 1e-6);
        assertEquals(vector1[100], (float) result.get(0)[100], 1e-6);
        assertEquals(vector2[100], (float) result.get(1)[100], 1e-6);
        verify(embeddingModel).embed(List.of("texto 1", "texto 2"));
    }

    @Test
    @DisplayName("(a) Sobrecarga conveniente para un único texto devuelve double[] con dimensiones esperadas")
    void testEmbedSingleTextOverload() {
        float[] vector = new float[768];
        vector[0] = 0.42f;
        when(embeddingModel.embed(List.of("pregunta de usuario"))).thenReturn(List.of(vector));

        double[] result = adapter.embed("pregunta de usuario");

        assertNotNull(result);
        assertEquals(768, result.length);
        assertEquals(0.42, result[0], 1e-6);
    }

    @Test
    @DisplayName("(b) Un doble lento hace que la llamada expire y lanza AiProviderException con ErrorType.TIMEOUT en timeout + 1 s")
    void testEmbedTimesOutWhenProviderIsSlow() {
        // Arrange: timeout configurado a 1 segundo
        properties.setTimeoutSeconds(1);
        AtomicBoolean taskWasInterrupted = new AtomicBoolean(false);

        when(embeddingModel.embed(anyList())).thenAnswer(invocation -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                taskWasInterrupted.set(true);
                Thread.currentThread().interrupt();
            }
            return List.of(new float[768]);
        });

        long start = System.currentTimeMillis();

        // Act & Assert
        AiProviderException ex = assertThrows(
                AiProviderException.class,
                () -> adapter.embed(List.of("texto demorado"))
        );

        long duration = System.currentTimeMillis() - start;

        assertEquals(ErrorType.TIMEOUT, ex.getErrorType());
        assertTrue(ex.getMessage().contains("timed out after 1s"));
        assertTrue(duration >= 900, "Debe esperar al menos el tiempo de timeout (1s)");
        assertTrue(duration <= 2000, "Debe expirar dentro de timeout + 1 s (medido: " + duration + " ms)");
    }

    @Test
    @DisplayName("(c) Error del proveedor se traduce a AiProviderException con ErrorType.UNAVAILABLE")
    void testEmbedProviderErrorTranslatesToAiProviderException() {
        // Arrange
        RuntimeException providerError = new RuntimeException("503 Service Unavailable from Gemini API");
        when(embeddingModel.embed(anyList())).thenThrow(providerError);

        // Act & Assert
        AiProviderException ex = assertThrows(
                AiProviderException.class,
                () -> adapter.embed(List.of("texto de prueba"))
        );

        assertEquals(ErrorType.UNAVAILABLE, ex.getErrorType());
        assertSame(providerError, ex.getCause());
        verify(embeddingModel).embed(List.of("texto de prueba"));
    }

    @Test
    @DisplayName("(c) Error de conexión de red se clasifica como UNAVAILABLE")
    void testEmbedConnectionErrorTranslatesToUnavailable() {
        RuntimeException networkError = new RuntimeException("java.net.ConnectException: Connection refused");
        when(embeddingModel.embed(anyList())).thenThrow(networkError);

        AiProviderException ex = assertThrows(
                AiProviderException.class,
                () -> adapter.embed(List.of("texto de prueba"))
        );

        assertEquals(ErrorType.UNAVAILABLE, ex.getErrorType());
    }

    @Test
    @DisplayName("Sin clave configurada, embed() lanza AiProviderException con ErrorType.UNAVAILABLE sin llamar al proveedor")
    void testEmbedWithoutApiKeyThrowsUnavailable() {
        GeminiEmbeddingAdapter noKeyAdapter = new GeminiEmbeddingAdapter(embeddingModel, properties, "");

        AiProviderException ex = assertThrows(
                AiProviderException.class,
                () -> noKeyAdapter.embed(List.of("texto"))
        );

        assertEquals(ErrorType.UNAVAILABLE, ex.getErrorType());
        assertTrue(ex.getMessage().contains("not configured"));
        verifyNoInteractions(embeddingModel);
    }

    @Test
    @DisplayName("Con EmbeddingModel nulo, embed() lanza AiProviderException con ErrorType.UNAVAILABLE")
    void testEmbedWithNullEmbeddingModelThrowsUnavailable() {
        GeminiEmbeddingAdapter nullModelAdapter = new GeminiEmbeddingAdapter((EmbeddingModel) null, properties, "some-key");

        AiProviderException ex = assertThrows(
                AiProviderException.class,
                () -> nullModelAdapter.embed(List.of("texto"))
        );

        assertEquals(ErrorType.UNAVAILABLE, ex.getErrorType());
    }

    @Test
    @DisplayName("Lista de textos vacía o nula retorna lista vacía sin invocar al proveedor")
    void testEmbedEmptyOrNullTexts() {
        assertEquals(Collections.emptyList(), adapter.embed(Collections.emptyList()));
        assertEquals(Collections.emptyList(), adapter.embed((List<String>) null));
        verifyNoInteractions(embeddingModel);
    }

    @Test
    @DisplayName("Proveedor devuelve cantidad de vectores inconsistente -> lanza AiProviderException UNAVAILABLE")
    void testEmbedProviderReturnsMismatchedVectorCount() {
        when(embeddingModel.embed(List.of("texto 1", "texto 2"))).thenReturn(List.of(new float[768]));

        AiProviderException ex = assertThrows(
                AiProviderException.class,
                () -> adapter.embed(List.of("texto 1", "texto 2"))
        );

        assertEquals(ErrorType.UNAVAILABLE, ex.getErrorType());
    }

    @Test
    @DisplayName("Inyección y arranque de Spring con adaptador y dependencias provistas")
    void testSpringContextCreatesAdapter() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(EmbeddingModel.class, () -> embeddingModel);
            context.registerBean(KnowledgeProperties.class, () -> properties);
            context.register(GeminiEmbeddingAdapter.class);
            context.refresh();

            GeminiEmbeddingAdapter bean = context.getBean(GeminiEmbeddingAdapter.class);
            assertNotNull(bean);
        }
    }

    private static final String EXCLUDE_EMBEDDING_AUTOCONFIGS =
            "spring.autoconfigure.exclude=" +
            "org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiEmbeddingConnectionAutoConfiguration," +
            "org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiTextEmbeddingAutoConfiguration";

    @Test
    @DisplayName("Sin exclusión y sin clave, GoogleGenAiEmbeddingConnectionAutoConfiguration reproduce el fallo project-id must be set")
    void testSpringAiAutoConfigReproducesFailureWithoutExclusion() {
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withPropertyValues("spring.ai.model.embedding.text=google-genai")
                .withConfiguration(AutoConfigurations.of(
                        GoogleGenAiEmbeddingConnectionAutoConfiguration.class,
                        GoogleGenAiTextEmbeddingAutoConfiguration.class
                ));

        contextRunner.run(context -> {
            assertNotNull(context.getStartupFailure(), "Debe fallar al instanciar googleGenAiEmbeddingConnectionDetails");
            Throwable rootCause = context.getStartupFailure();
            while (rootCause.getCause() != null) {
                rootCause = rootCause.getCause();
            }
            assertTrue(
                    rootCause.getMessage().contains("project-id must be set") ||
                            rootCause.getMessage().contains("api-key"),
                    "Reproduce el fallo de project-id: " + rootCause.getMessage()
            );
        });
    }

    @Test
    @DisplayName("Con autoconfigs excluidos igual que application.yml y sin clave: arranca sin startupFailure")
    void testApplicationContextRunnerWithExcludedAutoConfigsStartsWithoutKey() {
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withPropertyValues(
                        "spring.ai.model.embedding.text=google-genai",
                        EXCLUDE_EMBEDDING_AUTOCONFIGS
                )
                .withConfiguration(AutoConfigurations.of(
                        GoogleGenAiEmbeddingConnectionAutoConfiguration.class,
                        GoogleGenAiTextEmbeddingAutoConfiguration.class
                ))
                .withUserConfiguration(
                        GeminiEmbeddingConfiguration.class,
                        GeminiEmbeddingAdapter.class,
                        KnowledgeProperties.class
                );

        contextRunner.run(context -> {
            assertNull(context.getStartupFailure(), "El contexto debe arrancar sin error con los autoconfigs excluidos");
            assertFalse(context.containsBean("googleGenAiEmbeddingConnectionDetails"), "El autoconfig original no debe ejecutarse");
            assertFalse(context.containsBean("geminiEmbeddingModel"), "geminiEmbeddingModel no debe registrarse sin clave");
            // El adaptador siempre existe (EmbeddingPort disponible); sin clave rechaza con 503 al usarse (FR-017)
            assertTrue(context.containsBean("geminiEmbeddingAdapter"), "El adaptador debe registrarse aunque no haya clave");
            org.junit.jupiter.api.Assertions.assertThrows(
                    com.riwi.skillbridge.domain.exception.AiProviderException.class,
                    () -> context.getBean(GeminiEmbeddingAdapter.class).embed("hola"));
        });
    }

    @Test
    @DisplayName("Con autoconfigs excluidos pero con clave presente: registra geminiEmbeddingModel y adaptador")
    void testApplicationContextRunnerWithExcludedAutoConfigsAndKeyRegistersModel() {
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withPropertyValues(
                        "spring.ai.model.embedding.text=google-genai",
                        "spring.ai.google.genai.embedding.api-key=test-gemini-key",
                        EXCLUDE_EMBEDDING_AUTOCONFIGS
                )
                .withConfiguration(AutoConfigurations.of(
                        GoogleGenAiEmbeddingConnectionAutoConfiguration.class,
                        GoogleGenAiTextEmbeddingAutoConfiguration.class
                ))
                .withUserConfiguration(
                        GeminiEmbeddingConfiguration.class,
                        GeminiEmbeddingAdapter.class,
                        KnowledgeProperties.class
                );

        contextRunner.run(context -> {
            assertNull(context.getStartupFailure(), "El contexto debe arrancar sin error cuando la clave está presente");
            assertFalse(context.containsBean("googleGenAiEmbeddingConnectionDetails"), "El autoconfig original sigue excluido");
            assertTrue(context.containsBean("geminiEmbeddingModel"), "Debe registrar geminiEmbeddingModel");
            assertNotNull(context.getBean(EmbeddingModel.class));
            assertTrue(context.containsBean("geminiEmbeddingAdapter"), "El adaptador debe estar disponible con la clave");
            assertNotNull(context.getBean(GeminiEmbeddingAdapter.class));
            assertNotNull(context.getBean(com.riwi.skillbridge.application.port.out.EmbeddingPort.class));
        });
    }
}
