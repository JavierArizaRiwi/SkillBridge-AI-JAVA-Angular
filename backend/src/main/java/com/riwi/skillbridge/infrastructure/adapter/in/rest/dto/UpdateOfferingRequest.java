package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import com.riwi.skillbridge.domain.model.MentorshipMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/** Sin providerId a propósito: el dueño no es editable. */
public record UpdateOfferingRequest(
    @NotBlank String title,
    @NotBlank String description,
    @NotBlank String category,
    @NotNull @PositiveOrZero BigDecimal price,
    MentorshipMode mode,               // opcional: si falta se conserva el actual
    @Min(1) @Max(24) Integer durationHours) {}  // opcional: si falta se conserva el actual
