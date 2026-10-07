package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.application.port.out.BookingRepositoryPort;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.BookingEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.UserEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaBookingRepository;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaUserRepository;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class BookingPersistenceAdapter implements BookingRepositoryPort {
    private final JpaBookingRepository repository;
    private final JpaUserRepository userRepository;

    public BookingPersistenceAdapter(JpaBookingRepository repository, JpaUserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    @Override
    public Booking save(Booking booking) {
        BookingEntity entity = new BookingEntity(
                booking.id(), booking.offeringId(), booking.customerId(), booking.scheduledAt(), booking.status(), Instant.now());
        BookingEntity saved = repository.save(entity);
        return new Booking(saved.getId(), saved.getOfferingId(), saved.getCustomerId(), saved.getScheduledAt(), saved.getStatus());
    }

    @Override
    public Optional<Booking> findById(UUID bookingId) {
        return repository.findById(bookingId)
                .map(entity -> new Booking(
                        entity.getId(),
                        entity.getOfferingId(),
                        entity.getCustomerId(),
                        entity.getScheduledAt(),
                        entity.getStatus()));
    }

    @Override
    public List<Booking> findByCustomerEmail(String email) {
        UserEntity user = userRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new DomainNotFoundException("Usuario no encontrado con el email: " + email));

        return repository.findByCustomerIdOrderByScheduledAtDesc(user.getId())
            .stream()
            .map(entity -> new Booking(
                entity.getId(),
                entity.getOfferingId(),
                entity.getCustomerId(),
                entity.getScheduledAt(),
                entity.getStatus()
            ))
            .toList();
    }
}
