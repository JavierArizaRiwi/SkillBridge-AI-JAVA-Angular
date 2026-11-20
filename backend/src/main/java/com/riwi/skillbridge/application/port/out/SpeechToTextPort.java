package com.riwi.skillbridge.application.port.out;

public interface SpeechToTextPort {
    String transcribe(byte[] audio, String contentType, String fileName);
}
