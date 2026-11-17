package com.riwi.skillbridge.domain.event;

import com.riwi.skillbridge.domain.model.MentorshipMode;

import java.time.Instant;
import java.util.UUID;

/**
 * Pago aprobado. Contrato en docs/contracts/mentorships/events.md §2.
 *
 * <p>v2 transporta el estado completo necesario para aprovisionar una mentoría sin consultas
 * síncronas (Event-Carried State Transfer). {@code paymentId} puede ser nulo cuando la aprobación
 * llega por el webhook de Stripe (no hay UUID de pago propio). Si no se pudo reunir el estado completo,
 * se emite la forma mínima (v1: solo {@code bookingId}) para no bloquear el flujo del monolito.
 */
public record PaymentApproved(
    int version,
    UUID eventId,
    Instant occurredAt,
    String correlationId,
    UUID bookingId,
    UUID paymentId,
    UUID customerId,
    String customerEmail,
    UUID offeringId,
    String offeringTitle,
    UUID providerId,
    Instant scheduledAt,
    int durationHours,
    MentorshipMode mode
) {
    public static final int CURRENT_VERSION = 2;

    /** Forma mínima (v1): solo el id de la reserva. */
    public PaymentApproved(UUID bookingId) {
        this(1, null, null, null, bookingId, null, null, null, null, null, null, null, 0, null);
    }

    /** {@code true} si el evento trae todo lo que necesita el servicio de mentorías. Sin prefijo is/get a propósito: no debe serializarse como propiedad. */
    public boolean hasFullMentorshipState() {
        return version >= CURRENT_VERSION && customerId != null && customerEmail != null
            && offeringId != null && offeringTitle != null && providerId != null
            && scheduledAt != null && durationHours > 0 && mode != null;
    }
}
