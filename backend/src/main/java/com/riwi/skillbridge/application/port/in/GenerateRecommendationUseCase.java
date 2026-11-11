package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.application.recommendation.RecommendationRequest;
import com.riwi.skillbridge.application.recommendation.RecommendationResult;

public interface GenerateRecommendationUseCase {
    RecommendationResult recommend(RecommendationRequest request);
}
