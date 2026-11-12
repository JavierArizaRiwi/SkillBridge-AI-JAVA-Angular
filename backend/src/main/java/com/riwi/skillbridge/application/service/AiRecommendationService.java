package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.GenerateRecommendationUseCase;
import com.riwi.skillbridge.application.port.out.AiRecommendationPort;
import com.riwi.skillbridge.application.port.out.AiStructuredResponse;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.recommendation.Recommendation;
import com.riwi.skillbridge.application.recommendation.RecommendationRequest;
import com.riwi.skillbridge.application.recommendation.RecommendationResult;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.application.port.out.event.AuditEventPublisherPort;
import com.riwi.skillbridge.application.port.out.event.BusinessEvent;
import com.riwi.skillbridge.application.common.CorrelationIdHolder;
import java.time.Instant;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AiRecommendationService implements GenerateRecommendationUseCase {
    private final AiRecommendationPort ai;
    private final OfferingRepositoryPort offerings; 
    private final AuditEventPublisherPort events;
    
    public AiRecommendationService(AiRecommendationPort ai, OfferingRepositoryPort offerings, AuditEventPublisherPort events) { 
        this.ai = ai; 
        this.offerings = offerings; 
        this.events = events; 
    }

    @Override
    public RecommendationResult recommend(RecommendationRequest request) {
        UUID id = UUID.randomUUID(); 
        String correlation = CorrelationIdHolder.get() == null ? UUID.randomUUID().toString() : CorrelationIdHolder.get();
        events.publish(new BusinessEvent<>(UUID.randomUUID(), "RecommendationRequested", id.toString(), "Recommendation", Instant.now(), correlation, 1, request.inputType(), String.valueOf(request.userId()), null, null, "REQUEST", "RECOMMENDATION", id.toString()));
        
        List<Offering> active = offerings.findAllActive();
        
        AiStructuredResponse aiResponse = ai.recommend(request, active);
        
        List<Recommendation> selected = aiResponse.recommendations().stream()
            .map(item -> {
                try {
                    return new Recommendation(UUID.fromString(item.offeringId()), item.score(), item.reason());
                } catch (IllegalArgumentException e) {
                    return null; // Ignore if the AI hallucinated an invalid UUID
                }
            })
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toList());
            
        RecommendationResult result = new RecommendationResult(id, aiResponse.explanation(), request.inputType(), request.normalizedNeed(), selected);
        events.publish(new BusinessEvent<>(UUID.randomUUID(), "RecommendationGenerated", id.toString(), "Recommendation", Instant.now(), correlation, 1, request.inputType(), String.valueOf(request.userId()), null, null, "GENERATE", "RECOMMENDATION", id.toString()));
        return result;
    }
}