package com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository;

import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.BookingEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaBookingRepository extends JpaRepository<BookingEntity, UUID> {
    // metodo para buscar reservas por id de usuario
    List<BookingEntity> findByCustomerIdOrderByScheduledAtDesc(UUID customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select booking from BookingEntity booking where booking.id = :bookingId")
    Optional<BookingEntity> findByIdForUpdate(@Param("bookingId") UUID bookingId);
    Optional<BookingEntity> findByCustomerIdAndIdempotencyKey(UUID customerId, String idempotencyKey);
}
