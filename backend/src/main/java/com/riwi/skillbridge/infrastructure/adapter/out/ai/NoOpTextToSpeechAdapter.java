package com.riwi.skillbridge.infrastructure.adapter.out.ai;

import com.riwi.skillbridge.application.port.out.TextToSpeechPort;
import com.riwi.skillbridge.domain.exception.AiProviderException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "spring.ai.tts.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpTextToSpeechAdapter implements TextToSpeechPort {

    @Override
    public AudioContent synthesize(String text) {
        throw new AiProviderException(
            "La síntesis de voz no está habilitada",
            AiProviderException.ErrorType.UNAVAILABLE);
    }
}
