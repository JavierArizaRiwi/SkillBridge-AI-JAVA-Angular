package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.AskKnowledgeUseCase;
import com.riwi.skillbridge.domain.knowledge.KnowledgeAnswer;
import com.riwi.skillbridge.domain.knowledge.KnowledgeQuery;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adaptador de entrada REST para las consultas al asistente de conocimiento (HU-26).
 * Llama exclusivamente al puerto de entrada AskKnowledgeUseCase (constitución Art. I.2).
 */
@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "app.rag", name = "min-score-threshold")
@RequestMapping("/api/ai/knowledge")
public class KnowledgeController {

    private final AskKnowledgeUseCase askKnowledgeUseCase;

    public KnowledgeController(AskKnowledgeUseCase askKnowledgeUseCase) {
        this.askKnowledgeUseCase = askKnowledgeUseCase;
    }

    @PostMapping("/ask")
    public KnowledgeAskResponse ask(@Valid @RequestBody KnowledgeAskRequest request) {
        KnowledgeAnswer answer = askKnowledgeUseCase.ask(new KnowledgeQuery(request.question()));
        return KnowledgeAskResponse.from(answer);
    }
}
