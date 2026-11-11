package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TextToSpeechRequest(@NotBlank @Size(max = 2000) String text) {}
