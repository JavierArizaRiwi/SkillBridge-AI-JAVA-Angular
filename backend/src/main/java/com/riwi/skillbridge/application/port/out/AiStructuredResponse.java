package com.riwi.skillbridge.application.port.out;

import java.util.List;

public record AiStructuredResponse(String explanation, List<AiItem> recommendations) {
    public record AiItem(String offeringId, double score, String reason) {}
}