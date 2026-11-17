package com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository;

import com.riwi.skillbridge.domain.model.BookingStatus;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.BookingEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaBookingRepository extends JpaRepository<BookingEntity, UUID> {
    // metodo para buscar reservas por id de usuario
    List<BookingEntity> findByCustomerIdOrderByScheduledAtDesc(UUID customerId);
    Page<BookingEntity> findByCustomerId(UUID customerId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select booking from BookingEntity booking where booking.id = :bookingId")
    Optional<BookingEntity> findByIdForUpdate(@Param("bookingId") UUID bookingId);
    Optional<BookingEntity> findByCustomerIdAndIdempotencyKey(UUID customerId, String idempotencyKey);
    List<BookingEntity> findByStatus(BookingStatus status);
}
