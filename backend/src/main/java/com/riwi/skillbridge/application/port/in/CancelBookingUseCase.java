package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.Booking;

public interface CancelBookingUseCase {
    Booking cancel(CancelBookingCommand command);
}
