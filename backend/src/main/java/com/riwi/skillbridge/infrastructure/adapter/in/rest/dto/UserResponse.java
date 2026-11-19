package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.domain.model.UserAccount;
import com.riwi.skillbridge.domain.model.UserStatus;

import java.util.UUID;

/** Sin passwordHash a propósito: nunca se expone. */
public record UserResponse(UUID id, String name, String email, Role role, UserStatus status) {
    public static UserResponse from(UserAccount u) {
        return new UserResponse(u.id(), u.name(), u.email(), u.role(), u.status());
    }
}
