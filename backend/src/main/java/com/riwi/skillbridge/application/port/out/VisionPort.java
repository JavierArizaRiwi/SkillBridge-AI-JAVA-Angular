package com.riwi.skillbridge.application.port.out;

public interface VisionPort {
    String extractContext(byte[] image, String contentType, String fileName);
}
