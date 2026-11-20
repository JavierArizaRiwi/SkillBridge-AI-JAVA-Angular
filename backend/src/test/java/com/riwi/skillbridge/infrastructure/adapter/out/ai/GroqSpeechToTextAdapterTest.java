package com.riwi.skillbridge.infrastructure.adapter.out.ai;

import com.riwi.skillbridge.domain.exception.AiProviderException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GroqSpeechToTextAdapterTest {

    @Test
    void shouldTranscribeAudioSuccessfully() {
        RestTemplateBuilder builder = mock(RestTemplateBuilder.class);
        RestTemplate template = mock(RestTemplate.class);
        when(builder.build()).thenReturn(template);
        
        when(template.postForEntity(any(String.class), any(), eq(Map.class)))
            .thenReturn(new ResponseEntity<>(Map.of("text", "Hola mundo"), HttpStatus.OK));

        GroqSpeechToTextAdapter adapter = new GroqSpeechToTextAdapter(builder, "dummy-key");
        String result = adapter.transcribe(new byte[]{1, 2, 3}, "audio/mp3", "test.mp3");
        
        assertEquals("Hola mundo", result);
    }

    @Test
    void shouldThrowExceptionWhenApiKeyMissing() {
        RestTemplateBuilder builder = mock(RestTemplateBuilder.class);
        GroqSpeechToTextAdapter adapter = new GroqSpeechToTextAdapter(builder, "");
        
        assertThrows(AiProviderException.class, () -> 
            adapter.transcribe(new byte[]{1, 2, 3}, "audio/mp3", "test.mp3")
        );
    }
}