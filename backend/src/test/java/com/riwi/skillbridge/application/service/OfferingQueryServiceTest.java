package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.domain.exception.ForbiddenOperationException;
import com.riwi.skillbridge.domain.model.Actor;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.PageQuery;
import com.riwi.skillbridge.domain.model.PageResult;
import com.riwi.skillbridge.domain.model.Role;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class OfferingQueryServiceTest {

    private final OfferingRepositoryPort repository = mock(OfferingRepositoryPort.class);
    private final OfferingQueryService service = new OfferingQueryService(repository);

    @Test
    void provider_solo_consulta_los_suyos_usando_su_propio_id() {
        UUID providerId = UUID.randomUUID();
        var mine = List.of(new Offering(UUID.randomUUID(), providerId, "Java", "d", "BACKEND", BigDecimal.TEN, false));
        when(repository.findByProviderId(providerId)).thenReturn(mine);

        assertEquals(mine, service.listMine(new Actor(providerId, Role.PROVIDER)));
        verify(repository, never()).findAll();
    }

    @Test
    void customer_no_puede_listar_propios_ni_todos() {
        Actor customer = new Actor(UUID.randomUUID(), Role.CUSTOMER);

        assertThrows(ForbiddenOperationException.class, () -> service.listMine(customer));
        assertThrows(ForbiddenOperationException.class, () -> service.listAll(customer));
        verifyNoInteractions(repository);
    }

    @Test
    void provider_no_puede_listar_todos() {
        assertThrows(ForbiddenOperationException.class,
            () -> service.listAll(new Actor(UUID.randomUUID(), Role.PROVIDER)));
        verifyNoInteractions(repository);
    }

    @Test
    void admin_lista_todos() {
        var all = List.of(new Offering(UUID.randomUUID(), UUID.randomUUID(), "Java", "d", "BACKEND", BigDecimal.TEN, true));
        when(repository.findAll()).thenReturn(all);

        assertEquals(all, service.listAll(new Actor(UUID.randomUUID(), Role.ADMIN)));
    }

    private static final PageQuery QUERY = new PageQuery(1, 5, "title", false);

    @Test
    void provider_pagina_solo_los_suyos() {
        UUID providerId = UUID.randomUUID();
        var page = new PageResult<Offering>(List.of(), 1, 5, 12, 3);
        when(repository.findByProviderId(providerId, QUERY)).thenReturn(page);

        assertEquals(page, service.listMine(new Actor(providerId, Role.PROVIDER), QUERY));
        verify(repository, never()).findAll(any(PageQuery.class));
    }

    @Test
    void admin_pagina_todos() {
        var page = new PageResult<Offering>(List.of(), 1, 5, 12, 3);
        when(repository.findAll(QUERY)).thenReturn(page);

        assertEquals(page, service.listAll(new Actor(UUID.randomUUID(), Role.ADMIN), QUERY));
    }

    @Test
    void listados_paginados_respetan_los_roles() {
        Actor customer = new Actor(UUID.randomUUID(), Role.CUSTOMER);

        assertThrows(ForbiddenOperationException.class, () -> service.listMine(customer, QUERY));
        assertThrows(ForbiddenOperationException.class, () -> service.listAll(customer, QUERY));
        assertThrows(ForbiddenOperationException.class,
            () -> service.listAll(new Actor(UUID.randomUUID(), Role.PROVIDER), QUERY));
        verifyNoInteractions(repository);
    }
}
