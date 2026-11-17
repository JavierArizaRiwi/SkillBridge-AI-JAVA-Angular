package com.riwi.skillbridge.infrastructure.adapter.out.ai;

import com.riwi.skillbridge.application.port.out.AiRecommendationPort;
import com.riwi.skillbridge.application.port.out.AiStructuredResponse;
import com.riwi.skillbridge.application.recommendation.RecommendationRequest;
import com.riwi.skillbridge.domain.model.Offering;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "mock", matchIfMissing = false)
public class MockAiAdapter implements AiRecommendationPort {

    @Override
    public AiStructuredResponse recommend(RecommendationRequest request, List<Offering> offerings) {
        String explanation = "Mock explanation for: " + request.normalizedNeed();
        List<AiStructuredResponse.AiItem> items = offerings.stream()
            .limit(3)
            .map(o -> new AiStructuredResponse.AiItem(o.id().toString(), 0.95, "Mock reason"))
            .toList();
        return new AiStructuredResponse(explanation, items);
    }
}
