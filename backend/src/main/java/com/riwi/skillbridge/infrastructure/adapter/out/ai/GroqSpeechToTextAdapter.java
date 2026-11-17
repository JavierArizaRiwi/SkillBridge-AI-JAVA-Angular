package com.riwi.skillbridge.infrastructure.adapter.out.ai;

import com.riwi.skillbridge.application.port.out.SpeechToTextPort;
import com.riwi.skillbridge.domain.exception.AiProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.ai.audio-provider", havingValue = "groq")
public class GroqSpeechToTextAdapter implements SpeechToTextPort {

    private static final Logger log = LoggerFactory.getLogger(GroqSpeechToTextAdapter.class);
    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String audioModel;
    private static final String GROQ_URL = "https://api.groq.com/openai/v1/audio/transcriptions";

    public GroqSpeechToTextAdapter(
            RestTemplateBuilder restTemplateBuilder, 
            @Value("${app.ai.groq.api-key:}") String apiKey, @Value("${app.ai.groq.audio-model:whisper-large-v3-turbo}") String audioModel) {
        this.restTemplate = restTemplateBuilder.build();
        this.apiKey = apiKey;
        this.audioModel = audioModel;
    }

    @Override
    public String transcribe(byte[] audio, String type, String name) {
        if (apiKey == null || apiKey.isBlank()) {
            log.error("Groq API key no está configurada (app.ai.groq.api-key)");
            throw new AiProviderException("Groq API key is not configured", AiProviderException.ErrorType.INVALID_INPUT);
        }

        log.info("Iniciando transcripción de audio con Groq Whisper (modelo: {}, archivo: '{}', tamaño: {} bytes)...", audioModel, name, audio != null ? audio.length : 0);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setBearerAuth(apiKey);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            
            ByteArrayResource resource = new ByteArrayResource(audio) {
                @Override
                public String getFilename() {
                    return name;
                }
            };
            
            body.add("file", resource);
            body.add("model", audioModel);
            body.add("language", "es");
            body.add("response_format", "json");

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(GROQ_URL, requestEntity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String text = (String) response.getBody().get("text");
                if (text == null || text.isBlank()) {
                    log.warn("Groq devolvió una transcripción vacía");
                    throw new AiProviderException("Groq returned empty transcription", AiProviderException.ErrorType.INTERNAL_ERROR);
                }
                log.info("Transcripción exitosa con Groq Whisper: '{}'", text.trim());
                return text.trim();
            } else {
                log.error("Groq devolvió status de error: {}", response.getStatusCode());
                throw new AiProviderException("Groq returned error status: " + response.getStatusCode(), AiProviderException.ErrorType.INTERNAL_ERROR);
            }
        } catch (Exception e) {
            log.error("Fallo al transcribir audio con Groq: {}", e.getMessage());
            throw new AiProviderException("Failed to transcribe audio with Groq: " + e.getMessage(), AiProviderException.ErrorType.UNAVAILABLE, e);
        }
    }
}