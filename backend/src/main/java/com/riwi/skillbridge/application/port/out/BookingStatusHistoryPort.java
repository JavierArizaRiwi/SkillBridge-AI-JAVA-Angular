package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.model.BookingStatusHistory;

public interface BookingStatusHistoryPort {
    void save(BookingStatusHistory statusHistory);
}
