package com.riwi.skillbridge.application.port.out;

import java.util.UUID;

public interface TokenPort {
    String generate(UUID userId, String email, String role);
}
