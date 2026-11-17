package com.riwi.skillbridge.infrastructure.adapter.out.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.google.genai.GoogleGenAiEmbeddingConnectionDetails;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuración para embeddings de Gemini (HU-26).
 *
 * Construye el bean EmbeddingModel solo cuando la clave de API está presente y no está vacía,
 * evitando que la falta de clave cause fallos durante el arranque del backend.
 * También respeta la propiedad spring.autoconfigure.exclude en entornos de prueba con ApplicationContextRunner.
 */
@Configuration
public class GeminiEmbeddingConfiguration {

    private static final Logger log = LoggerFactory.getLogger(GeminiEmbeddingConfiguration.class);

    @Bean
    public static BeanDefinitionRegistryPostProcessor geminiEmbeddingAutoConfigExcluder(Environment env) {
        return new ExcludeAutoConfigPostProcessor(env);
    }

    private static class ExcludeAutoConfigPostProcessor implements BeanDefinitionRegistryPostProcessor, PriorityOrdered {
        private final Environment env;

        ExcludeAutoConfigPostProcessor(Environment env) {
            this.env = env;
        }

        @Override
        public int getOrder() {
            return PriorityOrdered.LOWEST_PRECEDENCE;
        }

        @Override
        public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
            String exclude = env.getProperty("spring.autoconfigure.exclude", "");
            if (exclude == null || exclude.isBlank()) {
                return;
            }
            List<String> toRemove = new ArrayList<>();
            for (String beanName : registry.getBeanDefinitionNames()) {
                BeanDefinition bd = registry.getBeanDefinition(beanName);
                String beanClassName = bd.getBeanClassName();
                String factoryBeanName = bd.getFactoryBeanName();
                if ((beanClassName != null && exclude.contains(beanClassName))
                        || (factoryBeanName != null && exclude.contains(factoryBeanName))
                        || exclude.contains(beanName)) {
                    toRemove.add(beanName);
                }
            }
            for (String name : toRemove) {
                log.info("Excluyendo bean/autoconfiguración de embeddings: {}", name);
                registry.removeBeanDefinition(name);
            }
        }
    }

    @Bean
    @ConditionalOnMissingBean(EmbeddingModel.class)
    @ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${spring.ai.google.genai.embedding.api-key:}')")
    public EmbeddingModel geminiEmbeddingModel(
            @Value("${spring.ai.google.genai.embedding.api-key:}") String apiKey,
            @Value("${spring.ai.google.genai.embedding.text.options.model:gemini-embedding-001}") String modelName,
            @Value("${spring.ai.google.genai.embedding.text.options.dimensions:768}") int dimensions
    ) {
        log.info("Inicializando Google GenAI Text EmbeddingModel (modelo={}, dimensiones={})", modelName, dimensions);
        GoogleGenAiEmbeddingConnectionDetails connectionDetails = GoogleGenAiEmbeddingConnectionDetails.builder()
                .apiKey(apiKey)
                .build();

        GoogleGenAiTextEmbeddingOptions options = GoogleGenAiTextEmbeddingOptions.builder()
                .model(modelName)
                .dimensions(dimensions)
                .build();

        return new GoogleGenAiTextEmbeddingModel(connectionDetails, options);
    }
}
