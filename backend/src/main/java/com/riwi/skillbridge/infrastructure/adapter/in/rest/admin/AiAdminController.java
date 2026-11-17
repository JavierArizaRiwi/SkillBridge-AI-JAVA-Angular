package com.riwi.skillbridge.infrastructure.adapter.in.rest.admin;

import com.riwi.skillbridge.infrastructure.adapter.in.rest.admin.dto.AiProvidersStatusResponse;
import com.riwi.skillbridge.infrastructure.adapter.out.ai.resilience.AiCircuitBreakerManager;
import com.riwi.skillbridge.infrastructure.config.AiFallbackProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Administrative controller for AI provider fallback chains and resilience inspection.
 * Restricted exclusively to users with ROLE_ADMIN.
 */
@RestController
@RequestMapping("/api/admin/ai/providers")
public class AiAdminController {

    private final AiFallbackProperties properties;
    private final AiCircuitBreakerManager circuitBreakerManager;

    public AiAdminController(AiFallbackProperties properties, AiCircuitBreakerManager circuitBreakerManager) {
        this.properties = properties;
        this.circuitBreakerManager = circuitBreakerManager;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AiProvidersStatusResponse> getProvidersStatus() {
        Map<String, AiFallbackProperties.CapabilityFallbackConfig> capabilityMap = new LinkedHashMap<>();
        capabilityMap.put("chat", properties.getChat());
        capabilityMap.put("vision", properties.getVision());
        capabilityMap.put("stt", properties.getStt());
        capabilityMap.put("tts", properties.getTts());
        capabilityMap.put("rag", properties.getRag());
        capabilityMap.put("embedding", properties.getEmbedding());

        Map<String, AiProvidersStatusResponse.CapabilityStatus> resultCapabilities = new LinkedHashMap<>();

        for (Map.Entry<String, AiFallbackProperties.CapabilityFallbackConfig> entry : capabilityMap.entrySet()) {
            String capability = entry.getKey();
            AiFallbackProperties.CapabilityFallbackConfig config = entry.getValue();

            List<AiProvidersStatusResponse.CandidateStatus> candidateStatuses = new ArrayList<>();
            if (config != null && config.getChain() != null) {
                for (AiFallbackProperties.ChainElementConfig element : config.getChain()) {
                    CircuitBreaker cb = circuitBreakerManager.getOrCreateCircuitBreaker(
                            capability,
                            element.getProvider(),
                            element.getModel()
                    );
                    CircuitBreaker.Metrics metrics = cb.getMetrics();

                    candidateStatuses.add(new AiProvidersStatusResponse.CandidateStatus(
                            element.getProvider(),
                            element.getModel(),
                            element.isEnabled(),
                            element.getTimeoutSeconds(),
                            cb.getState().name(),
                            metrics.getFailureRate(),
                            metrics.getNumberOfBufferedCalls(),
                            metrics.getNumberOfFailedCalls()
                    ));
                }
            }
            resultCapabilities.put(capability, new AiProvidersStatusResponse.CapabilityStatus(capability, candidateStatuses));
        }

        AiProvidersStatusResponse response = new AiProvidersStatusResponse(
                Instant.now().toString(),
                resultCapabilities
        );

        return ResponseEntity.ok(response);
    }
}
