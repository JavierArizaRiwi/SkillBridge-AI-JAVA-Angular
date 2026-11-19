package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.Booking;

import java.util.List;

// Puerto de entrada para listar las reservas por id de usuario
public interface ListCustomerBookingsUseCase {
    List<Booking> bookingsList(String email);
}
