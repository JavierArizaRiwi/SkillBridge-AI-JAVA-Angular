package com.riwi.skillbridge.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates AI fallback configurations strictly at startup (fail-fast principle).
 * Prevents application startup if invalid configurations, empty chains, non-positive timeouts,
 * or unknown providers are detected.
 */
@Component
public class AiFallbackValidator {

    private static final Logger log = LoggerFactory.getLogger(AiFallbackValidator.class);

    private static final Set<String> SUPPORTED_PROVIDERS = Set.of(
            "gemini",
            "openai",
            "groq",
            "elevenlabs",
            "noop"
    );

    /**
     * Validates all capabilities in the fallback properties.
     *
     * @param properties configuration properties to validate
     * @throws IllegalStateException if any validation rule is violated
     */
    public void validate(AiFallbackProperties properties) {
        if (properties == null) {
            throw new IllegalStateException("AI Fallback configuration properties must not be null");
        }

        Map<String, AiFallbackProperties.CapabilityFallbackConfig> capabilities = Map.of(
                "chat", properties.getChat(),
                "vision", properties.getVision(),
                "stt", properties.getStt(),
                "tts", properties.getTts(),
                "rag", properties.getRag(),
                "embedding", properties.getEmbedding()
        );

        for (Map.Entry<String, AiFallbackProperties.CapabilityFallbackConfig> entry : capabilities.entrySet()) {
            validateCapability(entry.getKey(), entry.getValue());
        }

        log.info("AI Fallback configuration validated successfully across {} capabilities", capabilities.size());
    }

    private void validateCapability(String capabilityName, AiFallbackProperties.CapabilityFallbackConfig config) {
        if (config == null || config.getChain() == null || config.getChain().isEmpty()) {
            String errorMsg = String.format("AI fallback configuration error: capability [%s] chain is empty. At least one provider candidate is required.", capabilityName);
            log.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        List<AiFallbackProperties.ChainElementConfig> chain = config.getChain();
        boolean hasEnabled = chain.stream().anyMatch(AiFallbackProperties.ChainElementConfig::isEnabled);
        if (!hasEnabled) {
            String errorMsg = String.format("AI fallback configuration error: capability [%s] has no enabled candidates in chain.", capabilityName);
            log.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        for (int i = 0; i < chain.size(); i++) {
            AiFallbackProperties.ChainElementConfig element = chain.get(i);
            validateElement(capabilityName, i, element);
        }
    }

    private void validateElement(String capabilityName, int index, AiFallbackProperties.ChainElementConfig element) {
        if (element == null) {
            throw new IllegalStateException(String.format("AI fallback element at index %d in capability [%s] is null", index, capabilityName));
        }

        if (element.getProvider() == null || element.getProvider().isBlank()) {
            String errorMsg = String.format("AI fallback candidate provider name must not be blank in capability [%s] at index %d", capabilityName, index);
            log.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        String normalizedProvider = element.getProvider().trim().toLowerCase();
        if (!SUPPORTED_PROVIDERS.contains(normalizedProvider)) {
            String errorMsg = String.format("Unsupported AI provider [%s] configured for capability [%s] at index %d. Supported providers: %s",
                    element.getProvider(), capabilityName, index, SUPPORTED_PROVIDERS);
            log.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        if (element.getModel() == null || element.getModel().isBlank()) {
            String errorMsg = String.format("AI fallback candidate model identifier must not be blank for provider [%s] in capability [%s] at index %d",
                    element.getProvider(), capabilityName, index);
            log.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        if (element.getTimeoutSeconds() <= 0) {
            String errorMsg = String.format("AI fallback candidate [%s:%s] in capability [%s] has invalid timeout (%d seconds) <= 0. Timeout must be a positive integer.",
                    element.getProvider(), element.getModel(), capabilityName, element.getTimeoutSeconds());
            log.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }
    }
}
