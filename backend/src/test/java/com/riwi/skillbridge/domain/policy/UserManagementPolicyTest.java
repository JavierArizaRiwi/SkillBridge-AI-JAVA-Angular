package com.riwi.skillbridge.domain.policy;

import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.ForbiddenOperationException;
import com.riwi.skillbridge.domain.model.Actor;
import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.domain.model.UserAccount;
import com.riwi.skillbridge.domain.model.UserStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserManagementPolicyTest {

    private final UserManagementPolicy policy = new UserManagementPolicy();

    private final Actor admin = new Actor(UUID.randomUUID(), Role.ADMIN);
    private final UserAccount customer =
        new UserAccount(UUID.randomUUID(), "Ana", "ana@test.com", "hash", Role.CUSTOMER);
    private final UserAccount otherAdmin =
        new UserAccount(UUID.randomUUID(), "Root", "root@test.com", "hash", Role.ADMIN);

    // ---------- Autorización ----------

    @Test
    void customer_no_puede_cambiar_estado_ni_rol() {
        Actor actor = new Actor(UUID.randomUUID(), Role.CUSTOMER);

        assertThrows(ForbiddenOperationException.class,
            () -> policy.assertCanChangeStatus(actor, customer, UserStatus.SUSPENDED));
        assertThrows(ForbiddenOperationException.class,
            () -> policy.assertCanChangeRole(actor, customer, Role.PROVIDER));
    }

    @Test
    void provider_no_puede_cambiar_estado_ni_rol() {
        Actor actor = new Actor(UUID.randomUUID(), Role.PROVIDER);

        assertThrows(ForbiddenOperationException.class,
            () -> policy.assertCanChangeStatus(actor, customer, UserStatus.SUSPENDED));
        assertThrows(ForbiddenOperationException.class,
            () -> policy.assertCanChangeRole(actor, customer, Role.ADMIN));
    }

    // ---------- Admin sobre otro usuario ----------

    @Test
    void admin_puede_suspender_y_reactivar_a_otro_usuario() {
        assertDoesNotThrow(() -> policy.assertCanChangeStatus(admin, customer, UserStatus.SUSPENDED));

        UserAccount suspendido = customer.withStatus(UserStatus.SUSPENDED);
        assertDoesNotThrow(() -> policy.assertCanChangeStatus(admin, suspendido, UserStatus.ACTIVE));
    }

    @Test
    void admin_puede_cambiar_el_rol_de_otro_usuario() {
        assertDoesNotThrow(() -> policy.assertCanChangeRole(admin, customer, Role.PROVIDER));
        assertDoesNotThrow(() -> policy.assertCanChangeRole(admin, customer, Role.ADMIN));
        assertDoesNotThrow(() -> policy.assertCanChangeRole(admin, otherAdmin, Role.CUSTOMER));
    }

    // ---------- Un Admin no puede tocarse a sí mismo ----------

    @Test
    void admin_no_puede_cambiar_su_propio_estado() {
        UserAccount yo = new UserAccount(admin.id(), "Yo", "yo@test.com", "hash", Role.ADMIN);

        assertThrows(BusinessRuleException.class,
            () -> policy.assertCanChangeStatus(admin, yo, UserStatus.SUSPENDED));
    }

    @Test
    void admin_no_puede_cambiar_su_propio_rol() {
        UserAccount yo = new UserAccount(admin.id(), "Yo", "yo@test.com", "hash", Role.ADMIN);

        assertThrows(BusinessRuleException.class,
            () -> policy.assertCanChangeRole(admin, yo, Role.CUSTOMER));
    }

    // ---------- Transiciones inválidas ----------

    @Test
    void no_se_puede_poner_el_mismo_estado() {
        assertThrows(BusinessRuleException.class,
            () -> policy.assertCanChangeStatus(admin, customer, UserStatus.ACTIVE));
    }

    @Test
    void no_se_puede_poner_el_mismo_rol() {
        assertThrows(BusinessRuleException.class,
            () -> policy.assertCanChangeRole(admin, customer, Role.CUSTOMER));
    }

    @Test
    void estado_o_rol_nulos_son_solicitudes_invalidas() {
        assertThrows(IllegalArgumentException.class,
            () -> policy.assertCanChangeStatus(admin, customer, null));
        assertThrows(IllegalArgumentException.class,
            () -> policy.assertCanChangeRole(admin, customer, null));
    }

    // ---------- Métodos de cambio del modelo ----------

    @Test
    void withStatus_y_withRole_devuelven_una_copia_sin_alterar_el_resto() {
        UserAccount suspendido = customer.withStatus(UserStatus.SUSPENDED);
        UserAccount provider = customer.withRole(Role.PROVIDER);

        assertEquals(UserStatus.SUSPENDED, suspendido.status());
        assertEquals(customer.role(), suspendido.role());
        assertEquals(Role.PROVIDER, provider.role());
        assertEquals(customer.status(), provider.status());
        assertEquals(UserStatus.ACTIVE, customer.status()); // el original no cambia
    }
}
