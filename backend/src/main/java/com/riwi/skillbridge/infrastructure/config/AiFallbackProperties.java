package com.riwi.skillbridge.infrastructure.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Hierarchical typed configuration for AI provider fallback chains under prefix {@code app.ai.fallback}.
 * Supports fine-grained configuration per capability, dual environment variable binding
 * (compact syntax and standard Spring relaxed binding), and backward compatibility.
 */
@Component
@ConfigurationProperties(prefix = "app.ai.fallback")
public class AiFallbackProperties implements EnvironmentAware {

    private Environment environment;

    private CapabilityFallbackConfig chat = new CapabilityFallbackConfig();
    private CapabilityFallbackConfig vision = new CapabilityFallbackConfig();
    private CapabilityFallbackConfig stt = new CapabilityFallbackConfig();
    private CapabilityFallbackConfig tts = new CapabilityFallbackConfig();
    private CapabilityFallbackConfig rag = new CapabilityFallbackConfig();
    private CapabilityFallbackConfig embedding = new CapabilityFallbackConfig();

    public AiFallbackProperties() {
        initDefaults();
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void initFromEnvironment() {
        if (environment == null) {
            return;
        }

        // 1. Process compact environment variables (highest precedence)
        applyCompactEnvIfPresent("AI_CHAT_FALLBACK_CHAIN", chat);
        applyCompactEnvIfPresent("AI_VISION_FALLBACK_CHAIN", vision);
        applyCompactEnvIfPresent("AI_STT_FALLBACK_CHAIN", stt);
        applyCompactEnvIfPresent("AI_TTS_FALLBACK_CHAIN", tts);
        applyCompactEnvIfPresent("AI_RAG_FALLBACK_CHAIN", rag);
        applyCompactEnvIfPresent("AI_EMBEDDING_FALLBACK_CHAIN", embedding);

        // 2. Backward compatibility with AI_PROVIDER and AI_AUDIO_PROVIDER
        String legacyProvider = environment.getProperty("AI_PROVIDER", environment.getProperty("app.ai.provider", "gemini"));
        String legacyAudioProvider = environment.getProperty("AI_AUDIO_PROVIDER", environment.getProperty("app.ai.audio-provider", "gemini"));
        int legacyTimeout = 10;
        try {
            String timeoutStr = environment.getProperty("AI_TIMEOUT_SECONDS", environment.getProperty("app.ai.timeout-seconds", "10"));
            if (timeoutStr != null && !timeoutStr.isBlank()) {
                legacyTimeout = Integer.parseInt(timeoutStr.trim());
            }
        } catch (NumberFormatException ignored) {
        }

        applyLegacyCompatibility(legacyProvider, legacyAudioProvider, legacyTimeout);
    }

    private void applyCompactEnvIfPresent(String envKey, CapabilityFallbackConfig capability) {
        String compact = environment.getProperty(envKey);
        if (compact != null && !compact.isBlank()) {
            List<ChainElementConfig> parsed = parseCompactChain(compact, 10);
            if (!parsed.isEmpty()) {
                capability.setChain(parsed);
            }
        }
    }

    private void initDefaults() {
        chat.getChain().add(new ChainElementConfig("gemini", "gemini-3.8-flash", true, 10));
        chat.getChain().add(new ChainElementConfig("openai", "gpt-4o-mini", true, 10));

        vision.getChain().add(new ChainElementConfig("gemini", "gemini-3.8-flash", true, 10));

        stt.getChain().add(new ChainElementConfig("groq", "whisper-large-v3-turbo", true, 10));
        stt.getChain().add(new ChainElementConfig("gemini", "gemini-audio", true, 10));

        tts.getChain().add(new ChainElementConfig("elevenlabs", "multilingual-v2", true, 10));
        tts.getChain().add(new ChainElementConfig("noop", "noop", true, 10));

        rag.getChain().add(new ChainElementConfig("gemini", "gemini-3.8-flash", true, 10));
        rag.getChain().add(new ChainElementConfig("openai", "gpt-4o-mini", true, 10));

        embedding.getChain().add(new ChainElementConfig("gemini", "text-embedding-004", true, 10));
        embedding.getChain().add(new ChainElementConfig("openai", "text-embedding-3-small", true, 10));
    }

    /**
     * Parses a compact chain string (e.g. "gemini:gemini-3.8-flash,openai:gpt-4o-mini")
     * tolerating redundant whitespace and normalizing delimiters.
     *
     * @param compactChain the compact chain string
     * @param defaultTimeoutSeconds default timeout for elements in seconds
     * @return parsed list of chain elements
     */
    public static List<ChainElementConfig> parseCompactChain(String compactChain, int defaultTimeoutSeconds) {
        List<ChainElementConfig> result = new ArrayList<>();
        if (compactChain == null || compactChain.isBlank()) {
            return result;
        }

        int timeout = defaultTimeoutSeconds > 0 ? defaultTimeoutSeconds : 10;
        String[] tokens = compactChain.split(",");
        for (String token : tokens) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int colonIndex = trimmed.indexOf(':');
            if (colonIndex <= 0 || colonIndex == trimmed.length() - 1) {
                throw new IllegalArgumentException("Invalid compact chain element format: '" + trimmed + "'. Expected 'provider:model'");
            }
            String provider = trimmed.substring(0, colonIndex).trim().toLowerCase();
            String model = trimmed.substring(colonIndex + 1).trim();
            result.add(new ChainElementConfig(provider, model, true, timeout));
        }
        return result;
    }

