package com.riwi.skillbridge.infrastructure.adapter.out.ai;

import com.riwi.skillbridge.application.port.out.AiRecommendationPort;
import com.riwi.skillbridge.application.port.out.AiStructuredResponse;
import com.riwi.skillbridge.application.recommendation.RecommendationRequest;
import com.riwi.skillbridge.domain.exception.AiProviderException;
import com.riwi.skillbridge.domain.exception.AiProviderException.ErrorType;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.infrastructure.config.AiProviderProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.ArrayList;

@Component
public class GeminiAiAdapter implements AiRecommendationPort {
    private final ChatClient chatClient;
    private final AiProviderProperties properties;

    public GeminiAiAdapter(ChatClient.Builder chatClientBuilder, AiProviderProperties properties) {
        this.chatClient = chatClientBuilder.build();
        this.properties = properties;
    }

    @Override
    public AiStructuredResponse recommend(RecommendationRequest request, List<Offering> offerings) {
        String catalog = offerings.stream()
                .map(o -> "- ID: %s | Título: %s | Categoría: [%s] | Descripción: %s".formatted(o.id(), o.title(), o.category(), o.description()))
                .reduce("", (a, b) -> a + "\n" + b);

        String prompt = """
                Eres el asistente inteligente de SkillBridge AI.
                Tu objetivo es recomendar como máximo 3 servicios del catálogo que ayuden al usuario a lograr su objetivo.
                
                REGLAS IMPORTANTES:
                1. NO inventes servicios que no estén en el catálogo provisto. Solo usa los IDs del catálogo.
                2. Si el objetivo del usuario NO tiene relación alguna con los servicios que ofrece SkillBridge,
                   debes responder con una 'explanation' amable indicando que no ofrecemos servicios para eso, 
                   y dejar la lista de 'recommendations' vacía.
                3. Devuelve los resultados estructurados.

                Objetivo del usuario:
                %s

                Catálogo disponible:
                %s
                """.formatted(request.normalizedNeed(), catalog);

        try {
            AiStructuredResponse response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .entity(AiStructuredResponse.class);
            
            if (response == null) {
                throw new AiProviderException(
                    "Gemini did not return a valid structured response",
                    ErrorType.INTERNAL_ERROR
                );
            }
            // Ensure lists are never null
            if (response.recommendations() == null) {
                return new AiStructuredResponse(response.explanation(), new ArrayList<>());
            }
            return response;
        } catch (AiProviderException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw new AiProviderException(
                "Gemini is not properly configured: " + ex.getMessage(),
                ErrorType.INVALID_INPUT,
                ex
            );
        } catch (RuntimeException ex) {
            throw new AiProviderException(
                "Gemini is temporarily unavailable (timeout: " + properties.getTimeoutSeconds() + "s)",
                ErrorType.UNAVAILABLE,
                ex
            );
        }
    }
}