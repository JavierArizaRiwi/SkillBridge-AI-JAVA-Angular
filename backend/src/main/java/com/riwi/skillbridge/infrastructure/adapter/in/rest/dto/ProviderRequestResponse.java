package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import com.riwi.skillbridge.domain.model.ProviderRequest;
import com.riwi.skillbridge.domain.model.ProviderRequestStatus;
import com.riwi.skillbridge.domain.model.UserAccount;

import java.time.Instant;
import java.util.UUID;

public record ProviderRequestResponse(
    UUID id,
    UUID userId,
    String userName,
    String userEmail,
    ProviderRequestStatus status,
    String message,
    Instant createdAt,
    UUID decidedBy,
    Instant decidedAt,
    String decisionReason
) {
    public static ProviderRequestResponse from(ProviderRequest r) {
        return new ProviderRequestResponse(
            r.id(),
            r.userId(),
            null,
            null,
            r.status(),
            r.message(),
            r.createdAt(),
            r.decidedBy(),
            r.decidedAt(),
            r.decisionReason()
        );
    }

    public static ProviderRequestResponse from(ProviderRequest r, UserAccount user) {
        return new ProviderRequestResponse(
            r.id(),
            r.userId(),
            user != null ? user.name() : null,
            user != null ? user.email() : null,
            r.status(),
            r.message(),
            r.createdAt(),
            r.decidedBy(),
            r.decidedAt(),
            r.decisionReason()
        );
    }
}
