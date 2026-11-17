package com.riwi.skillbridge.application.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.riwi.skillbridge.application.port.out.ProviderRequestPort;
import com.riwi.skillbridge.application.port.out.UserAdminPort;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.exception.ForbiddenOperationException;
import com.riwi.skillbridge.domain.model.Actor;
import com.riwi.skillbridge.domain.model.PageQuery;
import com.riwi.skillbridge.domain.model.PageResult;
import com.riwi.skillbridge.domain.model.ProviderRequest;
import com.riwi.skillbridge.domain.model.ProviderRequestStatus;
import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.domain.model.UserAccount;
import com.riwi.skillbridge.domain.model.UserStatus;
import com.riwi.skillbridge.domain.policy.ProviderRequestPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProviderRequestServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-11T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final ProviderRequestPort providerRequests = mock(ProviderRequestPort.class);
    private final UserAdminPort users = mock(UserAdminPort.class);
    private final ProviderRequestPolicy policy = new ProviderRequestPolicy();

    private final ProviderRequestService service =
        new ProviderRequestService(providerRequests, users, policy, FIXED_CLOCK);

    private final UUID customerId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();

    private final Actor customerActor = new Actor(customerId, Role.CUSTOMER);
    private final Actor adminActor = new Actor(adminId, Role.ADMIN);
    private final Actor providerActor = new Actor(otherUserId, Role.PROVIDER);

    private final UserAccount customerUser =
        new UserAccount(customerId, "Ana", "ana@test.com", "hash", Role.CUSTOMER, UserStatus.ACTIVE);
    private final UserAccount adminUser =
        new UserAccount(adminId, "Admin", "admin@test.com", "hash", Role.ADMIN, UserStatus.ACTIVE);

    private ListAppender<ILoggingEvent> auditLog;

    @BeforeEach
    void setUp() {
        when(users.findById(customerId)).thenReturn(Optional.of(customerUser));
        when(users.findById(adminId)).thenReturn(Optional.of(adminUser));
        when(providerRequests.save(any())).thenAnswer(i -> i.getArgument(0));

        auditLog = new ListAppender<>();
        auditLog.start();
        ((Logger) LoggerFactory.getLogger("AUDIT")).addAppender(auditLog);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger("AUDIT")).detachAppender(auditLog);
    }

    // ---------- Creación de solicitudes ----------

    @Test
    void customer_crea_solicitud_con_exito() {
        ProviderRequest result = service.create(customerActor, "Quiero ofrecer clases de Java");

        assertNotNull(result);
        assertEquals(customerId, result.userId());
        assertEquals(ProviderRequestStatus.PENDING, result.status());
        assertEquals("Quiero ofrecer clases de Java", result.message());
        assertEquals(NOW, result.createdAt());

        verify(providerRequests).save(argThat(r -> r.userId().equals(customerId) && r.isPending()));

        String logMsg = auditLog.list.get(0).getFormattedMessage();
        assertTrue(logMsg.contains("outcome=SUCCESS"));
        assertTrue(logMsg.contains(customerId.toString()));
        assertFalse(logMsg.contains("ana@test.com"));
        assertFalse(logMsg.contains("Ana"));
    }

    @Test
    void provider_no_puede_crear_solicitud() {
        when(users.findById(otherUserId)).thenReturn(Optional.of(
            new UserAccount(otherUserId, "Pedro", "pedro@test.com", "hash", Role.PROVIDER, UserStatus.ACTIVE)));

        assertThrows(ForbiddenOperationException.class,
            () -> service.create(providerActor, "Quiero más servicios"));
        verify(providerRequests, never()).save(any());
    }

    @Test
    void no_se_puede_crear_solicitud_si_cuenta_esta_suspendida() {
        UserAccount suspended = customerUser.withStatus(UserStatus.SUSPENDED);
        when(users.findById(customerId)).thenReturn(Optional.of(suspended));

        assertThrows(BusinessRuleException.class,
            () -> service.create(customerActor, "Quiero reactivarme"));
        verify(providerRequests, never()).save(any());
    }

    @Test
    void no_se_puede_crear_solicitud_con_motivo_vacio() {
        assertThrows(IllegalArgumentException.class,
            () -> service.create(customerActor, "   "));
        verify(providerRequests, never()).save(any());
    }

    // ---------- Consulta de mis solicitudes ----------

    @Test
    void customer_consulta_sus_propias_solicitudes() {
        ProviderRequest req = ProviderRequest.create(customerId, "Mentoría", NOW);
        when(providerRequests.findByUserId(customerId)).thenReturn(List.of(req));

        List<ProviderRequest> result = service.getMyRequests(customerActor);

        assertEquals(1, result.size());
        assertEquals(req.id(), result.get(0).id());
        verify(providerRequests).findByUserId(customerId);
    }

    // ---------- Listado de solicitudes pendientes por Admin ----------

    @Test
    void admin_lista_solicitudes_pendientes() {
        PageQuery query = new PageQuery(0, 10, "createdAt", false);
        ProviderRequest req = ProviderRequest.create(customerId, "Mentoría", NOW);
        PageResult<ProviderRequest> page = new PageResult<>(List.of(req), 0, 10, 1, 1);
        when(providerRequests.findByStatus(ProviderRequestStatus.PENDING, query)).thenReturn(page);

        PageResult<ProviderRequest> result = service.listPending(adminActor, query);

        assertSame(page, result);
        verify(providerRequests).findByStatus(ProviderRequestStatus.PENDING, query);
    }

    @Test
    void no_admin_no_puede_listar_solicitudes_pendientes() {
        PageQuery query = new PageQuery(0, 10, "createdAt", false);

        assertThrows(ForbiddenOperationException.class,
            () -> service.listPending(customerActor, query));
        verify(providerRequests, never()).findByStatus(any(), any());
    }

    // ---------- Aprobación de solicitud ----------

    @Test
    void admin_aprueba_solicitud_y_asigna_rol_provider() {
        ProviderRequest pending = ProviderRequest.create(customerId, "Mentoría", NOW.minusSeconds(3600));
        when(providerRequests.findById(pending.id())).thenReturn(Optional.of(pending));

        ProviderRequest result = service.approve(adminActor, pending.id(), "Perfil verificado");

        assertEquals(ProviderRequestStatus.APPROVED, result.status());
        assertEquals(adminId, result.decidedBy());
        assertEquals(NOW, result.decidedAt());
        assertEquals("Perfil verificado", result.decisionReason());

        verify(users).save(argThat(u -> u.id().equals(customerId) && u.role() == Role.PROVIDER));
        verify(providerRequests).save(argThat(r -> r.id().equals(pending.id()) && r.status() == ProviderRequestStatus.APPROVED));

        String logMsg = auditLog.list.get(0).getFormattedMessage();
        assertTrue(logMsg.contains("outcome=SUCCESS"));
        assertTrue(logMsg.contains("action=PROVIDER_REQUEST_APPROVED"));
    }

    @Test
    void admin_no_puede_aprobar_su_propia_solicitud() {
        ProviderRequest own = ProviderRequest.create(adminId, "Mentoría", NOW.minusSeconds(100));
        when(providerRequests.findById(own.id())).thenReturn(Optional.of(own));

        assertThrows(BusinessRuleException.class,
            () -> service.approve(adminActor, own.id(), "Aprobado"));
        verify(users, never()).save(any());
    }

    @Test
    void no_se_puede_aprobar_solicitud_ya_resuelta() {
        ProviderRequest pending = ProviderRequest.create(customerId, "Mentoría", NOW.minusSeconds(3600));
        ProviderRequest alreadyApproved = pending.approve(adminId, "Ya aprobado", NOW.minusSeconds(1800));
        when(providerRequests.findById(alreadyApproved.id())).thenReturn(Optional.of(alreadyApproved));

        assertThrows(BusinessRuleException.class,
            () -> service.approve(adminActor, alreadyApproved.id(), "Otra vez"));
    }

    // ---------- Rechazo de solicitud ----------

    @Test
    void admin_rechaza_solicitud_y_mantiene_rol_customer() {
        ProviderRequest pending = ProviderRequest.create(customerId, "Mentoría", NOW.minusSeconds(3600));
        when(providerRequests.findById(pending.id())).thenReturn(Optional.of(pending));

        ProviderRequest result = service.reject(adminActor, pending.id(), "Documentos incompletos");

        assertEquals(ProviderRequestStatus.REJECTED, result.status());
        assertEquals(adminId, result.decidedBy());
        assertEquals(NOW, result.decidedAt());
        assertEquals("Documentos incompletos", result.decisionReason());

        verify(users, never()).save(any());
        verify(providerRequests).save(argThat(r -> r.id().equals(pending.id()) && r.status() == ProviderRequestStatus.REJECTED));

        String logMsg = auditLog.list.get(0).getFormattedMessage();
        assertTrue(logMsg.contains("outcome=SUCCESS"));
        assertTrue(logMsg.contains("action=PROVIDER_REQUEST_REJECTED"));
    }

    @Test
    void no_admin_no_puede_aprobar_ni_rechazar() {
        UUID reqId = UUID.randomUUID();
        assertThrows(ForbiddenOperationException.class,
            () -> service.approve(customerActor, reqId, "Ok"));
        assertThrows(ForbiddenOperationException.class,
            () -> service.reject(customerActor, reqId, "No"));
        verify(providerRequests, never()).save(any());
    }
}
