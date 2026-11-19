package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.application.port.out.BookingStatusHistoryPort;
import com.riwi.skillbridge.domain.model.BookingStatusHistory;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.BookingStatusHistoryEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaBookingStatusHistoryRepository;
import org.springframework.stereotype.Component;

@Component
public class BookingStatusHistoryPersistenceAdapter implements BookingStatusHistoryPort {
    private final JpaBookingStatusHistoryRepository repository;

    public BookingStatusHistoryPersistenceAdapter(JpaBookingStatusHistoryRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(BookingStatusHistory statusHistory) {
        repository.save(new BookingStatusHistoryEntity(
                statusHistory.id(),
                statusHistory.bookingId(),
                statusHistory.previousStatus(),
                statusHistory.newStatus(),
                statusHistory.changedBy(),
                statusHistory.changedAt()));
    }
}
