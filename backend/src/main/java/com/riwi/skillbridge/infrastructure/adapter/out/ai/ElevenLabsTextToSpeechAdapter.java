package com.riwi.skillbridge.infrastructure.adapter.out.ai;

import com.riwi.skillbridge.application.port.out.TextToSpeechPort;
import com.riwi.skillbridge.domain.exception.AiProviderException;
import com.riwi.skillbridge.infrastructure.config.ElevenLabsProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

import java.time.Duration;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "spring.ai.tts.enabled", havingValue = "true")
public class ElevenLabsTextToSpeechAdapter implements TextToSpeechPort {

    private final RestClient client;
    private final ElevenLabsProperties props;

    public ElevenLabsTextToSpeechAdapter(ElevenLabsProperties props,
                                         @Value("${app.ai.timeout-seconds:10}") int timeoutSeconds) {
        this.props = props;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds * 3L)); // el audio tarda más
        this.client = RestClient.builder()
            .baseUrl("https://api.elevenlabs.io")
            .requestFactory(factory)
            .defaultHeader("xi-api-key", props.apiKey())
            .build();
    }

    @Override
    public AudioContent synthesize(String text) {
        try {
            byte[] audio = client.post()
                .uri("/v1/text-to-speech/{voiceId}?output_format=mp3_44100_128", props.voiceId())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.parseMediaType("audio/mpeg"))
                .body(Map.of("text", text, "model_id", props.modelId()))
                .retrieve()
                .body(byte[].class);

            if (audio == null || audio.length == 0) {
                throw new AiProviderException(
                    "ElevenLabs devolvió un audio vacío",
                    AiProviderException.ErrorType.INTERNAL_ERROR);
            }
            return new AudioContent(audio, "audio/mpeg");

        } catch (HttpClientErrorException e) {
            int status = e.getStatusCode().value();
            AiProviderException.ErrorType type = switch (status) {
                case 429 -> AiProviderException.ErrorType.RATE_LIMITED;
                case 400, 422 -> AiProviderException.ErrorType.INVALID_INPUT;
                default -> AiProviderException.ErrorType.INTERNAL_ERROR; // 401/403: clave o voz mal configurada
            };
            throw new AiProviderException("ElevenLabs respondió " + status, type, e);

        } catch (HttpServerErrorException e) {
            throw new AiProviderException(
                "ElevenLabs no está disponible (" + e.getStatusCode().value() + ")",
                AiProviderException.ErrorType.UNAVAILABLE, e);

        } catch (ResourceAccessException e) {
            // Error de red o timeout
            boolean timeout = e.getCause() instanceof java.net.SocketTimeoutException;
            throw new AiProviderException(
                timeout ? "Timeout al llamar a ElevenLabs" : "No se pudo conectar con ElevenLabs",
                timeout ? AiProviderException.ErrorType.TIMEOUT
                    : AiProviderException.ErrorType.UNAVAILABLE, e);

        } catch (RestClientException e) {
            throw new AiProviderException(
                "Error inesperado con ElevenLabs",
                AiProviderException.ErrorType.INTERNAL_ERROR, e);
        }
    }
}
