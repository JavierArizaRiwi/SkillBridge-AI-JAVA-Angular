package com.riwi.skillbridge.infrastructure.adapter.out.ai;
import com.riwi.skillbridge.application.port.out.SpeechToTextPort;
import com.riwi.skillbridge.domain.exception.AiProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.MimeTypeUtils;

@Component
@ConditionalOnProperty(name = "app.ai.audio-provider", havingValue = "gemini", matchIfMissing = true)
public class GeminiSpeechToTextAdapter implements SpeechToTextPort {
    private static final Logger log = LoggerFactory.getLogger(GeminiSpeechToTextAdapter.class);
    private final ChatClient client; 
    
    public GeminiSpeechToTextAdapter(ChatClient.Builder b){
        client=b.build();
    }
    
    public String transcribe(byte[] audio, String type, String name){ 
        log.info("Iniciando transcripción de audio con Gemini (archivo: '{}', tipo: '{}')...", name, type);
        try { 
            var resource = new ByteArrayResource(audio){ 
                public String getFilename(){return name;} 
            }; 
            var message = UserMessage.builder()
                .text("Transcribe este audio al español. Devuelve solamente el texto transcrito sin comillas ni formato adicional.")
                .media(new Media(MimeTypeUtils.parseMimeType(type), resource))
                .build(); 
                
            String r = client.prompt().messages(message).call().content(); 
            if(r == null || r.isBlank()) throw new AiProviderException("Gemini no devolvió transcripción", AiProviderException.ErrorType.INTERNAL_ERROR); 
            log.info("Transcripción exitosa con Gemini: '{}'", r.trim());
            return r.trim(); 
        } catch(AiProviderException e){
            log.error("Error en proveedor Gemini durante transcripción: {}", e.getMessage());
            throw e;
        } catch(RuntimeException e){
            log.error("Error inesperado transcribiendo con Gemini: {}", e.getMessage());
            throw new AiProviderException("No fue posible transcribir el audio con Gemini",AiProviderException.ErrorType.UNAVAILABLE,e);
        } 
    }
}