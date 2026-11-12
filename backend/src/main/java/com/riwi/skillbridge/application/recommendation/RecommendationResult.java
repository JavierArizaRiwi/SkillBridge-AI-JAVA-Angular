package com.riwi.skillbridge.application.recommendation;

import java.util.List;
import java.util.UUID;

public record RecommendationResult(UUID recommendationId, String explanation, InputType inputType, String sourceText,
                                   List<Recommendation> recommendations) {
    public RecommendationResult {
        if (recommendationId == null) throw new IllegalArgumentException("recommendationId es obligatorio");
        if (explanation == null || explanation.isBlank()) throw new IllegalArgumentException("explanation es obligatoria");
        if (sourceText == null || sourceText.isBlank()) throw new IllegalArgumentException("sourceText es obligatorio");
        if (inputType == null) throw new IllegalArgumentException("inputType es obligatorio");
        recommendations = List.copyOf(recommendations == null ? List.of() : recommendations);
    }
}
