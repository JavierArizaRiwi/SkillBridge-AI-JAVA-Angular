package com.riwi.skillbridge.application.service.knowledge;

import com.riwi.skillbridge.domain.knowledge.KnowledgeAnswer;
import com.riwi.skillbridge.domain.knowledge.KnowledgeQuery;
import com.riwi.skillbridge.domain.service.knowledge.HybridRanker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** T026: por debajo del umbral no se llama al LLM y la respuesta es constante (FR-006, SC-002, SC-005). */
class KnowledgeEarlyRejectionTest {

    private AskKnowledgeService service(DeterministicKnowledgeFixture.SpyAnswerPort spy) {
        var search = new DeterministicKnowledgeFixture.InMemorySearch(
                DeterministicKnowledgeFixture.corpus(), HybridRanker.Strategy.HYBRID);
        return new AskKnowledgeService(search, spy, 0.5, 3);
    }

    @Test
    void fueraDeDominio_noLlamaAlLlmYRespondeMensajeConstante() {
        var spy = new DeterministicKnowledgeFixture.SpyAnswerPort();
        KnowledgeAnswer a = service(spy).ask(new KnowledgeQuery("¿Cómo reparar la transmisión de un diésel?"));
        assertFalse(a.hasSufficientContext());
        assertTrue(a.sources().isEmpty());
        assertEquals(AskKnowledgeService.NO_INFO_MESSAGE, a.answer());
        assertEquals(0, spy.calls.get(), "El LLM no debe invocarse bajo el umbral");
    }

    @Test
    void mensajeSinInformacionEsDeterministaEntreConsultas() {
        var spy = new DeterministicKnowledgeFixture.SpyAnswerPort();
        var s = service(spy);
        String m1 = s.ask(new KnowledgeQuery("receta de paella valenciana")).answer();
        String m2 = s.ask(new KnowledgeQuery("quantum chromodynamics lattice")).answer();
        assertEquals(m1, m2);
        assertEquals(AskKnowledgeService.NO_INFO_MESSAGE, m1);
        assertEquals(0, spy.calls.get());
    }

    @Test
    void preguntaDelDominio_siSuperaElUmbralLlamaAlLlmUnaVez() {
        var spy = new DeterministicKnowledgeFixture.SpyAnswerPort();
        KnowledgeAnswer a = service(spy).ask(new KnowledgeQuery("¿Con cuánta anticipación debo cancelar una reserva?"));
        assertTrue(a.hasSufficientContext());
        assertEquals(1, spy.calls.get());
    }
}
