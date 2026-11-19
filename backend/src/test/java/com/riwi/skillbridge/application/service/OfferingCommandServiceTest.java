package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.CreateOfferingUseCase;
import com.riwi.skillbridge.application.port.out.OfferingCachePort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.domain.exception.ForbiddenOperationException;
import com.riwi.skillbridge.domain.model.Actor;
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
    private final OfferingCommandService service =
        new OfferingCommandService(repository, cache, new OfferingAccessPolicy(), org.mockito.Mockito.mock(com.riwi.skillbridge.application.port.out.event.AuditEventPublisherPort.class));

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
}
