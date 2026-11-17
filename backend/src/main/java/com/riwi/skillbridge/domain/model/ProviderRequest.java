package com.riwi.skillbridge.domain.model;

import com.riwi.skillbridge.domain.exception.BusinessRuleException;

import java.time.Instant;
import java.util.UUID;

public record ProviderRequest(
    UUID id,
    UUID userId,
    ProviderRequestStatus status,
    String message,
    Instant createdAt,
    UUID decidedBy,
    Instant decidedAt,
    String decisionReason) {

    public static final int MAX_LENGTH = 500;

    /** Crea una solicitud nueva en estado PENDING. El motivo es obligatorio. */
    public static ProviderRequest create(UUID userId, String message, Instant now) {
        String clean = message == null ? "" : message.trim();
        if (clean.isEmpty()) {
            throw new IllegalArgumentException("El motivo de la solicitud es obligatorio");
        }
        if (clean.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("El motivo no puede superar " + MAX_LENGTH + " caracteres");
        }
        return new ProviderRequest(UUID.randomUUID(), userId, ProviderRequestStatus.PENDING,
            clean, now, null, null, null);
    }

    public boolean isPending() {
        return status == ProviderRequestStatus.PENDING;
    }

    public ProviderRequest approve(UUID adminId, String reason, Instant now) {
        return decide(ProviderRequestStatus.APPROVED, adminId, reason, now);
    }

    public ProviderRequest reject(UUID adminId, String reason, Instant now) {
        return decide(ProviderRequestStatus.REJECTED, adminId, reason, now);
    }

    private ProviderRequest decide(ProviderRequestStatus newStatus, UUID adminId, String reason, Instant now) {
        if (!isPending()) {
            throw new BusinessRuleException("La solicitud ya fue resuelta (" + status + ")");
        }
        String note = reason == null || reason.isBlank() ? null : reason.trim();
        if (note != null && note.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("La nota no puede superar " + MAX_LENGTH + " caracteres");
        }
        return new ProviderRequest(id, userId, newStatus, message, createdAt, adminId, now, note);
    }
}
