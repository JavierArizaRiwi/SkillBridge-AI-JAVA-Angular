package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import com.riwi.skillbridge.domain.model.Role;
import jakarta.validation.constraints.NotNull;

public record ChangeUserRoleRequest(@NotNull Role role) {}
