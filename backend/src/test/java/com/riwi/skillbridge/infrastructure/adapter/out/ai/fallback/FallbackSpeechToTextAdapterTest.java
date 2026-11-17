package com.riwi.skillbridge.infrastructure.adapter.out.ai.fallback;

import com.riwi.skillbridge.application.port.out.SpeechToTextPort;
import com.riwi.skillbridge.domain.exception.AiProviderException;
import com.riwi.skillbridge.domain.exception.AiProviderException.ErrorType;
import com.riwi.skillbridge.infrastructure.adapter.out.ai.candidate.AiModelCandidate;
import com.riwi.skillbridge.infrastructure.adapter.out.ai.classification.AiErrorClassifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FallbackSpeechToTextAdapterTest {

    private final AiErrorClassifier classifier = new AiErrorClassifier();
    private SpeechToTextPort primaryStt;
    private SpeechToTextPort secondaryStt;
    private FallbackSpeechToTextAdapter fallbackAdapter;

    private static final byte[] AUDIO = new byte[]{1, 2, 3};
    private static final String TYPE = "audio/wav";
    private static final String FILENAME = "audio.wav";

    @BeforeEach
    void setUp() {
        primaryStt = mock(SpeechToTextPort.class);
        secondaryStt = mock(SpeechToTextPort.class);
    }

    @Test
    @DisplayName("Scenario 2.1: Primary STT succeeds -> returns transcription without invoking secondary")
    void shouldReturnPrimaryTranscriptionWhenHealthy() {
        when(primaryStt.transcribe(AUDIO, TYPE, FILENAME)).thenReturn("Transcripción primaria");

        List<FallbackSpeechToTextAdapter.CandidateExecutor> candidates = List.of(
                new FallbackSpeechToTextAdapter.CandidateExecutor(
                        AiModelCandidate.of("groq", "whisper-large-v3-turbo", 1, 10),
                        primaryStt
                ),
                new FallbackSpeechToTextAdapter.CandidateExecutor(
                        AiModelCandidate.of("gemini", "gemini-audio", 2, 10),
                        secondaryStt
                )
        );

        fallbackAdapter = new FallbackSpeechToTextAdapter(candidates, classifier);

        String result = fallbackAdapter.transcribe(AUDIO, TYPE, FILENAME);

        assertThat(result).isEqualTo("Transcripción primaria");
        verify(primaryStt).transcribe(AUDIO, TYPE, FILENAME);
        verifyNoInteractions(secondaryStt);
    }

    @Test
    @DisplayName("Scenario 2.1: Primary STT fails with recoverable timeout -> switches to secondary transparently")
    void shouldFallbackToSecondaryOnTimeout() {
        when(primaryStt.transcribe(AUDIO, TYPE, FILENAME))
                .thenThrow(new AiProviderException("Timeout", ErrorType.TIMEOUT, new SocketTimeoutException()));
        when(secondaryStt.transcribe(AUDIO, TYPE, FILENAME)).thenReturn("Transcripción secundaria");

        List<FallbackSpeechToTextAdapter.CandidateExecutor> candidates = List.of(
                new FallbackSpeechToTextAdapter.CandidateExecutor(
                        AiModelCandidate.of("groq", "whisper-large-v3-turbo", 1, 10),
                        primaryStt
                ),
                new FallbackSpeechToTextAdapter.CandidateExecutor(
                        AiModelCandidate.of("gemini", "gemini-audio", 2, 10),
                        secondaryStt
                )
        );

        fallbackAdapter = new FallbackSpeechToTextAdapter(candidates, classifier);

        String result = fallbackAdapter.transcribe(AUDIO, TYPE, FILENAME);

        assertThat(result).isEqualTo("Transcripción secundaria");
        verify(primaryStt).transcribe(AUDIO, TYPE, FILENAME);
        verify(secondaryStt).transcribe(AUDIO, TYPE, FILENAME);
    }

    @Test
    @DisplayName("Non-recoverable input error does not fallback and propagates immediately")
    void shouldNotFallbackOnNonRecoverableError() {
        when(primaryStt.transcribe(AUDIO, TYPE, FILENAME))
                .thenThrow(new IllegalArgumentException("Audio payload too large"));

        List<FallbackSpeechToTextAdapter.CandidateExecutor> candidates = List.of(
                new FallbackSpeechToTextAdapter.CandidateExecutor(
                        AiModelCandidate.of("groq", "whisper-large-v3-turbo", 1, 10),
                        primaryStt
                ),
                new FallbackSpeechToTextAdapter.CandidateExecutor(
                        AiModelCandidate.of("gemini", "gemini-audio", 2, 10),
                        secondaryStt
                )
        );

        fallbackAdapter = new FallbackSpeechToTextAdapter(candidates, classifier);

        assertThatThrownBy(() -> fallbackAdapter.transcribe(AUDIO, TYPE, FILENAME))
                .isInstanceOf(IllegalArgumentException.class);

        verify(primaryStt).transcribe(AUDIO, TYPE, FILENAME);
        verifyNoInteractions(secondaryStt);
    }

    @Test
    @DisplayName("Exhaustion of all STT candidates throws HTTP 503 (UNAVAILABLE)")
    void shouldThrowUnavailableWhenAllCandidatesFail() {
        when(primaryStt.transcribe(AUDIO, TYPE, FILENAME))
                .thenThrow(new AiProviderException("Groq down", ErrorType.UNAVAILABLE));
        when(secondaryStt.transcribe(AUDIO, TYPE, FILENAME))
                .thenThrow(new AiProviderException("Gemini down", ErrorType.UNAVAILABLE));

        List<FallbackSpeechToTextAdapter.CandidateExecutor> candidates = List.of(
                new FallbackSpeechToTextAdapter.CandidateExecutor(
                        AiModelCandidate.of("groq", "whisper-large-v3-turbo", 1, 10),
                        primaryStt
                ),
                new FallbackSpeechToTextAdapter.CandidateExecutor(
                        AiModelCandidate.of("gemini", "gemini-audio", 2, 10),
                        secondaryStt
                )
        );

        fallbackAdapter = new FallbackSpeechToTextAdapter(candidates, classifier);

        assertThatThrownBy(() -> fallbackAdapter.transcribe(AUDIO, TYPE, FILENAME))
                .isInstanceOf(AiProviderException.class)
                .satisfies(ex -> assertThat(((AiProviderException) ex).getErrorType()).isEqualTo(ErrorType.UNAVAILABLE));
    }
}
