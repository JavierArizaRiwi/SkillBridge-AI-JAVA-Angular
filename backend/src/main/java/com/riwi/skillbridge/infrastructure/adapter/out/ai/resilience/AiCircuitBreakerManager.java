package com.riwi.skillbridge.infrastructure.adapter.out.ai.resilience;

import com.riwi.skillbridge.infrastructure.adapter.out.ai.classification.AiErrorClassifier;
import com.riwi.skillbridge.infrastructure.config.AiFallbackProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Manages independent Resilience4j Circuit Breakers per candidate model and capability tuple
 * (e.g. "chat:gemini:gemini-3.8-flash").
 *
 * Automatically binds circuit breaker metrics to Micrometer if available, and ensures non-recoverable
 * client errors do not degrade circuit health.
 */
@Component
public class AiCircuitBreakerManager {

    private static final Logger log = LoggerFactory.getLogger(AiCircuitBreakerManager.class);

    private final CircuitBreakerRegistry registry;
    private final AiFallbackProperties properties;
    private final AiErrorClassifier errorClassifier;
    private final Map<String, CircuitBreakerKey> registeredKeys = new ConcurrentHashMap<>();

    public record CircuitBreakerKey(String capability, String provider, String model) {
        public String keyName() {
            return capability + ":" + provider + ":" + model;
        }
    }

    @Autowired
    public AiCircuitBreakerManager(
            AiFallbackProperties properties,
            AiErrorClassifier errorClassifier,
            @Autowired(required = false) MeterRegistry meterRegistry
    ) {
        this.properties = properties != null ? properties : new AiFallbackProperties();
        this.errorClassifier = errorClassifier != null ? errorClassifier : new AiErrorClassifier();
        this.registry = CircuitBreakerRegistry.ofDefaults();

        if (meterRegistry != null) {
            TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(this.registry).bindTo(meterRegistry);
            log.info("Resilience4j Circuit Breaker metrics registered to Micrometer");
        }

        initializeConfiguredBreakers();
    }

    public AiCircuitBreakerManager(AiFallbackProperties properties, AiErrorClassifier errorClassifier) {
        this(properties, errorClassifier, null);
    }

    private void initializeConfiguredBreakers() {
        registerBreakersForCapability("chat", properties.getChat());
        registerBreakersForCapability("vision", properties.getVision());
        registerBreakersForCapability("stt", properties.getStt());
        registerBreakersForCapability("tts", properties.getTts());
        registerBreakersForCapability("rag", properties.getRag());
        registerBreakersForCapability("embedding", properties.getEmbedding());
    }

    private void registerBreakersForCapability(String capability, AiFallbackProperties.CapabilityFallbackConfig config) {
        if (config == null || config.getChain() == null) {
            return;
        }
        for (AiFallbackProperties.ChainElementConfig element : config.getChain()) {
            getOrCreateCircuitBreaker(capability, element.getProvider(), element.getModel());
        }
    }

    /**
     * Resolves or dynamically registers a Circuit Breaker for the given capability, provider and model.
     */
    public CircuitBreaker getOrCreateCircuitBreaker(String capability, String provider, String model) {
        String normalizedCapability = capability != null ? capability.trim().toLowerCase() : "unknown";
        String normalizedProvider = provider != null ? provider.trim().toLowerCase() : "unknown";
        String normalizedModel = model != null ? model.trim() : "unknown";

        CircuitBreakerKey key = new CircuitBreakerKey(normalizedCapability, normalizedProvider, normalizedModel);
        String name = key.keyName();

        registeredKeys.putIfAbsent(name, key);

        return registry.circuitBreaker(name, () -> createConfigForCapability(normalizedCapability));
    }

    private CircuitBreakerConfig createConfigForCapability(String capability) {
        AiFallbackProperties.CapabilityFallbackConfig capConfig = properties.getCapability(capability);
        AiFallbackProperties.CircuitBreakerProperties cbProps = capConfig != null && capConfig.getCircuitBreaker() != null
                ? capConfig.getCircuitBreaker()
                : new AiFallbackProperties.CircuitBreakerProperties();

        return CircuitBreakerConfig.custom()
                .failureRateThreshold(cbProps.getFailureRateThreshold())
                .slidingWindowSize(cbProps.getSlidingWindowSize())
                .waitDurationInOpenState(Duration.ofSeconds(cbProps.getWaitDurationInOpenStateSeconds()))
                .permittedNumberOfCallsInHalfOpenState(cbProps.getPermittedNumberOfCallsInHalfOpenState())
                .minimumNumberOfCalls(cbProps.getMinimumNumberOfCalls())
                .recordException(throwable -> {
                    // Only recoverable technical failures penalize the circuit
                    return errorClassifier.classify(throwable).isRecoverable();
                })
                .build();
    }

    /**
     * Attempts to acquire permission from the candidate's circuit breaker.
     * Returns true if allowed, false if the circuit is OPEN (to skip instantly in < 5ms).
     */
    public boolean tryAcquirePermission(String capability, String provider, String model) {
        CircuitBreaker cb = getOrCreateCircuitBreaker(capability, provider, model);
        return cb.tryAcquirePermission();
    }

    public void onSuccess(String capability, String provider, String model, long durationMs) {
        CircuitBreaker cb = getOrCreateCircuitBreaker(capability, provider, model);
        cb.onSuccess(durationMs, TimeUnit.MILLISECONDS);
    }

    public void onError(String capability, String provider, String model, long durationMs, Throwable throwable) {
        CircuitBreaker cb = getOrCreateCircuitBreaker(capability, provider, model);
        cb.onError(durationMs, TimeUnit.MILLISECONDS, throwable);
    }

    /**
     * Returns current snapshot of all registered circuit breakers for admin inspection and health checks.
     */
    public List<ProviderCircuitStatus> getAllStatuses() {
        List<ProviderCircuitStatus> statuses = new ArrayList<>();
        for (Map.Entry<String, CircuitBreakerKey> entry : registeredKeys.entrySet()) {
            CircuitBreakerKey key = entry.getValue();
            CircuitBreaker cb = registry.circuitBreaker(entry.getKey());
            CircuitBreaker.Metrics metrics = cb.getMetrics();

            statuses.add(new ProviderCircuitStatus(
                    key.capability(),
                    key.provider(),
                    key.model(),
                    cb.getState().name(),
                    metrics.getFailureRate(),
                    metrics.getNumberOfBufferedCalls(),
                    metrics.getNumberOfFailedCalls(),
                    metrics.getNumberOfNotPermittedCalls()
            ));
        }
        return statuses;
    }

    public CircuitBreakerRegistry getRegistry() {
        return registry;
    }
}
