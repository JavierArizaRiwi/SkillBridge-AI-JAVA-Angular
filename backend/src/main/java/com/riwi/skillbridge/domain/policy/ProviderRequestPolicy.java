package com.riwi.skillbridge.domain.policy;

import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.ForbiddenOperationException;
import com.riwi.skillbridge.domain.model.Actor;
import com.riwi.skillbridge.domain.model.ProviderRequest;
import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.domain.model.UserAccount;
import com.riwi.skillbridge.domain.model.UserStatus;

public class ProviderRequestPolicy {

    public void assertAdmin(Actor actor) {
        if (actor.role() != Role.ADMIN) {
            throw new ForbiddenOperationException("Solo un Admin puede gestionar solicitudes de Provider");
        }
    }

    /** Un Customer activo solicita para sí mismo; nadie solicita en nombre de otro. */
    public void assertCanRequest(Actor actor, UserAccount user) {
        if (actor.role() != Role.CUSTOMER) {
            throw new ForbiddenOperationException("Solo un Customer puede solicitar ser Provider");
        }
        if (!actor.id().equals(user.id())) {
            throw new ForbiddenOperationException("No puedes solicitar en nombre de otro usuario");
        }
        if (user.role() != Role.CUSTOMER) {
            throw new BusinessRuleException("Tu cuenta ya tiene capacidades de Provider o superiores");
        }
        if (user.status() == UserStatus.SUSPENDED) {
            throw new BusinessRuleException("La cuenta está suspendida");
        }
    }

    /** Solo un Admin decide, y nunca sobre su propia solicitud. */
    public void assertCanDecide(Actor actor, ProviderRequest request) {
        assertAdmin(actor);
        if (actor.id().equals(request.userId())) {
            throw new BusinessRuleException("No puedes resolver tu propia solicitud");
        }
    }

    /** Al aprobar, el solicitante debe seguir siendo un Customer activo. */
    public void assertCanApprove(UserAccount applicant) {
        if (applicant.role() != Role.CUSTOMER) {
            throw new BusinessRuleException("El solicitante ya no es Customer");
        }
        if (applicant.status() != UserStatus.ACTIVE) {
            throw new BusinessRuleException("La cuenta del solicitante no está activa");
        }
    }
}
