package com.riwi.skillbridge.infrastructure.adapter.out.ai;

import com.riwi.skillbridge.application.port.out.SpeechToTextPort;
import com.riwi.skillbridge.domain.exception.AiProviderException;
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

    private final RestTemplate restTemplate;
    private final String apiKey;
    private static final String GROQ_URL = "https://api.groq.com/openai/v1/audio/transcriptions";

    public GroqSpeechToTextAdapter(
            RestTemplateBuilder restTemplateBuilder, 
            @Value("${app.ai.groq.api-key:}") String apiKey) {
        this.restTemplate = restTemplateBuilder.build();
        this.apiKey = apiKey;
    }

    @Override
    public String transcribe(byte[] audio, String type, String name) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiProviderException("Groq API key is not configured", AiProviderException.ErrorType.INVALID_INPUT);
        }

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
            body.add("model", "whisper-large-v3-turbo");
            body.add("language", "es");
            body.add("response_format", "json");

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(GROQ_URL, requestEntity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String text = (String) response.getBody().get("text");
                if (text == null || text.isBlank()) {
                    throw new AiProviderException("Groq returned empty transcription", AiProviderException.ErrorType.INTERNAL_ERROR);
                }
                return text.trim();
            } else {
                throw new AiProviderException("Groq returned error status: " + response.getStatusCode(), AiProviderException.ErrorType.INTERNAL_ERROR);
            }
        } catch (Exception e) {
            throw new AiProviderException("Failed to transcribe audio with Groq: " + e.getMessage(), AiProviderException.ErrorType.UNAVAILABLE, e);
        }
    }
}