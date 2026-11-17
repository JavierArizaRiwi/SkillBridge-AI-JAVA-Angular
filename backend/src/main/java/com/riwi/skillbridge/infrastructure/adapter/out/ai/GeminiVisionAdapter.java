package com.riwi.skillbridge.infrastructure.adapter.out.ai;
import com.riwi.skillbridge.application.port.out.VisionPort;
import com.riwi.skillbridge.domain.exception.AiProviderException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
@Component
public class GeminiVisionAdapter implements VisionPort {
 private final ChatClient client; public GeminiVisionAdapter(ChatClient.Builder b){client=b.build();}
 public String extractContext(byte[] image,String type,String name){ try { var resource=new ByteArrayResource(image){ public String getFilename(){return name;} }; var message=UserMessage.builder().text("Analiza esta imagen y extrae habilidades, tecnologías, experiencia u objetivos profesionales relevantes para recomendar servicios de SkillBridge. Devuelve contexto textual conciso en español.").media(new Media(MimeTypeUtils.parseMimeType(type),resource)).build(); String r=client.prompt().messages(message).call().content(); if(r==null||r.isBlank()) throw new AiProviderException("Gemini no devolvió contexto",AiProviderException.ErrorType.INTERNAL_ERROR); return r; } catch(AiProviderException e){throw e;} catch(RuntimeException e){throw new AiProviderException("No fue posible analizar la imagen",AiProviderException.ErrorType.UNAVAILABLE,e);} }
}
