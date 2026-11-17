package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.domain.knowledge.KnowledgeAnswer;
import com.riwi.skillbridge.domain.knowledge.KnowledgeSource;

import java.util.List;

/**
 * Respuesta del asistente de conocimiento RAG (HU-26, FR-003).
 */
public record KnowledgeAskResponse(
        String answer,
        boolean hasSufficientContext,
        List<Source> sources
) {
    public KnowledgeAskResponse {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }

    public record Source(
            String sourceId,
            String title,
            String snippet,
            double score
    ) {
        public static Source from(KnowledgeSource source) {
            return new Source(
                    source.sourceId(),
                    source.title(),
                    source.snippet(),
                    source.score()
            );
        }
    }

    public static KnowledgeAskResponse from(KnowledgeAnswer answer) {
        List<Source> sources = answer.sources() == null
                ? List.of()
                : answer.sources().stream().map(Source::from).toList();
        return new KnowledgeAskResponse(
                answer.answer(),
                answer.hasSufficientContext(),
                sources
        );
    }
}
