package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateOfferingRequest(
    UUID providerId,   // solo lo respeta el Admin; para un Provider se ignora
    @NotBlank String title,
    @NotBlank String description,
    @NotBlank String category,
    @NotNull @PositiveOrZero BigDecimal price) {}
