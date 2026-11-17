package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.CreateOfferingUseCase;
import com.riwi.skillbridge.application.port.in.UpdateOfferingUseCase;
import com.riwi.skillbridge.application.port.out.OfferingCachePort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.service.knowledge.KnowledgeIndexService;
import com.riwi.skillbridge.domain.exception.ForbiddenOperationException;
import com.riwi.skillbridge.domain.model.Actor;
import com.riwi.skillbridge.domain.model.MentorshipMode;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.domain.policy.OfferingAccessPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class OfferingCommandServiceTest {

    private final OfferingRepositoryPort repository = mock(OfferingRepositoryPort.class);
    private final OfferingCachePort cache = mock(OfferingCachePort.class);
    private final KnowledgeIndexService knowledgeIndexService = mock(KnowledgeIndexService.class);
    private final OfferingCommandService service =
        new OfferingCommandService(repository, cache, new OfferingAccessPolicy(), org.mockito.Mockito.mock(com.riwi.skillbridge.application.port.out.event.AuditEventPublisherPort.class), knowledgeIndexService);

    private final UUID ownerId = UUID.randomUUID();
    private final Offering offering =
        new Offering(UUID.randomUUID(), ownerId, "Java", "desc", "BACKEND", BigDecimal.TEN, true);

    @Test
    void provider_crea_offering_y_el_dueno_es_el_actor_aunque_envie_otro_providerId() {
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        var command = new CreateOfferingUseCase.Command(UUID.randomUUID(), "T", "d", "C", BigDecimal.ONE);

        Offering result = service.create(new Actor(ownerId, Role.PROVIDER), command);

        assertEquals(ownerId, result.providerId());
        verify(cache).evictActiveOfferings();
    }

    @Test
    void customer_no_puede_crear() {
        var command = new CreateOfferingUseCase.Command(null, "T", "d", "C", BigDecimal.ONE);

        assertThrows(ForbiddenOperationException.class,
            () -> service.create(new Actor(UUID.randomUUID(), Role.CUSTOMER), command));

        verifyNoInteractions(repository);
    }

    @Test
    void provider_no_puede_desactivar_offering_ajeno() {
        when(repository.findById(offering.id())).thenReturn(Optional.of(offering));
        Actor otro = new Actor(UUID.randomUUID(), Role.PROVIDER);

        assertThrows(ForbiddenOperationException.class, () -> service.deactivate(otro, offering.id()));

        verify(repository, never()).save(any());
        verifyNoInteractions(cache);
    }

    @Test
    void admin_desactiva_offering_de_cualquier_provider() {
        when(repository.findById(offering.id())).thenReturn(Optional.of(offering));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.deactivate(new Actor(UUID.randomUUID(), Role.ADMIN), offering.id());

        verify(repository).save(argThat(o -> !o.active() && o.providerId().equals(ownerId)));
        verify(cache).evictActiveOfferings();
    }

    @Test
    void desactivar_uno_ya_inactivo_es_idempotente() {
        Offering inactive = offering.deactivate();
        when(repository.findById(inactive.id())).thenReturn(Optional.of(inactive));

        service.deactivate(new Actor(ownerId, Role.PROVIDER), inactive.id());

        verify(repository, never()).save(any());
    }

    @Test
    void reindexacion_se_llama_en_las_cuatro_operaciones() {
        // Arrange
        UUID offeringId = offering.id();
        when(repository.findById(offeringId)).thenReturn(Optional.of(offering));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        org.mockito.InOrder inOrder = inOrder(knowledgeIndexService);

        // 1. Crear
        var createCmd = new CreateOfferingUseCase.Command(
                UUID.randomUUID(), "Nuevo Java", "desc", "BACKEND", BigDecimal.valueOf(100),
                MentorshipMode.LIVE, 2
        );
        Offering created = service.create(new Actor(ownerId, Role.PROVIDER), createCmd);
        inOrder.verify(knowledgeIndexService).reindexOffering(created);

        // 2. Actualizar
        var updateCmd = new UpdateOfferingUseCase.Command(
                "Java Actualizado", "nueva desc", "BACKEND", BigDecimal.valueOf(120),
                MentorshipMode.LIVE, 3
        );
        Offering updated = service.update(new Actor(ownerId, Role.PROVIDER), offeringId, updateCmd);
        inOrder.verify(knowledgeIndexService).reindexOffering(updated);

        // 3. Desactivar
        service.deactivate(new Actor(ownerId, Role.PROVIDER), offeringId);
        inOrder.verify(knowledgeIndexService).reindexOffering(argThat(o ->
                o.id().equals(offeringId) && !o.active()
        ));

        // 4. Activar
        Offering inactive = offering.deactivate();
        when(repository.findById(offeringId)).thenReturn(Optional.of(inactive));
        service.activate(new Actor(ownerId, Role.PROVIDER), offeringId);
        inOrder.verify(knowledgeIndexService).reindexOffering(argThat(o ->
                o.id().equals(offeringId) && o.active()
        ));

        inOrder.verifyNoMoreInteractions();
    }
}
