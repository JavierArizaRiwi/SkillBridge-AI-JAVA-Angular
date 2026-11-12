package com.riwi.skillbridge.application.recommendation;

import java.util.UUID;

public record RecommendationRequest(InputType inputType, String text, String context, UUID userId) {
    public RecommendationRequest {
        if (inputType == null) throw new IllegalArgumentException("inputType es obligatorio");
        if ((text == null || text.isBlank()) && (context == null || context.isBlank())) {
            throw new IllegalArgumentException("Se requiere texto o contexto para recomendar");
        }
    }

    public String normalizedNeed() {
        return text != null && !text.isBlank() ? text.trim() : context.trim();
    }
}
