package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.Size;

public record DecideProviderRequestDto(
    @Size(max = 500, message = "La nota no puede superar 500 caracteres")
    String reason
) {}
