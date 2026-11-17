package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;
import jakarta.validation.constraints.*;
public record ProgressRequest(@Min(0) @Max(100) int progressPercent) {}
