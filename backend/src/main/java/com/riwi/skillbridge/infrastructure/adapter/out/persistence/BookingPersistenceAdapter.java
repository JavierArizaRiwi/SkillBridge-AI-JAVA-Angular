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

    public BookingPersistenceAdapter(
        JpaBookingRepository repository,
        JpaUserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    @Override
    public Booking save(Booking booking) {
        BookingEntity saved = repository.findById(booking.id())
            .map(entity -> {
                entity.updateStatus(booking.status());
                return repository.saveAndFlush(entity);
            })
            .orElseGet(() -> repository.save(new BookingEntity(
                booking.id(),
                booking.offeringId(),
                booking.customerId(),
                booking.scheduledAt(),
                booking.status(),
                booking.version(),
                Instant.now()
            )));

        return toDomain(saved);
    }
    @Override
    public Booking save(Booking booking, String idempotencyKey, String requestHash) {
        return toDomain(repository.saveAndFlush(new BookingEntity(booking.id(), booking.offeringId(), booking.customerId(),
            booking.scheduledAt(), booking.status(), booking.version(), Instant.now(), idempotencyKey, requestHash)));
    }

    @Override
    public Optional<Booking> findByCustomerIdAndIdempotencyKey(UUID customerId, String idempotencyKey) {
        return repository.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey).map(this::toDomain);
    }

    @Override
    public Optional<String> findIdempotencyRequestHash(UUID customerId, String idempotencyKey) {
        return repository.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey)
            .map(BookingEntity::getIdempotencyRequestHash);
    }

    @Override
    public Optional<Booking> findById(UUID bookingId) {
        return repository.findById(bookingId).map(this::toDomain);
    }

    @Override
    public Optional<Booking> findByIdForCancellation(UUID bookingId) {
        return repository.findByIdForUpdate(bookingId).map(this::toDomain);
    }

    @Override
    public List<Booking> findByCustomerEmail(String email) {
        UserEntity user = userRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new DomainNotFoundException(
                "Usuario no encontrado con el email: " + email));

        return repository.findByCustomerIdOrderByScheduledAtDesc(user.getId())
            .stream()
            .map(this::toDomain)
            .toList();
    }

    private Booking toDomain(BookingEntity entity) {
        return new Booking(
            entity.getId(),
            entity.getOfferingId(),
            entity.getCustomerId(),
            entity.getScheduledAt(),
            entity.getStatus(),
            entity.getVersion()
        );
    }
}
