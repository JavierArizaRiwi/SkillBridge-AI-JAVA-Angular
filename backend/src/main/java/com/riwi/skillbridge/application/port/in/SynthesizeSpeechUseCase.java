package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.application.port.out.TextToSpeechPort;

public interface SynthesizeSpeechUseCase {
    TextToSpeechPort.AudioContent synthesize(String text);
}
