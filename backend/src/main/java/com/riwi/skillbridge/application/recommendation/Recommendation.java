package com.riwi.skillbridge.application.recommendation;

import java.util.UUID;

public record Recommendation(UUID offeringId, double score, String reason) {
    public Recommendation {
        if (offeringId == null) throw new IllegalArgumentException("offeringId es obligatorio");
        if (score < 0.0 || score > 1.0) throw new IllegalArgumentException("score debe estar entre 0.0 y 1.0");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason es obligatorio");
    }
}
