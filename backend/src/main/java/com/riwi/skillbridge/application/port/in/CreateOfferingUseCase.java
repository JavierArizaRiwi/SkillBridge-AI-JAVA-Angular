package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.Actor;
import com.riwi.skillbridge.domain.model.Offering;
import java.math.BigDecimal;
import java.util.UUID;

public interface CreateOfferingUseCase {
    Offering create(Actor actor, Command command);

    /** providerId solo lo usa el Admin; para un Provider se ignora. */
    record Command(UUID providerId, String title, String description,
                   String category, BigDecimal price) {}
}
