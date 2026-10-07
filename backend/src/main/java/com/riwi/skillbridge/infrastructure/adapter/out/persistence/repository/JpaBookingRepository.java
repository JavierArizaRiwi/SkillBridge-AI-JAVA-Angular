package com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository;

import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.BookingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaBookingRepository extends JpaRepository<BookingEntity, UUID> {
    // metodo para buscar reservas por id de usuario
    List<BookingEntity> findByCustomerIdOrderByScheduledAtDesc(UUID customerId);
}
