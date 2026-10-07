package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.Actor;
import com.riwi.skillbridge.domain.model.Offering;
import java.math.BigDecimal;
import java.util.UUID;

public interface UpdateOfferingUseCase {
    Offering update(Actor actor, UUID offeringId, Command command);

    /** Sin providerId a propósito: el dueño no es editable. */
    record Command(String title, String description, String category, BigDecimal price) {}
}
