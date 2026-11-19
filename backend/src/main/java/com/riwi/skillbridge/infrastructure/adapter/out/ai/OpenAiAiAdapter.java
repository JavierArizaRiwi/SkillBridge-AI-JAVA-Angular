package com.riwi.skillbridge.infrastructure.adapter.out.ai;

import com.riwi.skillbridge.application.port.out.AiRecommendationPort;
import com.riwi.skillbridge.application.port.out.AiStructuredResponse;
import com.riwi.skillbridge.application.recommendation.RecommendationRequest;
import com.riwi.skillbridge.domain.exception.AiProviderException;
import com.riwi.skillbridge.domain.exception.AiProviderException.ErrorType;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.infrastructure.config.AiProviderProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.ArrayList;

@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "openai")
public class OpenAiAiAdapter implements AiRecommendationPort {
    private final ChatClient chatClient;
    private final AiProviderProperties properties;

    public OpenAiAiAdapter(ChatClient.Builder chatClientBuilder, AiProviderProperties properties) {
        this.chatClient = chatClientBuilder.build();
        this.properties = properties;
    }

    @Override
    public AiStructuredResponse recommend(RecommendationRequest request, List<Offering> offerings) {
        String catalog = offerings.stream()
                .map(o -> "- ID: %s | Title: %s | Category: [%s] | Description: %s".formatted(o.id(), o.title(), o.category(), o.description()))
                .reduce("", (a, b) -> a + "\n" + b);

        String prompt = """
                You are the SkillBridge AI assistant. Your role is to recommend at most 3 services from the catalog
                that align with the user's learning goal. For each recommendation:
                - Explain briefly why this service matches the goal
                - Only recommend services that exist in the catalog below using their exact ID
                
                Do NOT invent services that are not in the catalog.
                If the user's goal is completely unrelated to any catalog offering, provide a polite explanation 
                stating that SkillBridge does not offer those services, and leave recommendations empty.
                
                User's Goal:
                %s
                
                Available Catalog:
                %s
                """.formatted(request.normalizedNeed(), catalog);

        try {
            AiStructuredResponse response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .entity(AiStructuredResponse.class);
            
            if (response == null) {
                throw new AiProviderException("OpenAI did not return a valid structured response", ErrorType.INTERNAL_ERROR);
            }
            if (response.recommendations() == null) {
                return new AiStructuredResponse(response.explanation(), new ArrayList<>());
            }
            return response;
        } catch (AiProviderException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw new AiProviderException("OpenAI is not properly configured: " + ex.getMessage(), ErrorType.INVALID_INPUT, ex);
        } catch (RuntimeException ex) {
            throw new AiProviderException("OpenAI is temporarily unavailable (timeout: " + properties.getTimeoutSeconds() + "s)", ErrorType.UNAVAILABLE, ex);
        }
    }
}