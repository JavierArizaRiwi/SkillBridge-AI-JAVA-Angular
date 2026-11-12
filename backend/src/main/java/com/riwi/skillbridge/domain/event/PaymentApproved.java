package com.riwi.skillbridge.domain.event;

import java.util.UUID;

public record PaymentApproved(
    UUID bookingId
) {}
