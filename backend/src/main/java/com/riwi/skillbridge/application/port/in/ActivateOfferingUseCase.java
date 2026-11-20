package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.Actor;
import java.util.UUID;

public interface ActivateOfferingUseCase {
    void activate(Actor actor, UUID offeringId);
}
