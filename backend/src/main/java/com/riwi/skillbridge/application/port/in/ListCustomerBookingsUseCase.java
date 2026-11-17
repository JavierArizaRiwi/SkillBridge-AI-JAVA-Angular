package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.Booking;

import com.riwi.skillbridge.domain.model.PageQuery;
import com.riwi.skillbridge.domain.model.PageResult;

import java.util.List;

// Puerto de entrada para listar las reservas por id de usuario
public interface ListCustomerBookingsUseCase {
    List<Booking> bookingsList(String email);

    PageResult<Booking> bookingsList(String email, PageQuery query);
}
