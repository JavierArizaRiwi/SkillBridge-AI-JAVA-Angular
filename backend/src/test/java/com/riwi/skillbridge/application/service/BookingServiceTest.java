package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.CancelBookingCommand;

import com.riwi.skillbridge.application.port.out.BookingRepositoryPort;
import com.riwi.skillbridge.application.port.out.BookingEventPublisherPort;
import com.riwi.skillbridge.application.port.out.BookingStatusHistoryPort;
import com.riwi.skillbridge.application.port.out.NotificationMessage;
import com.riwi.skillbridge.application.port.out.NotificationPublisherPort;
import com.riwi.skillbridge.application.port.out.NotificationType;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.port.out.UserRepositoryPort;
import com.riwi.skillbridge.application.port.out.event.BusinessEvent;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.exception.IdempotencyKeyConflictException;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.BookingStatusHistory;
import com.riwi.skillbridge.domain.model.BookingStatus;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.domain.model.UserAccount;
import com.riwi.skillbridge.domain.service.BookingCancellationPolicy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BookingServiceTest {

    @Test
    void shouldPersistAndPublishBookingCreated() {
        BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
        OfferingRepositoryPort offerings = mock(OfferingRepositoryPort.class);
        UserRepositoryPort users = mock(UserRepositoryPort.class);
        
        NotificationPublisherPort notificationPublisher = mock(NotificationPublisherPort.class);
        BookingEventPublisherPort eventPublisher = mock(BookingEventPublisherPort.class);

        UUID offeringId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        UUID userId = UUID.randomUUID();

        Offering offering = new Offering(
            offeringId,
            UUID.randomUUID(),
            "Java",
            "Mentoría",
            "BACKEND",
            BigDecimal.TEN,
            true
        );
        when(offerings.findById(offeringId)).thenReturn(Optional.of(offering));
        when(users.findByEmail("user@example.com")).thenReturn(Optional.of(user(userId, "user@example.com")));
        when(bookings.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        BookingService service = service(
                bookings,
                offerings,
                users,
                notificationPublisher,
                mock(BookingStatusHistoryPort.class),
                eventPublisher,
                Instant.parse("2030-10-10T12:00:00Z")
        );

        Booking result = service.create(
                offeringId,
                Instant.now().plusSeconds(3600),
                "user@example.com"
        );

        assertEquals(offeringId, result.offeringId());
        assertEquals(userId, result.customerId());
        verify(bookings).save(any(Booking.class));
        ArgumentCaptor<NotificationMessage> captor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notificationPublisher).publish(captor.capture());

        NotificationMessage message = captor.getValue();
        assertEquals(result.id(), message.bookingId());
        assertEquals(userId, message.userId());
        assertEquals(NotificationType.BOOKING_CREATED, message.notificationType());
        assertNotNull(message.eventId());
        assertNotNull(message.occurredAt());
        ArgumentCaptor<BusinessEvent> eventCaptor = ArgumentCaptor.forClass(BusinessEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());
        assertEquals("BookingCreated", eventCaptor.getValue().eventType());
    }

    @Test
    void shouldNotPublishWhenScheduledDateIsInThePast() {
        BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
        OfferingRepositoryPort offerings = mock(OfferingRepositoryPort.class);
        UserRepositoryPort users = mock(UserRepositoryPort.class);
        
        NotificationPublisherPort notificationPublisher = mock(NotificationPublisherPort.class);
        BookingStatusHistoryPort historyPort = mock(BookingStatusHistoryPort.class);

        BookingService service = service(
                bookings,
                offerings,
                users,
                
                notificationPublisher,
                Instant.parse("2030-10-10T12:00:00Z")
        );

        assertThrows(
                BusinessRuleException.class,
                () -> service.create(
                        UUID.randomUUID(),
                        Instant.now().minusSeconds(60),
                        "user@example.com"
                )
        );

        verify(bookings, never()).save(any());
        verify(notificationPublisher, never()).publish(any());
    }

    @Test
    void shouldNotPublishWhenOfferingIsInactive() {
        BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
        OfferingRepositoryPort offerings = mock(OfferingRepositoryPort.class);
        UserRepositoryPort users = mock(UserRepositoryPort.class);
        
        NotificationPublisherPort notificationPublisher = mock(NotificationPublisherPort.class);

        UUID offeringId = UUID.randomUUID();

        when(offerings.findById(offeringId)).thenReturn(Optional.of(
                new Offering(
                        offeringId,
                        UUID.randomUUID(),
                        "Java",
                        "Mentoría",
                        "BACKEND",
                        BigDecimal.TEN,
                        false
                )
        ));

        BookingService service = service(
                bookings,
                offerings,
                users,
                
                notificationPublisher,
                Instant.parse("2030-10-10T12:00:00Z")
        );

        assertThrows(
                BusinessRuleException.class,
                () -> service.create(
                        offeringId,
                        Instant.now().plusSeconds(3600),
                        "user@example.com"
                )
        );

        verify(bookings, never()).save(any());
        verify(notificationPublisher, never()).publish(any());
    }

    @Test
    void shouldNotPublishWhenUserDoesNotExist() {
        BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
        OfferingRepositoryPort offerings = mock(OfferingRepositoryPort.class);
        UserRepositoryPort users = mock(UserRepositoryPort.class);
        
        NotificationPublisherPort notificationPublisher = mock(NotificationPublisherPort.class);

        UUID offeringId = UUID.randomUUID();

        when(offerings.findById(offeringId)).thenReturn(Optional.of(
                new Offering(
                        offeringId,
                        UUID.randomUUID(),
                        "Java",
                        "Mentoría",
                        "BACKEND",
                        BigDecimal.TEN,
                        true
                )
        ));

        when(users.findByEmail("ghost@example.com"))
                .thenReturn(Optional.empty());

        BookingService service = service(
                bookings,
                offerings,
                users,
                
                notificationPublisher,
                Instant.parse("2030-10-10T12:00:00Z")
        );

        assertThrows(
                DomainNotFoundException.class,
                () -> service.create(
                        offeringId,
                        Instant.now().plusSeconds(3600),
                        "ghost@example.com"
                )
        );

        verify(bookings, never()).save(any());
        verify(notificationPublisher, never()).publish(any());
    }

    @Test
    void shouldCancelOwnedCreatedBooking() {
        BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
        OfferingRepositoryPort offerings = mock(OfferingRepositoryPort.class);
        UserRepositoryPort users = mock(UserRepositoryPort.class);
        NotificationPublisherPort notificationPublisher = mock(NotificationPublisherPort.class);
        BookingStatusHistoryPort historyPort = mock(BookingStatusHistoryPort.class);
        BookingEventPublisherPort eventPublisher = mock(BookingEventPublisherPort.class);

        Instant now = Instant.parse("2030-10-10T12:00:00Z");
        UUID customerId = UUID.randomUUID();

        Booking booking = booking(
                customerId,
                now.plus(Duration.ofHours(24)),
                BookingStatus.CREATED
        );

        when(bookings.findByIdForCancellation(booking.id())).thenReturn(Optional.of(booking));
        when(users.findByEmail("customer@example.com"))
                .thenReturn(Optional.of(user(customerId, "customer@example.com")));
        when(bookings.save(any(Booking.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Booking result = service(
                bookings,
                offerings,
                users,
                notificationPublisher,
                historyPort,
                eventPublisher,
                now
        ).cancel(new CancelBookingCommand(
                booking.id(),
                "customer@example.com"
        ));

        assertEquals(BookingStatus.CANCELLED, result.status());
        verify(bookings).save(result);
        ArgumentCaptor<BookingStatusHistory> historyCaptor = ArgumentCaptor.forClass(BookingStatusHistory.class);
        verify(historyPort).save(historyCaptor.capture());
        assertEquals(booking.id(), historyCaptor.getValue().bookingId());
        assertEquals(BookingStatus.CREATED, historyCaptor.getValue().previousStatus());
        assertEquals(BookingStatus.CANCELLED, historyCaptor.getValue().newStatus());
        assertEquals(customerId, historyCaptor.getValue().changedBy());
        ArgumentCaptor<NotificationMessage> notificationCaptor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notificationPublisher).publish(notificationCaptor.capture());
        assertEquals(booking.id(), notificationCaptor.getValue().bookingId());
        assertEquals(customerId, notificationCaptor.getValue().userId());
        assertEquals(NotificationType.BOOKING_CANCELLED, notificationCaptor.getValue().notificationType());
        ArgumentCaptor<BusinessEvent> eventCaptor = ArgumentCaptor.forClass(BusinessEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());
        assertEquals("BookingCancelled", eventCaptor.getValue().eventType());
    }

    @Test
    void shouldHideBookingOwnedByAnotherCustomer() {
        BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
        OfferingRepositoryPort offerings = mock(OfferingRepositoryPort.class);
        UserRepositoryPort users = mock(UserRepositoryPort.class);
        
        NotificationPublisherPort notificationPublisher = mock(NotificationPublisherPort.class);
        BookingStatusHistoryPort historyPort = mock(BookingStatusHistoryPort.class);

        Instant now = Instant.parse("2030-10-10T12:00:00Z");

        Booking booking = booking(
                UUID.randomUUID(),
                now.plus(Duration.ofHours(24)),
                BookingStatus.CREATED
        );

        when(bookings.findByIdForCancellation(booking.id())).thenReturn(Optional.of(booking));
        when(users.findByEmail("other@example.com"))
                .thenReturn(Optional.of(user(UUID.randomUUID(), "other@example.com")));

        assertThrows(
                DomainNotFoundException.class,
                () -> service(
                        bookings,
                        offerings,
                        users,
                        notificationPublisher,
                        historyPort,
                        now
                ).cancel(new CancelBookingCommand(
                        booking.id(),
                        "other@example.com"
                ))
        );

        verify(bookings, never()).save(any());
        verifyNoInteractions(notificationPublisher, historyPort);
    }

    @Test
    void shouldRejectMissingBookingWithoutSideEffects() {
        BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
        OfferingRepositoryPort offerings = mock(OfferingRepositoryPort.class);
        UserRepositoryPort users = mock(UserRepositoryPort.class);
        
        NotificationPublisherPort notificationPublisher = mock(NotificationPublisherPort.class);
        BookingStatusHistoryPort historyPort = mock(BookingStatusHistoryPort.class);

        UUID bookingId = UUID.randomUUID();

        when(bookings.findByIdForCancellation(bookingId)).thenReturn(Optional.empty());

        assertThrows(
                DomainNotFoundException.class,
                () -> service(
                        bookings,
                        offerings,
                        users,
                        notificationPublisher,
                        historyPort,
                        Instant.parse("2030-10-10T12:00:00Z")
                ).cancel(new CancelBookingCommand(
                        bookingId,
                        "customer@example.com"
                ))
        );

        verify(bookings, never()).save(any());
        verifyNoInteractions(users, notificationPublisher, historyPort);
    }

    @Test
    void shouldRejectCancellationWithInsufficientNoticeWithoutSideEffects() {
        BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
        OfferingRepositoryPort offerings = mock(OfferingRepositoryPort.class);
        UserRepositoryPort users = mock(UserRepositoryPort.class);
        
        NotificationPublisherPort notificationPublisher = mock(NotificationPublisherPort.class);
        BookingStatusHistoryPort historyPort = mock(BookingStatusHistoryPort.class);

        Instant now = Instant.parse("2030-10-10T12:00:00Z");
        UUID customerId = UUID.randomUUID();

        Booking booking = booking(
                customerId,
                now.plus(Duration.ofHours(24)).minusSeconds(121),
                BookingStatus.CREATED
        );

        when(bookings.findByIdForCancellation(booking.id())).thenReturn(Optional.of(booking));
        when(users.findByEmail("customer@example.com"))
                .thenReturn(Optional.of(user(customerId, "customer@example.com")));

        assertThrows(
                BusinessRuleException.class,
                () -> service(
                        bookings,
                        offerings,
                        users,
                        notificationPublisher,
                        historyPort,
                        now
                ).cancel(new CancelBookingCommand(
                        booking.id(),
                        "customer@example.com"
                ))
        );

        verify(bookings, never()).save(any());
        verifyNoInteractions(notificationPublisher, historyPort);
    }

    @Test
    void shouldReturnAlreadyCancelledBookingWithoutSavingOrPublishing() {
        BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
        OfferingRepositoryPort offerings = mock(OfferingRepositoryPort.class);
        UserRepositoryPort users = mock(UserRepositoryPort.class);
        
        NotificationPublisherPort notificationPublisher = mock(NotificationPublisherPort.class);
        BookingStatusHistoryPort historyPort = mock(BookingStatusHistoryPort.class);

        Instant now = Instant.parse("2030-10-10T12:00:00Z");
        UUID customerId = UUID.randomUUID();

        Booking booking = booking(
                customerId,
                now.minusSeconds(1),
                BookingStatus.CANCELLED
        );

        when(bookings.findByIdForCancellation(booking.id())).thenReturn(Optional.of(booking));
        when(users.findByEmail("customer@example.com"))
                .thenReturn(Optional.of(user(customerId, "customer@example.com")));

        Booking result = service(
                bookings,
                offerings,
                users,
                notificationPublisher,
                historyPort,
                now
        ).cancel(new CancelBookingCommand(
                booking.id(),
                "customer@example.com"
        ));

        assertSame(booking, result);
        verify(bookings, never()).save(any());
        verifyNoInteractions(notificationPublisher, historyPort);
    }

    @Test
    void shouldReuseBookingWithoutPublishingEffectsForRepeatedIdempotencyKey() {
        BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
        OfferingRepositoryPort offerings = mock(OfferingRepositoryPort.class);
        UserRepositoryPort users = mock(UserRepositoryPort.class);
        NotificationPublisherPort notifications = mock(NotificationPublisherPort.class);
        BookingEventPublisherPort events = mock(BookingEventPublisherPort.class);
        UUID customerId = UUID.randomUUID();
        UUID offeringId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        Instant scheduledAt = Instant.parse("2030-10-11T12:00:00Z");
        Booking existing = new Booking(UUID.randomUUID(), offeringId, customerId, scheduledAt, BookingStatus.CREATED, 0);
        when(users.findByEmail("user@example.com")).thenReturn(Optional.of(user(customerId, "user@example.com")));
        when(bookings.findByCustomerIdAndIdempotencyKey(customerId, "same-key")).thenReturn(Optional.of(existing));
        when(bookings.findIdempotencyRequestHash(customerId, "same-key"))
            .thenReturn(Optional.of("900abdf16c6e2d53d8ee6a980e26db9b7e6b8e5a47a54a44c89498d2367f9c8b"));

        Booking result = service(bookings, offerings, users, notifications,
            mock(BookingStatusHistoryPort.class), events, Instant.parse("2030-10-10T12:00:00Z"))
            .create(offeringId, scheduledAt, "user@example.com", "same-key");
        assertSame(existing, result);
        verify(bookings, never()).save(any());
        verifyNoInteractions(notifications, events);
    }

    @Test
    void shouldRejectAnIdempotencyKeyReusedWithDifferentRequest() {
        BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
        UserRepositoryPort users = mock(UserRepositoryPort.class);
        UUID customerId = UUID.randomUUID();
        UUID originalOfferingId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        UUID differentOfferingId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        Instant scheduledAt = Instant.parse("2030-10-11T12:00:00Z");
        Booking existing = new Booking(UUID.randomUUID(), originalOfferingId, customerId, scheduledAt, BookingStatus.CREATED, 0);
        when(users.findByEmail("user@example.com")).thenReturn(Optional.of(user(customerId, "user@example.com")));
        when(bookings.findByCustomerIdAndIdempotencyKey(customerId, "same-key")).thenReturn(Optional.of(existing));
        when(bookings.findIdempotencyRequestHash(customerId, "same-key")).thenReturn(Optional.of("different-hash"));

        BookingService bookingService = service(bookings, mock(OfferingRepositoryPort.class), users,
            mock(NotificationPublisherPort.class), mock(BookingStatusHistoryPort.class),
            mock(BookingEventPublisherPort.class), Instant.parse("2030-10-10T12:00:00Z"));

        assertThrows(IdempotencyKeyConflictException.class,
            () -> bookingService.create(differentOfferingId, scheduledAt, "user@example.com", "same-key"));
        verify(bookings, never()).save(any());
    }

    private BookingService service(
            BookingRepositoryPort bookings,
            OfferingRepositoryPort offerings,
            UserRepositoryPort users,
            NotificationPublisherPort notificationPublisher,
            Instant now) {
        return service(
                bookings,
                offerings,
                users,
                notificationPublisher,
                mock(BookingStatusHistoryPort.class),
                mock(BookingEventPublisherPort.class),
                now);
    }

    private BookingService service(
            BookingRepositoryPort bookings,
            OfferingRepositoryPort offerings,
            UserRepositoryPort users,
            NotificationPublisherPort notificationPublisher,
            BookingStatusHistoryPort historyPort,
            Instant now) {
        return service(
                bookings,
                offerings,
                users,
                notificationPublisher,
                historyPort,
                mock(BookingEventPublisherPort.class),
                now);
    }

    private BookingService service(
            BookingRepositoryPort bookings,
            OfferingRepositoryPort offerings,
            UserRepositoryPort users,
            NotificationPublisherPort notificationPublisher,
            BookingStatusHistoryPort historyPort,
            BookingEventPublisherPort eventPublisher,
            Instant now) {
        return new BookingService(
                bookings,
                offerings,
                users,
                cancellationPolicyAt(now),
                notificationPublisher,
                historyPort,
                Clock.fixed(now, ZoneOffset.UTC),
                eventPublisher
        );
    }

    private BookingCancellationPolicy cancellationPolicyAt(Instant now) {
        return new BookingCancellationPolicy(
                Clock.fixed(now, ZoneOffset.UTC),
                Duration.ofHours(24)
        );
    }

    private Booking booking(
            UUID customerId,
            Instant scheduledAt,
            BookingStatus status) {

        return new Booking(
                UUID.randomUUID(),
                UUID.randomUUID(),
                customerId,
                scheduledAt,
                status,
                0
        );
    }

    private UserAccount user(UUID id, String email) {
        return new UserAccount(id, "Customer", email, "password", Role.CUSTOMER);
    }
}


