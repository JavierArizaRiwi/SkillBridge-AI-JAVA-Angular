package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProviderRequestDto(
    @NotBlank(message = "El motivo de la solicitud es obligatorio")
    @Size(max = 500, message = "El motivo no puede superar 500 caracteres")
    String message
) {}
