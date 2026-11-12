package com.riwi.skillbridge.application.port.out.event;
import com.riwi.skillbridge.application.recommendation.InputType;
import java.util.UUID;
public record RecommendationRequestedPayload(UUID recommendationId, UUID userId, InputType inputType) {}