    /**
     * Applies legacy fallback compatibility if advanced chain is empty.
     */
    public void applyLegacyCompatibility(String legacyProvider, String legacyAudioProvider, int legacyTimeoutSeconds) {
        int timeout = legacyTimeoutSeconds > 0 ? legacyTimeoutSeconds : 10;
        String primary = (legacyProvider != null && !legacyProvider.isBlank()) ? legacyProvider.trim().toLowerCase() : "gemini";
        String audioPrimary = (legacyAudioProvider != null && !legacyAudioProvider.isBlank()) ? legacyAudioProvider.trim().toLowerCase() : "gemini";

        if (chat.getChain().isEmpty()) {
            String model = "gemini".equals(primary) ? "gemini-3.8-flash" : "gpt-4o-mini";
            chat.getChain().add(new ChainElementConfig(primary, model, true, timeout));
        }

        if (stt.getChain().isEmpty()) {
            String model = "groq".equals(audioPrimary) ? "whisper-large-v3-turbo" : "gemini-audio";
            stt.getChain().add(new ChainElementConfig(audioPrimary, model, true, timeout));
        }
    }

    public CapabilityFallbackConfig getChat() {
        return chat;
    }

    public void setChat(CapabilityFallbackConfig chat) {
        this.chat = chat;
    }

    public CapabilityFallbackConfig getVision() {
        return vision;
    }

    public void setVision(CapabilityFallbackConfig vision) {
        this.vision = vision;
    }

    public CapabilityFallbackConfig getStt() {
        return stt;
    }

    public void setStt(CapabilityFallbackConfig stt) {
        this.stt = stt;
    }

    public CapabilityFallbackConfig getTts() {
        return tts;
    }

    public void setTts(CapabilityFallbackConfig tts) {
        this.tts = tts;
    }

    public CapabilityFallbackConfig getRag() {
        return rag;
    }

    public void setRag(CapabilityFallbackConfig rag) {
        this.rag = rag;
    }

    public CapabilityFallbackConfig getEmbedding() {
        return embedding;
    }

    public void setEmbedding(CapabilityFallbackConfig embedding) {
        this.embedding = embedding;
    }

    public CapabilityFallbackConfig getCapability(String capabilityName) {
        if (capabilityName == null) return null;
        return switch (capabilityName.toLowerCase().trim()) {
            case "chat", "recommendation" -> chat;
            case "vision" -> vision;
            case "stt", "speech-to-text", "speech_to_text" -> stt;
            case "tts", "text-to-speech", "text_to_speech" -> tts;
            case "rag" -> rag;
            case "embedding" -> embedding;
            default -> null;
        };
    }

    // --- Nested Configuration Classes ---

