package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.SynthesizeSpeechUseCase;
import com.riwi.skillbridge.application.port.out.TextToSpeechPort;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TextToSpeechService implements SynthesizeSpeechUseCase {

    private final TextToSpeechPort port;
    private final int maxChars;

    public TextToSpeechService(TextToSpeechPort port,
                               @Value("${app.ai.tts.max-chars:2000}") int maxChars) {
        this.port = port;
        this.maxChars = maxChars;
    }

    @Override
    public TextToSpeechPort.AudioContent synthesize(String text) {
        if (text == null || text.isBlank()) {
            throw new BusinessRuleException("El texto no puede estar vacío");
        }
        String clean = text.replaceAll("[*_`#>]", "").trim(); // quita markdown
        if (clean.length() > maxChars) {
            throw new BusinessRuleException("El texto excede " + maxChars + " caracteres");
        }
        return port.synthesize(clean);
    }
}
