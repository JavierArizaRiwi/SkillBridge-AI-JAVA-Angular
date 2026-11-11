package com.riwi.skillbridge.domain.policy;

import com.riwi.skillbridge.domain.exception.ForbiddenOperationException;
import com.riwi.skillbridge.domain.model.Actor;
import com.riwi.skillbridge.domain.model.Offering;

public class OfferingAccessPolicy {

    public boolean canModify(Actor actor, Offering offering) {
        return switch (actor.role()) {
            case ADMIN    -> true;
            case PROVIDER -> offering.isOwnedBy(actor.id());
            case CUSTOMER -> false;
        };
    }

    public void assertCanModify(Actor actor, Offering offering) {
        if (!canModify(actor, offering)) {
            throw new ForbiddenOperationException("No tienes permiso para modificar este offering");
        }
    }
}
