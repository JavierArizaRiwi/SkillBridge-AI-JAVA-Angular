package com.riwi.skillbridge.application.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.riwi.skillbridge.application.port.out.UserAdminPort;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.exception.ForbiddenOperationException;
import com.riwi.skillbridge.domain.model.Actor;
import com.riwi.skillbridge.domain.model.PageQuery;
import com.riwi.skillbridge.domain.model.PageResult;
import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.domain.model.UserAccount;
import com.riwi.skillbridge.domain.model.UserStatus;
import com.riwi.skillbridge.domain.policy.UserManagementPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserAdminServiceTest {

    private final UserAdminPort users = mock(UserAdminPort.class);
    private final UserAdminService service = new UserAdminService(users, new UserManagementPolicy());

    private final Actor admin = new Actor(UUID.randomUUID(), Role.ADMIN);
    private final Actor customerActor = new Actor(UUID.randomUUID(), Role.CUSTOMER);
    private final Actor providerActor = new Actor(UUID.randomUUID(), Role.PROVIDER);
    private final UserAccount customer =
        new UserAccount(UUID.randomUUID(), "Ana", "ana@test.com", "secret-hash", Role.CUSTOMER);

    private ListAppender<ILoggingEvent> auditLog;

    @BeforeEach
    void setUp() {
        when(users.findById(customer.id())).thenReturn(Optional.of(customer));
        when(users.save(any())).thenAnswer(i -> i.getArgument(0));

        auditLog = new ListAppender<>();
        auditLog.start();
        ((Logger) LoggerFactory.getLogger("AUDIT")).addAppender(auditLog);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger("AUDIT")).detachAppender(auditLog);
    }

    /** El Admin como usuario objetivo de sí mismo. */
    private UserAccount adminAccount() {
        UserAccount account = new UserAccount(admin.id(), "Root", "root@test.com", "hash", Role.ADMIN);
        when(users.findById(admin.id())).thenReturn(Optional.of(account));
        return account;
    }

    // ---------- Listar ----------

    @Test
    void admin_lista_usuarios_paginados() {
        PageQuery query = new PageQuery(0, 20, "createdAt", true);
        PageResult<UserAccount> page = new PageResult<>(List.of(customer), 0, 20, 1, 1);
        when(users.findAll(query)).thenReturn(page);

        assertSame(page, service.listUsers(admin, query));
    }

    @Test
    void customer_y_provider_no_pueden_listar() {
        PageQuery query = new PageQuery(0, 20, "createdAt", true);

        assertThrows(ForbiddenOperationException.class, () -> service.listUsers(customerActor, query));
        assertThrows(ForbiddenOperationException.class, () -> service.listUsers(providerActor, query));
        verify(users, never()).findAll(any());
    }

    @Test
    void orden_por_campo_no_permitido_es_rechazado() {
        PageQuery query = new PageQuery(0, 10, "passwordHash", false);

        assertThrows(IllegalArgumentException.class, () -> service.listUsers(admin, query));
        verify(users, never()).findAll(any());
    }

    // ---------- Detalle ----------

    @Test
    void admin_ve_el_detalle_de_un_usuario() {
        assertEquals(customer, service.getUser(admin, customer.id()));
    }

    @Test
    void detalle_de_usuario_inexistente_es_404() {
        assertThrows(DomainNotFoundException.class, () -> service.getUser(admin, UUID.randomUUID()));
    }

    @Test
    void un_no_admin_no_puede_ver_detalle_ni_averiguar_si_existe() {
        assertThrows(ForbiddenOperationException.class, () -> service.getUser(customerActor, customer.id()));
        verify(users, never()).findById(any());
    }

    // ---------- Cambio de estado ----------

    @Test
    void admin_suspende_a_otro_usuario() {
        UserAccount result = service.changeStatus(admin, customer.id(), UserStatus.SUSPENDED);

        assertEquals(UserStatus.SUSPENDED, result.status());
        verify(users).save(argThat(u -> u.id().equals(customer.id()) && u.status() == UserStatus.SUSPENDED));
    }

    @Test
    void admin_no_puede_cambiar_su_propio_estado() {
        adminAccount();

        assertThrows(BusinessRuleException.class,
            () -> service.changeStatus(admin, admin.id(), UserStatus.SUSPENDED));
        verify(users, never()).save(any());
    }

    @Test
    void poner_el_mismo_estado_es_una_transicion_invalida() {
        assertThrows(BusinessRuleException.class,
            () -> service.changeStatus(admin, customer.id(), UserStatus.ACTIVE));
        verify(users, never()).save(any());
    }

    @Test
    void un_no_admin_no_puede_cambiar_estado_ni_sondear_ids() {
        assertThrows(ForbiddenOperationException.class,
            () -> service.changeStatus(customerActor, customer.id(), UserStatus.SUSPENDED));
        verify(users, never()).findById(any());
        verify(users, never()).save(any());
    }

    @Test
    void cambiar_estado_de_usuario_inexistente_es_404() {
        assertThrows(DomainNotFoundException.class,
            () -> service.changeStatus(admin, UUID.randomUUID(), UserStatus.SUSPENDED));
    }

    // ---------- Cambio de rol ----------

    @Test
    void admin_promueve_un_customer_a_provider() {
        UserAccount result = service.changeRole(admin, customer.id(), Role.PROVIDER);

        assertEquals(Role.PROVIDER, result.role());
        verify(users).save(argThat(u -> u.id().equals(customer.id()) && u.role() == Role.PROVIDER));
    }

    @Test
    void admin_no_puede_cambiar_su_propio_rol() {
        adminAccount();

        assertThrows(BusinessRuleException.class,
            () -> service.changeRole(admin, admin.id(), Role.CUSTOMER));
        verify(users, never()).save(any());
    }

    @Test
    void poner_el_mismo_rol_es_una_transicion_invalida() {
        assertThrows(BusinessRuleException.class,
            () -> service.changeRole(admin, customer.id(), Role.CUSTOMER));
    }

    @Test
    void provider_no_puede_cambiar_roles() {
        assertThrows(ForbiddenOperationException.class,
            () -> service.changeRole(providerActor, customer.id(), Role.ADMIN));
        verify(users, never()).save(any());
    }

    // ---------- Auditoría ----------

    @Test
    void la_auditoria_registra_el_exito_sin_datos_personales() {
        service.changeStatus(admin, customer.id(), UserStatus.SUSPENDED);

        String line = auditLog.list.get(0).getFormattedMessage();
        assertTrue(line.contains("outcome=SUCCESS"));
        assertTrue(line.contains(admin.id().toString()));
        assertTrue(line.contains(customer.id().toString()));
        assertFalse(line.contains("@"));
        assertFalse(line.contains("secret-hash"));
        assertFalse(line.contains("Ana"));
    }

    @Test
    void la_auditoria_registra_el_rechazo_con_su_motivo() {
        adminAccount();

        assertThrows(BusinessRuleException.class,
            () -> service.changeStatus(admin, admin.id(), UserStatus.SUSPENDED));

        String line = auditLog.list.get(0).getFormattedMessage();
        assertTrue(line.contains("outcome=REJECTED"));
        assertTrue(line.contains("reason=BusinessRuleException"));
    }
}
