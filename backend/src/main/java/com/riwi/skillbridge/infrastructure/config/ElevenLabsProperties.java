package com.riwi.skillbridge.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.ai.tts.elevenlabs")
public record ElevenLabsProperties(String apiKey, String voiceId, String modelId) {}
