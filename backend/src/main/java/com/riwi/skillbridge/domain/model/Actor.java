package com.riwi.skillbridge.domain.model;

import java.util.UUID;

public record Actor(UUID id, Role role) {
    public Actor {
        if (id == null || role == null) {
            throw new IllegalArgumentException("Actor requiere id y role");
        }
    }
}
