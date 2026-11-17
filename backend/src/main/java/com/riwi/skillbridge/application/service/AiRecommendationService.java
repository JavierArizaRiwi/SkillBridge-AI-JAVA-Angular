package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.common.CorrelationIdHolder;
import com.riwi.skillbridge.application.port.in.GenerateRecommendationUseCase;
import com.riwi.skillbridge.application.port.out.AiRecommendationPort;
import com.riwi.skillbridge.application.port.out.AiStructuredResponse;
import com.riwi.skillbridge.application.port.out.KnowledgeCandidate;
import com.riwi.skillbridge.application.port.out.KnowledgeSearchPort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.port.out.event.AuditEventPublisherPort;
import com.riwi.skillbridge.application.port.out.event.BusinessEvent;
import com.riwi.skillbridge.application.port.out.event.RecommendationGeneratedPayload;
import com.riwi.skillbridge.application.recommendation.Recommendation;
import com.riwi.skillbridge.application.recommendation.RecommendationRequest;
import com.riwi.skillbridge.application.recommendation.RecommendationResult;
import com.riwi.skillbridge.domain.exception.AiProviderException;
import com.riwi.skillbridge.domain.knowledge.KnowledgeChunk;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.RecommendationInputType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AiRecommendationService implements GenerateRecommendationUseCase {

    private static final Logger log = LoggerFactory.getLogger(AiRecommendationService.class);

    private final AiRecommendationPort ai;
    private final OfferingRepositoryPort offerings;
    private final AuditEventPublisherPort events;
    private final KnowledgeSearchPort knowledgeSearch;
    private final int shortlistSize;

    /** Sin preselección: el LLM recibe todas las ofertas activas (comportamiento anterior). */
    public AiRecommendationService(AiRecommendationPort ai, OfferingRepositoryPort offerings, AuditEventPublisherPort events) {
        this(ai, offerings, events, null, 0);
    }

    /**
     * Con preselección RAG (HU-26): si el catálogo activo supera {@code shortlistSize}, solo las N ofertas más cercanas
     * a la necesidad del usuario llegan al LLM. Con {@code shortlistSize <= 0} o sin búsqueda, se envía todo el catálogo.
     */
    @Autowired
    public AiRecommendationService(AiRecommendationPort ai, OfferingRepositoryPort offerings, AuditEventPublisherPort events,
                                   @Autowired(required = false) KnowledgeSearchPort knowledgeSearch,
                                   @Value("${app.rag.recommendation-shortlist-size:15}") int shortlistSize) {
        this.ai = ai;
        this.offerings = offerings;
        this.events = events;
        this.knowledgeSearch = knowledgeSearch;
        this.shortlistSize = shortlistSize;
    }

    /**
     * Reduce el catálogo a las ofertas más relevantes. Si la búsqueda no aplica o falla, devuelve el catálogo completo:
     * recomendar con todo el catálogo es un resultado válido; la preselección es solo una optimización (nunca inventa datos).
     */
    private List<Offering> shortlist(RecommendationRequest request, List<Offering> active) {
        if (knowledgeSearch == null || shortlistSize <= 0 || active.size() <= shortlistSize) {
            return active;
        }
        try {
            List<KnowledgeCandidate> hits = knowledgeSearch.searchByType(
                    request.normalizedNeed(), shortlistSize, KnowledgeChunk.SourceType.OFFERING);
            Map<String, Offering> byChunkId = active.stream()
                    .collect(Collectors.toMap(o -> "offering:" + o.id(), Function.identity(), (a, b) -> a));
            Set<Offering> picked = new LinkedHashSet<>();
            for (KnowledgeCandidate hit : hits) {
                Offering match = byChunkId.get(hit.chunk().sourceId());
                if (match != null) {
                    picked.add(match);
                }
            }
            if (picked.isEmpty()) {
                log.warn("Preselección RAG sin coincidencias; se envía el catálogo completo ({} ofertas)", active.size());
                return active;
            }
            log.info("Preselección RAG: {} de {} ofertas activas -> {}", picked.size(), active.size(),
                    picked.stream().map(Offering::title).collect(Collectors.joining(" | ")));
            return new ArrayList<>(picked);
        } catch (AiProviderException e) {
            log.warn("Preselección RAG no disponible (tipo={}); se envía el catálogo completo", e.getErrorType());
            return active;
        }
    }

    @Override
    public RecommendationResult recommend(RecommendationRequest request) {
        UUID id = UUID.randomUUID();
        String correlation = CorrelationIdHolder.get() == null ? UUID.randomUUID().toString() : CorrelationIdHolder.get();

        events.publish(new BusinessEvent<>(
                UUID.randomUUID(),
                "RecommendationRequested",
                id.toString(),
                "Recommendation",
                Instant.now(),
                correlation,
                1,
                request,
                String.valueOf(request.userId()),
                null,
                null,
                "REQUEST",
                "RECOMMENDATION",
                id.toString()
        ));

        List<Offering> active = offerings.findAllActive();

        long startNanos = System.currentTimeMillis();
        AiStructuredResponse aiResponse = ai.recommend(request, shortlist(request, active));
        long latencyMs = System.currentTimeMillis() - startNanos;

        String provider = aiResponse.resolvedByProvider() != null ? aiResponse.resolvedByProvider() : "unknown";
        String model = aiResponse.resolvedByModel() != null ? aiResponse.resolvedByModel() : "unknown";

        log.info("AI recommendation completed recommendationId={} provider={} model={} latencyMs={} correlationId={}",
                id, provider, model, latencyMs, correlation);

        Map<UUID, String> titles = active.stream()
                .collect(Collectors.toMap(Offering::id, Offering::title, (a, b) -> a));

        List<Recommendation> selected = aiResponse.recommendations().stream()
                .map(item -> {
                    try {
                        UUID offeringId = UUID.fromString(item.offeringId());
                        String title = titles.get(offeringId);
                        if (title == null) {
                            return null; // La IA citó una oferta que no existe o no está activa: se descarta
                        }
                        return new Recommendation(offeringId, title, item.score(), item.reason());
                    } catch (IllegalArgumentException e) {
                        return null; // Ignore if the AI hallucinated an invalid UUID
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        RecommendationResult result = new RecommendationResult(id, aiResponse.explanation(), request.inputType(), request.normalizedNeed(), selected);

        RecommendationInputType domainInputType = request.inputType() != null
                ? RecommendationInputType.valueOf(request.inputType().name())
                : RecommendationInputType.TEXT;

        RecommendationGeneratedPayload payload = new RecommendationGeneratedPayload(
                id,
                request.userId(),
                request.normalizedNeed(),
                domainInputType,
                model,
                latencyMs,
                provider,
                selected.stream().map(Recommendation::offeringId).distinct().toList()
        );

        events.publish(new BusinessEvent<>(
                UUID.randomUUID(),
                "RecommendationGenerated",
                id.toString(),
                "Recommendation",
                Instant.now(),
                correlation,
                2,
                payload,
                String.valueOf(request.userId()),
                null,
                null,
                "GENERATE",
                "RECOMMENDATION",
                id.toString()
        ));

        return result;
    }
}
