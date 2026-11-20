package com.riwi.skillbridge.domain.policy;

import com.riwi.skillbridge.domain.exception.ForbiddenOperationException;
import com.riwi.skillbridge.domain.model.Actor;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.Role;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfferingAccessPolicyTest {

    private final OfferingAccessPolicy policy = new OfferingAccessPolicy();
    private final UUID ownerId = UUID.randomUUID();
    private final Offering offering =
        Offering.create(ownerId, "Java", "desc", "BACKEND", BigDecimal.TEN);

    @Test
    void provider_puede_modificar_su_offering() {
        Actor dueno = new Actor(ownerId, Role.PROVIDER);

        assertTrue(policy.canModify(dueno, offering));
        assertDoesNotThrow(() -> policy.assertCanModify(dueno, offering));
    }

    @Test
    void provider_no_puede_modificar_offering_ajeno() {
        Actor otro = new Actor(UUID.randomUUID(), Role.PROVIDER);

        assertFalse(policy.canModify(otro, offering));
        assertThrows(ForbiddenOperationException.class, () -> policy.assertCanModify(otro, offering));
    }

    @Test
    void admin_puede_modificar_cualquier_offering() {
        Actor admin = new Actor(UUID.randomUUID(), Role.ADMIN);

        assertTrue(policy.canModify(admin, offering));
        assertDoesNotThrow(() -> policy.assertCanModify(admin, offering));
    }

    @Test
    void customer_no_puede_modificar_aunque_use_el_id_del_dueno() {
        Actor customer = new Actor(ownerId, Role.CUSTOMER);

        assertFalse(policy.canModify(customer, offering));
        assertThrows(ForbiddenOperationException.class, () -> policy.assertCanModify(customer, offering));
    }
}
