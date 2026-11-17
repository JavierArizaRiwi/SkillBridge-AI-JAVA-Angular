package com.riwi.skillbridge.infrastructure.adapter.out.ai.fallback;

import com.riwi.skillbridge.application.port.out.VisionPort;
import com.riwi.skillbridge.domain.exception.AiProviderException;
import com.riwi.skillbridge.domain.exception.AiProviderException.ErrorType;
import com.riwi.skillbridge.infrastructure.adapter.out.ai.candidate.AiModelCandidate;
import com.riwi.skillbridge.infrastructure.adapter.out.ai.classification.AiErrorClassifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class FallbackVisionAdapterTest {

    private final AiErrorClassifier classifier = new AiErrorClassifier();
    private VisionPort primaryVision;
    private VisionPort secondaryVision;
    private FallbackVisionAdapter fallbackAdapter;

    private static final byte[] IMAGE = new byte[]{4, 5, 6};
    private static final String TYPE = "image/png";
    private static final String FILENAME = "certificate.png";

    @BeforeEach
    void setUp() {
        primaryVision = mock(VisionPort.class);
        secondaryVision = mock(VisionPort.class);
    }

    @Test
    @DisplayName("Scenario 2.2: Primary vision succeeds -> returns context without invoking secondary")
    void shouldReturnPrimaryVisionContextWhenHealthy() {
        when(primaryVision.extractContext(IMAGE, TYPE, FILENAME)).thenReturn("Java certification context");

        List<FallbackVisionAdapter.CandidateExecutor> candidates = List.of(
                new FallbackVisionAdapter.CandidateExecutor(
                        AiModelCandidate.of("gemini", "gemini-3.8-flash", 1, 10),
                        primaryVision
                ),
                new FallbackVisionAdapter.CandidateExecutor(
                        AiModelCandidate.of("openai", "gpt-4o-mini", 2, 10),
                        secondaryVision
                )
        );

        fallbackAdapter = new FallbackVisionAdapter(candidates, classifier);

        String result = fallbackAdapter.extractContext(IMAGE, TYPE, FILENAME);

        assertThat(result).isEqualTo("Java certification context");
        verify(primaryVision).extractContext(IMAGE, TYPE, FILENAME);
        verifyNoInteractions(secondaryVision);
    }

    @Test
    @DisplayName("Scenario 2.2: Primary vision fails with recoverable 5xx -> switches to secondary successfully")
    void shouldFallbackToSecondaryOnVisionFailure() {
        when(primaryVision.extractContext(IMAGE, TYPE, FILENAME))
                .thenThrow(new AiProviderException("Vision 503", ErrorType.UNAVAILABLE));
        when(secondaryVision.extractContext(IMAGE, TYPE, FILENAME)).thenReturn("Secondary vision context");

        List<FallbackVisionAdapter.CandidateExecutor> candidates = List.of(
                new FallbackVisionAdapter.CandidateExecutor(
                        AiModelCandidate.of("gemini", "gemini-3.8-flash", 1, 10),
                        primaryVision
                ),
                new FallbackVisionAdapter.CandidateExecutor(
                        AiModelCandidate.of("openai", "gpt-4o-mini", 2, 10),
                        secondaryVision
                )
        );

        fallbackAdapter = new FallbackVisionAdapter(candidates, classifier);

        String result = fallbackAdapter.extractContext(IMAGE, TYPE, FILENAME);

        assertThat(result).isEqualTo("Secondary vision context");
        verify(primaryVision).extractContext(IMAGE, TYPE, FILENAME);
        verify(secondaryVision).extractContext(IMAGE, TYPE, FILENAME);
    }

    @Test
    @DisplayName("Exhaustion of vision candidates throws HTTP 503 (UNAVAILABLE)")
    void shouldThrowUnavailableWhenAllVisionCandidatesFail() {
        when(primaryVision.extractContext(IMAGE, TYPE, FILENAME))
                .thenThrow(new AiProviderException("Primary down", ErrorType.UNAVAILABLE));
        when(secondaryVision.extractContext(IMAGE, TYPE, FILENAME))
                .thenThrow(new AiProviderException("Secondary down", ErrorType.UNAVAILABLE));

        List<FallbackVisionAdapter.CandidateExecutor> candidates = List.of(
                new FallbackVisionAdapter.CandidateExecutor(
                        AiModelCandidate.of("gemini", "gemini-3.8-flash", 1, 10),
                        primaryVision
                ),
                new FallbackVisionAdapter.CandidateExecutor(
                        AiModelCandidate.of("openai", "gpt-4o-mini", 2, 10),
                        secondaryVision
                )
        );

        fallbackAdapter = new FallbackVisionAdapter(candidates, classifier);

        assertThatThrownBy(() -> fallbackAdapter.extractContext(IMAGE, TYPE, FILENAME))
                .isInstanceOf(AiProviderException.class)
                .satisfies(ex -> assertThat(((AiProviderException) ex).getErrorType()).isEqualTo(ErrorType.UNAVAILABLE));
    }
}
