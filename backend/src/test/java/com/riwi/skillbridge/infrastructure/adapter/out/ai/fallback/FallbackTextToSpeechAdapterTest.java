package com.riwi.skillbridge.infrastructure.adapter.out.ai.fallback;

import com.riwi.skillbridge.application.port.out.TextToSpeechPort;
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

class FallbackTextToSpeechAdapterTest {

    private final AiErrorClassifier classifier = new AiErrorClassifier();
    private TextToSpeechPort primaryTts;
    private TextToSpeechPort secondaryTts;
    private FallbackTextToSpeechAdapter fallbackAdapter;

    private static final String TEXT = "Hola, esta es tu recomendación.";
    private static final TextToSpeechPort.AudioContent AUDIO_CONTENT =
            new TextToSpeechPort.AudioContent(new byte[]{1, 2, 3}, "audio/mpeg");

    @BeforeEach
    void setUp() {
        primaryTts = mock(TextToSpeechPort.class);
        secondaryTts = mock(TextToSpeechPort.class);
    }

    @Test
    @DisplayName("Scenario 2.3: Primary TTS succeeds -> returns audio content without invoking secondary")
    void shouldReturnPrimaryAudioWhenHealthy() {
        when(primaryTts.synthesize(TEXT)).thenReturn(AUDIO_CONTENT);

        List<FallbackTextToSpeechAdapter.CandidateExecutor> candidates = List.of(
                new FallbackTextToSpeechAdapter.CandidateExecutor(
                        AiModelCandidate.of("elevenlabs", "multilingual-v2", 1, 10),
                        primaryTts
                ),
                new FallbackTextToSpeechAdapter.CandidateExecutor(
                        AiModelCandidate.of("noop", "noop", 2, 10),
                        secondaryTts
                )
        );

        fallbackAdapter = new FallbackTextToSpeechAdapter(candidates, classifier);

        TextToSpeechPort.AudioContent result = fallbackAdapter.synthesize(TEXT);

        assertThat(result).isEqualTo(AUDIO_CONTENT);
        verify(primaryTts).synthesize(TEXT);
        verifyNoInteractions(secondaryTts);
    }

    @Test
    @DisplayName("Scenario 2.3: Primary TTS fails with quota/rate limit -> switches to secondary")
    void shouldFallbackToSecondaryOnTtsQuotaExceeded() {
        when(primaryTts.synthesize(TEXT))
                .thenThrow(new AiProviderException("Quota exceeded", ErrorType.RATE_LIMITED));
        when(secondaryTts.synthesize(TEXT)).thenReturn(AUDIO_CONTENT);

        List<FallbackTextToSpeechAdapter.CandidateExecutor> candidates = List.of(
                new FallbackTextToSpeechAdapter.CandidateExecutor(
                        AiModelCandidate.of("elevenlabs", "multilingual-v2", 1, 10),
                        primaryTts
                ),
                new FallbackTextToSpeechAdapter.CandidateExecutor(
                        AiModelCandidate.of("noop", "noop", 2, 10),
                        secondaryTts
                )
        );

        fallbackAdapter = new FallbackTextToSpeechAdapter(candidates, classifier);

        TextToSpeechPort.AudioContent result = fallbackAdapter.synthesize(TEXT);

        assertThat(result).isEqualTo(AUDIO_CONTENT);
        verify(primaryTts).synthesize(TEXT);
        verify(secondaryTts).synthesize(TEXT);
    }

    @Test
    @DisplayName("Exhaustion of TTS candidates throws HTTP 503 (UNAVAILABLE)")
    void shouldThrowUnavailableWhenAllTtsCandidatesFail() {
        when(primaryTts.synthesize(TEXT))
                .thenThrow(new AiProviderException("ElevenLabs unavailable", ErrorType.UNAVAILABLE));
        when(secondaryTts.synthesize(TEXT))
                .thenThrow(new AiProviderException("NoOp unavailable", ErrorType.UNAVAILABLE));

        List<FallbackTextToSpeechAdapter.CandidateExecutor> candidates = List.of(
                new FallbackTextToSpeechAdapter.CandidateExecutor(
                        AiModelCandidate.of("elevenlabs", "multilingual-v2", 1, 10),
                        primaryTts
                ),
                new FallbackTextToSpeechAdapter.CandidateExecutor(
                        AiModelCandidate.of("noop", "noop", 2, 10),
                        secondaryTts
                )
        );

        fallbackAdapter = new FallbackTextToSpeechAdapter(candidates, classifier);

        assertThatThrownBy(() -> fallbackAdapter.synthesize(TEXT))
                .isInstanceOf(AiProviderException.class)
                .satisfies(ex -> assertThat(((AiProviderException) ex).getErrorType()).isEqualTo(ErrorType.UNAVAILABLE));
    }
}
