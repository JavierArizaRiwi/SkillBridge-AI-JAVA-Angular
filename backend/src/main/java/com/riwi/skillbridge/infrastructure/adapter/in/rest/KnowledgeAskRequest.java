package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload de entrada para la consulta al asistente de conocimiento (HU-26, FR-002).
 */
public record KnowledgeAskRequest(
        @NotBlank(message = "La pregunta no puede estar vacía")
        @Size(max = 500, message = "La pregunta no puede superar los 500 caracteres")
        String question
) {}
