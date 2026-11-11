package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.application.recommendation.RecommendationRequest;
import com.riwi.skillbridge.domain.model.Offering;
import java.util.List;

public interface AiRecommendationPort {
    AiStructuredResponse recommend(RecommendationRequest request, List<Offering> offerings);
}