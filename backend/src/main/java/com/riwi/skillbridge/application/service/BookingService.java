package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.CancelBookingCommand;
import com.riwi.skillbridge.application.port.in.CancelBookingUseCase;
import com.riwi.skillbridge.application.port.in.CreateBookingUseCase;
import com.riwi.skillbridge.application.port.in.ListCustomerBookingsUseCase;
import com.riwi.skillbridge.application.port.in.PaymentResultUseCase;
import com.riwi.skillbridge.application.port.out.*;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.exception.IdempotencyKeyConflictException;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.BookingStatus;
import com.riwi.skillbridge.domain.model.BookingStatusHistory;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.UserAccount;
import com.riwi.skillbridge.domain.service.BookingCancellationPolicy;
import org.springframework.stereotype.Service;

import com.riwi.skillbridge.application.port.out.event.BusinessEvent;
import com.riwi.skillbridge.application.port.out.event.BookingCreatedPayload;
import com.riwi.skillbridge.application.port.out.event.BookingCancelledPayload;
import com.riwi.skillbridge.application.common.CorrelationIdHolder;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
@RequiredArgsConstructor
public class BookingService implements CreateBookingUseCase, ListCustomerBookingsUseCase, CancelBookingUseCase, PaymentResultUseCase {
    private final BookingRepositoryPort bookingRepository;
    private final OfferingRepositoryPort offeringRepository;
    private final UserRepositoryPort userRepositoryPort;

    private final BookingCancellationPolicy cancellationPolicy;
    private final NotificationPublisherPort notificationPublisher;
    private final BookingStatusHistoryPort bookingStatusHistoryPort;
    private final Clock clock;
    private final BookingEventPublisherPort eventPublisher;

    @Override
    public Booking create(UUID offeringId, Instant scheduledAt, String customerEmail) {
        return createInternal(offeringId, scheduledAt, customerEmail, null);
    }

