package com.riwi.skillbridge.infrastructure.config;

import com.riwi.skillbridge.application.port.in.AskKnowledgeUseCase;
import com.riwi.skillbridge.application.port.out.CategoryRepositoryPort;
import com.riwi.skillbridge.application.port.out.EmbeddingPort;
import com.riwi.skillbridge.application.port.out.KnowledgeAnswerPort;
import com.riwi.skillbridge.application.port.out.KnowledgeCorpusSourcePort;
import com.riwi.skillbridge.application.port.out.KnowledgeIndexPort;
import com.riwi.skillbridge.application.port.out.KnowledgeSearchPort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.service.knowledge.AskKnowledgeService;
import com.riwi.skillbridge.application.service.knowledge.KnowledgeCorpusLoader;
import com.riwi.skillbridge.application.service.knowledge.KnowledgeIndexService;
import com.riwi.skillbridge.application.service.knowledge.KnowledgePromptBuilder;
import com.riwi.skillbridge.application.service.knowledge.SourceCitationValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cableado de la funcionalidad RAG (HU-26). Las clases de aplicación son Java puro; aquí se registran como beans.
 * <p>
 * Solo se activa cuando {@code app.rag.min-score-threshold} está definido: el umbral no tiene valor por defecto
 * inventado y se fija tras calibrar con el conjunto de evaluación (FR-019, T030). Sin él, la funcionalidad queda
 * desactivada (no hay endpoint ni índice) en lugar de responder con un umbral arbitrario.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.rag", name = "min-score-threshold")
public class KnowledgeConfiguration {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeConfiguration.class);

    @Bean
    KnowledgePromptBuilder knowledgePromptBuilder() {
        return new KnowledgePromptBuilder();
    }

    @Bean
    SourceCitationValidator sourceCitationValidator() {
        return new SourceCitationValidator();
    }

    @Bean
    AskKnowledgeUseCase askKnowledgeUseCase(KnowledgeSearchPort searchPort,
                                            KnowledgeAnswerPort answerPort,
                                            KnowledgePromptBuilder promptBuilder,
                                            SourceCitationValidator citationValidator,
                                            KnowledgeProperties properties) {
        return new AskKnowledgeService(searchPort, answerPort, promptBuilder, citationValidator,
                properties.getMinScoreThreshold(), properties.getTopK());
    }

    @Bean
    KnowledgeCorpusLoader knowledgeCorpusLoader(KnowledgeCorpusSourcePort corpusSource) {
        return new KnowledgeCorpusLoader(corpusSource);
    }

    @Bean
    KnowledgeIndexService knowledgeIndexService(KnowledgeCorpusLoader corpusLoader,
                                                OfferingRepositoryPort offeringRepository,
                                                CategoryRepositoryPort categoryRepository,
                                                EmbeddingPort embeddingPort,
                                                KnowledgeIndexPort indexPort) {
        return new KnowledgeIndexService(corpusLoader, offeringRepository, categoryRepository, embeddingPort, indexPort);
    }

    /** Carga inicial del índice al arrancar. Un fallo se registra pero no impide levantar el backend. */
    @Bean
    ApplicationRunner knowledgeIndexInitializer(KnowledgeIndexService indexService) {
        return args -> {
            try {
                int indexed = indexService.loadInitialIndex();
                log.info("Knowledge index loaded: {} chunks", indexed);
            } catch (RuntimeException e) {
                log.warn("Knowledge index initial load failed: {}", e.getClass().getSimpleName());
            }
        };
    }
}
