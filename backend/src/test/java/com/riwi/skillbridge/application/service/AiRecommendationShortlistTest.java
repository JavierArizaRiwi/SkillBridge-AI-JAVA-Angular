package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.AiRecommendationPort;
import com.riwi.skillbridge.application.port.out.AiStructuredResponse;
import com.riwi.skillbridge.application.port.out.KnowledgeCandidate;
import com.riwi.skillbridge.application.port.out.KnowledgeSearchPort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.port.out.event.AuditEventPublisherPort;
import com.riwi.skillbridge.application.recommendation.InputType;
import com.riwi.skillbridge.application.recommendation.RecommendationRequest;
import com.riwi.skillbridge.domain.exception.AiProviderException;
import com.riwi.skillbridge.domain.knowledge.KnowledgeChunk;
import com.riwi.skillbridge.domain.model.Offering;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** HU-26: el LLM de recomendaciones recibe solo las ofertas preseleccionadas por RAG, no todo el catálogo. */
@DisplayName("AiRecommendationService: preselección RAG del catálogo")
class AiRecommendationShortlistTest {

    private AiRecommendationPort ai;
    private OfferingRepositoryPort offerings;
    private KnowledgeSearchPort search;
    private final List<Offering> catalog = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ai = mock(AiRecommendationPort.class);
        offerings = mock(OfferingRepositoryPort.class);
        search = mock(KnowledgeSearchPort.class);
        catalog.clear();
        for (int i = 0; i < 40; i++) {
            catalog.add(new Offering(UUID.randomUUID(), UUID.randomUUID(), "Curso " + i, "Descripción " + i,
                    "CAT", BigDecimal.TEN, true));
        }
        when(offerings.findAllActive()).thenReturn(catalog);
        when(ai.recommend(any(), any())).thenReturn(new AiStructuredResponse("ok", List.of()));
    }

    private AiRecommendationService service(KnowledgeSearchPort port, int size) {
        return new AiRecommendationService(ai, offerings, mock(AuditEventPublisherPort.class), port, size);
    }

    private RecommendationRequest request() {
        return new RecommendationRequest(InputType.TEXT, "Quiero aprender Spring Boot", null, UUID.randomUUID());
    }

    private KnowledgeCandidate hit(Offering o) {
        return new KnowledgeCandidate(new KnowledgeChunk("offering:" + o.id(), KnowledgeChunk.SourceType.OFFERING,
                o.title(), "contenido", KnowledgeChunk.Language.ES, "h-" + o.id()), 0.8);
    }

    @SuppressWarnings("unchecked")
    private List<Offering> offeredToLlm() {
        ArgumentCaptor<List<Offering>> captor = ArgumentCaptor.forClass(List.class);
        verify(ai).recommend(any(), captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("Catálogo grande: el LLM recibe solo las ofertas devueltas por la búsqueda, en su orden")
    void catalogoGrande_enviaSoloLaPreseleccion() {
        List<Offering> top = List.of(catalog.get(7), catalog.get(3), catalog.get(21));
        when(search.searchByType(anyString(), eq(15), eq(KnowledgeChunk.SourceType.OFFERING)))
                .thenReturn(top.stream().map(this::hit).toList());

        service(search, 15).recommend(request());

        assertEquals(top, offeredToLlm());
    }

    @Test
    @DisplayName("Catálogo igual o menor al tamaño: no se consulta el RAG ni se gasta una llamada de embeddings")
    void catalogoPequeno_noConsultaElRag() {
        service(search, 100).recommend(request());

        verifyNoInteractions(search);
        assertEquals(catalog, offeredToLlm());
    }

    @Test
    @DisplayName("Si los embeddings fallan, se recomienda con el catálogo completo (la preselección es solo una optimización)")
    void fallaDeEmbeddings_usaCatalogoCompleto() {
        when(search.searchByType(anyString(), anyInt(), any()))
                .thenThrow(new AiProviderException("caído", AiProviderException.ErrorType.UNAVAILABLE));

        service(search, 15).recommend(request());

        assertEquals(catalog, offeredToLlm());
    }

    @Test
    @DisplayName("Sin coincidencias mapeables (índice vacío o desactualizado): catálogo completo")
    void sinCoincidencias_usaCatalogoCompleto() {
        when(search.searchByType(anyString(), anyInt(), any())).thenReturn(List.of());
        service(search, 15).recommend(request());
        assertEquals(catalog, offeredToLlm());
    }

    @Test
    @DisplayName("Fragmentos de ofertas ya inactivas o desconocidas se ignoran; solo llegan ofertas activas")
    void ignoraOfertasQueNoEstanActivas() {
        Offering ghost = new Offering(UUID.randomUUID(), UUID.randomUUID(), "Fantasma", "x", "CAT", BigDecimal.TEN, true);
        when(search.searchByType(anyString(), anyInt(), any()))
                .thenReturn(List.of(hit(ghost), hit(catalog.get(1))));

        service(search, 15).recommend(request());

        assertEquals(List.of(catalog.get(1)), offeredToLlm());
        assertTrue(offeredToLlm().stream().noneMatch(o -> o.id().equals(ghost.id())));
    }

    @Test
    @DisplayName("tamaño 0 desactiva la preselección")
    void tamanoCero_desactiva() {
        service(search, 0).recommend(request());
        verify(search, never()).searchByType(anyString(), anyInt(), any());
        assertEquals(catalog, offeredToLlm());
    }

    @Test
    @DisplayName("Sin puerto de búsqueda (constructor original) se mantiene el comportamiento anterior")
    void sinPuerto_mantieneComportamientoAnterior() {
        new AiRecommendationService(ai, offerings, mock(AuditEventPublisherPort.class)).recommend(request());
        assertEquals(catalog, offeredToLlm());
    }

    @Test
    @DisplayName("Cada recomendación lleva el título de la oferta y se descartan ids que no son del catálogo activo")
    void incluyeTituloYDescartaIdsInexistentes() {
        Offering real = catalog.get(4);
        when(ai.recommend(any(), any())).thenReturn(new AiStructuredResponse("ok", List.of(
                new AiStructuredResponse.AiItem(real.id().toString(), 0.9, "Encaja"),
                new AiStructuredResponse.AiItem(UUID.randomUUID().toString(), 0.8, "Inventada"),
                new AiStructuredResponse.AiItem("no-es-uuid", 0.7, "Basura"))));

        var result = new AiRecommendationService(ai, offerings, mock(AuditEventPublisherPort.class)).recommend(request());

        assertEquals(1, result.recommendations().size());
        assertEquals(real.id(), result.recommendations().getFirst().offeringId());
        assertEquals(real.title(), result.recommendations().getFirst().offeringTitle());
    }
}
