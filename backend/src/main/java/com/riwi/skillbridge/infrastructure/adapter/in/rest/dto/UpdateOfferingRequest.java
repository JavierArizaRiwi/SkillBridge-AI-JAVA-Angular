package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/** Sin providerId a propósito: el dueño no es editable. */
public record UpdateOfferingRequest(
    @NotBlank String title,
    @NotBlank String description,
    @NotBlank String category,
    @NotNull @PositiveOrZero BigDecimal price) {}