    public static class CapabilityFallbackConfig {
        private List<ChainElementConfig> chain = new ArrayList<>();
        private CircuitBreakerProperties circuitBreaker = new CircuitBreakerProperties();
        private RetryProperties retry = new RetryProperties();

        public List<ChainElementConfig> getChain() {
            return chain;
        }

        public void setChain(List<ChainElementConfig> chain) {
            this.chain = chain != null ? chain : new ArrayList<>();
        }

        public CircuitBreakerProperties getCircuitBreaker() {
            return circuitBreaker;
        }

        public void setCircuitBreaker(CircuitBreakerProperties circuitBreaker) {
            this.circuitBreaker = circuitBreaker != null ? circuitBreaker : new CircuitBreakerProperties();
        }

        public RetryProperties getRetry() {
            return retry;
        }

        public void setRetry(RetryProperties retry) {
            this.retry = retry != null ? retry : new RetryProperties();
        }
    }

    public static class ChainElementConfig {
        private String provider;
        private String model;
        private boolean enabled = true;
        private int timeoutSeconds = 10;

        public ChainElementConfig() {
        }

        public ChainElementConfig(String provider, String model, boolean enabled, int timeoutSeconds) {
            this.provider = provider;
            this.model = model;
            this.enabled = enabled;
            this.timeoutSeconds = timeoutSeconds;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ChainElementConfig that = (ChainElementConfig) o;
            return enabled == that.enabled && timeoutSeconds == that.timeoutSeconds &&
                    Objects.equals(provider, that.provider) && Objects.equals(model, that.model);
        }

        @Override
        public int hashCode() {
            return Objects.hash(provider, model, enabled, timeoutSeconds);
        }

        @Override
        public String toString() {
            return "ChainElementConfig{" +
                    "provider='" + provider + '\'' +
                    ", model='" + model + '\'' +
                    ", enabled=" + enabled +
                    ", timeoutSeconds=" + timeoutSeconds +
                    '}';
        }
    }

    public static class CircuitBreakerProperties {
        private float failureRateThreshold = 50.0f;
        private int slidingWindowSize = 10;
        private int waitDurationInOpenStateSeconds = 30;
        private int permittedNumberOfCallsInHalfOpenState = 3;
        private int minimumNumberOfCalls = 5;

        public float getFailureRateThreshold() {
            return failureRateThreshold;
        }

        public void setFailureRateThreshold(float failureRateThreshold) {
            this.failureRateThreshold = failureRateThreshold;
        }

        public int getSlidingWindowSize() {
            return slidingWindowSize;
        }

        public void setSlidingWindowSize(int slidingWindowSize) {
            this.slidingWindowSize = slidingWindowSize;
        }

        public int getWaitDurationInOpenStateSeconds() {
            return waitDurationInOpenStateSeconds;
        }

        public void setWaitDurationInOpenStateSeconds(int waitDurationInOpenStateSeconds) {
            this.waitDurationInOpenStateSeconds = waitDurationInOpenStateSeconds;
        }

        public int getPermittedNumberOfCallsInHalfOpenState() {
            return permittedNumberOfCallsInHalfOpenState;
        }

        public void setPermittedNumberOfCallsInHalfOpenState(int permittedNumberOfCallsInHalfOpenState) {
            this.permittedNumberOfCallsInHalfOpenState = permittedNumberOfCallsInHalfOpenState;
        }

        public int getMinimumNumberOfCalls() {
            return minimumNumberOfCalls;
        }

        public void setMinimumNumberOfCalls(int minimumNumberOfCalls) {
            this.minimumNumberOfCalls = minimumNumberOfCalls;
        }
    }

    public static class RetryProperties {
        private int maxAttempts = 3;
        private long initialIntervalMs = 500;
        private double multiplier = 1.5;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public long getInitialIntervalMs() {
            return initialIntervalMs;
        }

        public void setInitialIntervalMs(long initialIntervalMs) {
            this.initialIntervalMs = initialIntervalMs;
        }

        public double getMultiplier() {
            return multiplier;
        }

        public void setMultiplier(double multiplier) {
            this.multiplier = multiplier;
        }
    }
}