    @Override
    @Transactional
    public Booking create(UUID offeringId, Instant scheduledAt, String customerEmail, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 255) {
            throw new IllegalArgumentException("Idempotency-Key es obligatorio y debe tener máximo 255 caracteres");
        }
        return createInternal(offeringId, scheduledAt, customerEmail, idempotencyKey.trim());
    }

    private Booking createInternal(UUID offeringId, Instant scheduledAt, String customerEmail, String idempotencyKey) {
        if (idempotencyKey == null) {
            validateBookingCreation(offeringId, scheduledAt);
        }
        UserAccount customer = userRepositoryPort.findByEmail(customerEmail)
            .orElseThrow(() -> new DomainNotFoundException("Usuario no encontrado"));

        String requestHash = idempotencyKey == null ? null : requestHash(offeringId, scheduledAt);
        if (idempotencyKey != null) {
            var existing = bookingRepository.findByCustomerIdAndIdempotencyKey(customer.id(), idempotencyKey);
            if (existing.isPresent()) {
                String existingHash = bookingRepository.findIdempotencyRequestHash(customer.id(), idempotencyKey).orElse(null);
                if (!requestHash.equals(existingHash)) {
                    throw new IdempotencyKeyConflictException("Idempotency-Key ya fue usada con una solicitud diferente");
                }
                return existing.get();
            }
        }

        if (idempotencyKey != null) {
            validateBookingCreation(offeringId, scheduledAt);
        }

        Booking booking = new Booking(UUID.randomUUID(), offeringId, customer.id(), scheduledAt, BookingStatus.CREATED, 0);
        Booking saved = idempotencyKey == null ? bookingRepository.save(booking)
            : bookingRepository.save(booking, idempotencyKey, requestHash);
        notificationPublisher.publish(NotificationMessage.bookingCreated(saved.id(), saved.customerId()));
        String correlationId = CorrelationIdHolder.get() != null ? CorrelationIdHolder.get() : UUID.randomUUID().toString();
        BookingCreatedPayload payload = new BookingCreatedPayload(saved.id(), saved.offeringId(), saved.customerId(), saved.scheduledAt(), saved.status().name());
        BusinessEvent<BookingCreatedPayload> event = new BusinessEvent<>(UUID.randomUUID(), "BookingCreated", saved.id().toString(), "Booking", Instant.now(), correlationId, 1, payload, customer.id().toString(), customer.email(), customer.role().name(), "CREATE", "BOOKING", saved.id().toString());
        eventPublisher.publish(event);
        return saved;
    }

    private void validateBookingCreation(UUID offeringId, Instant scheduledAt) {
        if (scheduledAt.isBefore(Instant.now())) {
            throw new BusinessRuleException("La reserva debe programarse en una fecha futura");
        }
        Offering offering = offeringRepository.findById(offeringId)
            .orElseThrow(() -> new DomainNotFoundException("Servicio no encontrado"));
        if (!offering.active()) {
            throw new BusinessRuleException("El servicio no estǭ activo");
        }
    }

    private String requestHash(UUID offeringId, Instant scheduledAt) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest((offeringId + "|" + scheduledAt).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest) result.append(String.format("%02x", value & 0xff));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }

    @Override
    public List<Booking> bookingsList(String email) {
        return bookingRepository.findByCustomerEmail(email);
    }

    @Override
    @Transactional
    public Booking cancel(CancelBookingCommand command) {
        Booking booking = bookingRepository.findByIdForCancellation(command.bookingId())
                .orElseThrow(() -> new DomainNotFoundException("Reserva no encontrada"));

        UserAccount customer = userRepositoryPort.findByEmail(command.customerEmail())
                .orElseThrow(() -> new DomainNotFoundException("Reserva no encontrada"));
        if (!booking.customerId().equals(customer.id())) {
            throw new DomainNotFoundException("Reserva no encontrada");
        }
        
        Booking cancelled = booking.cancel();
        if (cancelled == booking) {
            return booking;
        }

        cancellationPolicy.validate(booking);
        Booking saved = bookingRepository.save(cancelled);
        bookingStatusHistoryPort.save(BookingStatusHistory.forTransition(
                booking,
                saved.status(),
                customer.id(),
                clock.instant()));
        notificationPublisher.publish(NotificationMessage.bookingCancelled(saved.id(), saved.customerId()));
        String correlationId = CorrelationIdHolder.get() != null ? CorrelationIdHolder.get() : UUID.randomUUID().toString();
        BookingCancelledPayload payload = new BookingCancelledPayload(saved.id(), saved.customerId(), saved.status().name());
        BusinessEvent<BookingCancelledPayload> event = new BusinessEvent<>(
            UUID.randomUUID(), "BookingCancelled", saved.id().toString(), "Booking", Instant.now(), correlationId, 1, payload,
            customer.id().toString(), customer.email(), customer.role().name(), "CANCEL", "BOOKING", saved.id().toString()
        );
        eventPublisher.publish(event);
        return saved;
    }


    @Override
    public void processPaymentApproved(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
            .orElseThrow(() -> new DomainNotFoundException("Reserva no encontrada"));

        if (booking.status() != BookingStatus.CREATED) {
            throw new BusinessRuleException("La reserva no esta en estado CREATED");
        }

        Booking confirmedBooking = new Booking(
            booking.id(),
            booking.offeringId(),
            booking.customerId(),
            booking.scheduledAt(),
            BookingStatus.CONFIRMED,
            booking.version()
        );

        bookingRepository.save(confirmedBooking);
    }

    @Override
    public void processPaymentRejected(UUID bookingId, String reason) {
        Booking booking = bookingRepository.findById(bookingId)
            .orElseThrow(() -> new DomainNotFoundException("Reserva no encontrada"));

        // No alteramos el estado de la reserva, la mantenemos en CREATED
        // para permitir que el usuario pueda intentar realizar el pago nuevamente.
    }
}

