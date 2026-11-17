package com.riwi.skillbridge.domain.policy;

import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.ForbiddenOperationException;
import com.riwi.skillbridge.domain.model.Actor;
import com.riwi.skillbridge.domain.model.ProviderRequest;
import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.domain.model.UserAccount;
import com.riwi.skillbridge.domain.model.UserStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProviderRequestPolicyTest {

    private final ProviderRequestPolicy policy = new ProviderRequestPolicy();

    private final Actor customerActor = new Actor(UUID.randomUUID(), Role.CUSTOMER);
    private final Actor providerActor = new Actor(UUID.randomUUID(), Role.PROVIDER);
    private final Actor adminActor = new Actor(UUID.randomUUID(), Role.ADMIN);

    private UserAccount accountOf(Actor actor, Role role) {
        return new UserAccount(actor.id(), "Nombre", "mail@test.com", "hash", role);
    }

    private ProviderRequest requestOf(UUID userId) {
        return ProviderRequest.create(userId, "Quiero ser Provider", Instant.parse("2026-10-21T12:00:00Z"));
    }

    // ---------- Solicitar ----------

    @Test
    void un_customer_activo_puede_solicitar_para_si_mismo() {
        assertDoesNotThrow(() -> policy.assertCanRequest(customerActor, accountOf(customerActor, Role.CUSTOMER)));
    }

    @Test
    void provider_y_admin_no_pueden_solicitar() {
        assertThrows(ForbiddenOperationException.class,
            () -> policy.assertCanRequest(providerActor, accountOf(providerActor, Role.PROVIDER)));
        assertThrows(ForbiddenOperationException.class,
            () -> policy.assertCanRequest(adminActor, accountOf(adminActor, Role.ADMIN)));
    }

    @Test
    void nadie_puede_solicitar_en_nombre_de_otro_usuario() {
        UserAccount otro = new UserAccount(UUID.randomUUID(), "Otro", "otro@test.com", "hash", Role.CUSTOMER);

        assertThrows(ForbiddenOperationException.class, () -> policy.assertCanRequest(customerActor, otro));
    }

    @Test
    void una_cuenta_que_ya_es_provider_no_puede_solicitar() {
        assertThrows(BusinessRuleException.class,
            () -> policy.assertCanRequest(customerActor, accountOf(customerActor, Role.PROVIDER)));
    }

    @Test
    void una_cuenta_suspendida_no_puede_solicitar() {
        UserAccount suspendida = accountOf(customerActor, Role.CUSTOMER).withStatus(UserStatus.SUSPENDED);

        assertThrows(BusinessRuleException.class, () -> policy.assertCanRequest(customerActor, suspendida));
    }

    // ---------- Decidir ----------

    @Test
    void un_admin_puede_decidir_la_solicitud_de_otro_usuario() {
        assertDoesNotThrow(() -> policy.assertCanDecide(adminActor, requestOf(customerActor.id())));
    }

    @Test
    void customer_y_provider_no_pueden_decidir_ni_siquiera_su_propia_solicitud() {
        assertThrows(ForbiddenOperationException.class,
            () -> policy.assertCanDecide(customerActor, requestOf(customerActor.id())));
        assertThrows(ForbiddenOperationException.class,
            () -> policy.assertCanDecide(providerActor, requestOf(UUID.randomUUID())));
    }

    @Test
    void un_admin_no_puede_resolver_su_propia_solicitud() {
        assertThrows(BusinessRuleException.class,
            () -> policy.assertCanDecide(adminActor, requestOf(adminActor.id())));
    }

    // ---------- Aprobar ----------

    @Test
    void se_puede_aprobar_a_un_customer_activo() {
        assertDoesNotThrow(() -> policy.assertCanApprove(accountOf(customerActor, Role.CUSTOMER)));
    }

    @Test
    void no_se_aprueba_si_el_solicitante_ya_no_es_customer() {
        assertThrows(BusinessRuleException.class,
            () -> policy.assertCanApprove(accountOf(customerActor, Role.PROVIDER)));
        assertThrows(BusinessRuleException.class,
            () -> policy.assertCanApprove(accountOf(customerActor, Role.ADMIN)));
    }

    @Test
    void no_se_aprueba_si_la_cuenta_del_solicitante_esta_suspendida() {
        UserAccount suspendida = accountOf(customerActor, Role.CUSTOMER).withStatus(UserStatus.SUSPENDED);

        assertThrows(BusinessRuleException.class, () -> policy.assertCanApprove(suspendida));
    }

    // ---------- Listar ----------

    @Test
    void solo_un_admin_puede_listar_solicitudes() {
        assertDoesNotThrow(() -> policy.assertAdmin(adminActor));
        assertThrows(ForbiddenOperationException.class, () -> policy.assertAdmin(customerActor));
        assertThrows(ForbiddenOperationException.class, () -> policy.assertAdmin(providerActor));
    }
}
