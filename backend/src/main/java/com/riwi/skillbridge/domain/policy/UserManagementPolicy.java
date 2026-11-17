package com.riwi.skillbridge.domain.policy;

import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.ForbiddenOperationException;
import com.riwi.skillbridge.domain.model.Actor;
import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.domain.model.UserAccount;
import com.riwi.skillbridge.domain.model.UserStatus;

public class UserManagementPolicy {

    public void assertAdmin(Actor actor) {
        if (actor.role() != Role.ADMIN) {
            throw new ForbiddenOperationException("Solo un Admin puede gestionar usuarios");
        }
    }

    public void assertCanChangeStatus(Actor actor, UserAccount target, UserStatus newStatus) {
        assertAdminActingOnAnotherUser(actor, target);
        if (newStatus == null) {
            throw new IllegalArgumentException("El estado es obligatorio");
        }
        if (target.status() == newStatus) {
            throw new BusinessRuleException("La cuenta ya está en estado " + newStatus);
        }
    }

    public void assertCanChangeRole(Actor actor, UserAccount target, Role newRole) {
        assertAdminActingOnAnotherUser(actor, target);
        if (newRole == null) {
            throw new IllegalArgumentException("El rol es obligatorio");
        }
        if (target.role() == newRole) {
            throw new BusinessRuleException("El usuario ya tiene el rol " + newRole);
        }
    }

    // Como quien actúa es siempre un Admin activo y no puede tocarse a sí mismo,
    // el sistema nunca puede quedarse sin Admin activo por esta vía.
    private void assertAdminActingOnAnotherUser(Actor actor, UserAccount target) {
        assertAdmin(actor);
        if (actor.id().equals(target.id())) {
            throw new BusinessRuleException("Un Admin no puede modificar su propia cuenta");
        }
    }
}
