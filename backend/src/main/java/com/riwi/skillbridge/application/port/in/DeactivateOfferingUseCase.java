package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.Actor;
import java.util.UUID;

public interface DeactivateOfferingUseCase {
    void deactivate(Actor actor, UUID offeringId);
}
