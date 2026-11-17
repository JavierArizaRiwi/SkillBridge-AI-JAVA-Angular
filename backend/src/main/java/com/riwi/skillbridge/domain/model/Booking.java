package com.riwi.skillbridge.domain.model;

import com.riwi.skillbridge.domain.exception.BusinessRuleException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record Booking(
        UUID id,
        UUID offeringId,
        UUID customerId,
        Instant scheduledAt,
        BookingStatus status,
        long version,
        Instant lastPaymentReminderSentAt
) {
    public Booking(UUID id, UUID offeringId, UUID customerId, Instant scheduledAt, BookingStatus status, long version) {
        this(id, offeringId, customerId, scheduledAt, status, version, null);
    }

    public Booking cancel() {
        if (status == BookingStatus.CANCELLED) {
            return this;
        }
        if (status == BookingStatus.COMPLETED) {
            throw new BusinessRuleException("No se pueden cancelar reservas completadas");
        }
        return new Booking(id, offeringId, customerId, scheduledAt, BookingStatus.CANCELLED, version, lastPaymentReminderSentAt);
    }

    /**
     * Fecha límite para pagar: exactamente 12 horas antes del inicio de la sesión.
     */
    public Instant paymentDeadline() {
        return scheduledAt.minus(Duration.ofHours(12));
    }

    /**
     * Determina si la ventana de pago ha expirado.
     * Una reserva en estado CREATED no puede pagarse si faltan menos de 12 horas para la sesión o si la fecha ya pasó.
     */
    public boolean isPaymentWindowExpired(Instant now) {
        return status == BookingStatus.CREATED && !now.isBefore(paymentDeadline());
    }

    /**
     * Determina si la reserva requiere un recordatorio de pago diario.
     * Requisitos:
     * 1. Estado CREATED (pendiente de pago).
     * 2. El plazo de pago aún no ha vencido (now < paymentDeadline).
     * 3. Faltan 7 días o menos para el vencimiento (now >= paymentDeadline - 7 días).
     * 4. No se ha enviado un recordatorio hoy (en las últimas 24 horas).
     */
    public boolean isEligibleForPaymentReminder(Instant now) {
        if (status != BookingStatus.CREATED) {
            return false;
        }
        Instant deadline = paymentDeadline();
        if (!now.isBefore(deadline)) {
            return false;
        }
        Instant reminderStart = deadline.minus(Duration.ofDays(7));
        if (now.isBefore(reminderStart)) {
            return false;
        }
        if (lastPaymentReminderSentAt != null) {
            Duration elapsed = Duration.between(lastPaymentReminderSentAt, now);
            if (!elapsed.isNegative() && elapsed.toHours() < 24) {
                return false;
            }
        }
        return true;
    }

    public Booking withLastPaymentReminderSentAt(Instant reminderSentAt) {
        return new Booking(id, offeringId, customerId, scheduledAt, status, version, reminderSentAt);
    }
}
