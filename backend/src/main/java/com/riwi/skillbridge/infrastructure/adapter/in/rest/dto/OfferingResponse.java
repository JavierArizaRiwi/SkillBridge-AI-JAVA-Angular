package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import com.riwi.skillbridge.domain.model.Offering;
import java.math.BigDecimal;
import java.util.UUID;

public record OfferingResponse(UUID id, UUID providerId, String title, String description,
                               String category, BigDecimal price, boolean active) {
    public static OfferingResponse from(Offering o) {
        return new OfferingResponse(o.id(), o.providerId(), o.title(), o.description(),
            o.category(), o.price(), o.active());
    }
}
