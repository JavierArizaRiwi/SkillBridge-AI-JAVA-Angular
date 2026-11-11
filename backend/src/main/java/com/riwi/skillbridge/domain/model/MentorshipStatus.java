package com.riwi.skillbridge.domain.model;

/** Estado individual de la mentoría; no sustituye el estado del pago ni el de la reserva. */
public enum MentorshipStatus {
    PENDING_PAYMENT, SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED, EXPIRED, NO_SHOW
}
