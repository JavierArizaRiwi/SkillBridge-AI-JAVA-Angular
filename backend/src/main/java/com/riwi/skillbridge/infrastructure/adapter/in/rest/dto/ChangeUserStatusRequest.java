package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import com.riwi.skillbridge.domain.model.UserStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeUserStatusRequest(@NotNull UserStatus status) {}
