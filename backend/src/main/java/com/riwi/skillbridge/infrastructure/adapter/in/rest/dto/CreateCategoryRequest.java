package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCategoryRequest(
    @NotBlank(message = "name es obligatorio")
    @Size(max = 80, message = "name no puede superar 80 caracteres")
    String name
) {}
