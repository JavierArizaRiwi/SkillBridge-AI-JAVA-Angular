package com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository;

import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.BookingStatusHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaBookingStatusHistoryRepository extends JpaRepository<BookingStatusHistoryEntity, UUID> {
}
