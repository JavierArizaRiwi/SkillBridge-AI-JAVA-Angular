package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.CancelBookingCommand;

import com.riwi.skillbridge.application.port.out.BookingRepositoryPort;
import com.riwi.skillbridge.application.port.out.NotificationMessage;
import com.riwi.skillbridge.application.port.out.NotificationPublisherPort;
import com.riwi.skillbridge.application.port.out.NotificationType;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.BookingStatus;
import com.riwi.skillbridge.domain.model.Offering;
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
        UserAccountPort users = mock(UserAccountPort.class);
        
        NotificationPublisherPort notificationPublisher = mock(NotificationPublisherPort.class);

        UUID offeringId = UUID.randomUUID();
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
        when(users.findIdByEmail("user@example.com")).thenReturn(Optional.of(userId));
        when(bookings.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        BookingService service = service(
                bookings,
                offerings,
                users,
                
                notificationPublisher,
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
    }

    @Test
    void shouldNotPublishWhenScheduledDateIsInThePast() {
        BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
        OfferingRepositoryPort offerings = mock(OfferingRepositoryPort.class);
        UserAccountPort users = mock(UserAccountPort.class);
        
        NotificationPublisherPort notificationPublisher = mock(NotificationPublisherPort.class);

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
        UserAccountPort users = mock(UserAccountPort.class);
        
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
        UserAccountPort users = mock(UserAccountPort.class);
        
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

        when(users.findIdByEmail("ghost@example.com"))
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
        UserAccountPort users = mock(UserAccountPort.class);
        
        NotificationPublisherPort notificationPublisher = mock(NotificationPublisherPort.class);

        Instant now = Instant.parse("2030-10-10T12:00:00Z");
        UUID customerId = UUID.randomUUID();

        Booking booking = booking(
                customerId,
                now.plus(Duration.ofHours(24)),
                BookingStatus.CREATED
        );

        when(bookings.findById(booking.id())).thenReturn(Optional.of(booking));
        when(users.findIdByEmail("customer@example.com"))
                .thenReturn(Optional.of(customerId));
        when(bookings.save(any(Booking.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Booking result = service(
                bookings,
                offerings,
                users,
                
                notificationPublisher,
                now
        ).cancel(new CancelBookingCommand(
                booking.id(),
                "customer@example.com"
        ));

        assertEquals(BookingStatus.CANCELLED, result.status());
        verify(bookings).save(result);
        
    }

    @Test
    void shouldHideBookingOwnedByAnotherCustomer() {
        BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
        OfferingRepositoryPort offerings = mock(OfferingRepositoryPort.class);
        UserAccountPort users = mock(UserAccountPort.class);
        
        NotificationPublisherPort notificationPublisher = mock(NotificationPublisherPort.class);

        Instant now = Instant.parse("2030-10-10T12:00:00Z");

        Booking booking = booking(
                UUID.randomUUID(),
                now.plus(Duration.ofHours(24)),
                BookingStatus.CREATED
        );

        when(bookings.findById(booking.id())).thenReturn(Optional.of(booking));
        when(users.findIdByEmail("other@example.com"))
                .thenReturn(Optional.of(UUID.randomUUID()));

        assertThrows(
                DomainNotFoundException.class,
                () -> service(
                        bookings,
                        offerings,
                        users,
                        
                        notificationPublisher,
                        now
                ).cancel(new CancelBookingCommand(
                        booking.id(),
                        "other@example.com"
                ))
        );

        verify(bookings, never()).save(any());
        
    }

    @Test
    void shouldRejectMissingBookingWithoutSideEffects() {
        BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
        OfferingRepositoryPort offerings = mock(OfferingRepositoryPort.class);
        UserAccountPort users = mock(UserAccountPort.class);
        
        NotificationPublisherPort notificationPublisher = mock(NotificationPublisherPort.class);

        UUID bookingId = UUID.randomUUID();

        when(bookings.findById(bookingId)).thenReturn(Optional.empty());

        assertThrows(
                DomainNotFoundException.class,
                () -> service(
                        bookings,
                        offerings,
                        users,
                        
                        notificationPublisher,
                        Instant.parse("2030-10-10T12:00:00Z")
                ).cancel(new CancelBookingCommand(
                        bookingId,
                        "customer@example.com"
                ))
        );

        verify(bookings, never()).save(any());
        verifyNoInteractions(users);
    }

    @Test
    void shouldRejectCancellationWithInsufficientNoticeWithoutSideEffects() {
        BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
        OfferingRepositoryPort offerings = mock(OfferingRepositoryPort.class);
        UserAccountPort users = mock(UserAccountPort.class);
        
        NotificationPublisherPort notificationPublisher = mock(NotificationPublisherPort.class);

        Instant now = Instant.parse("2030-10-10T12:00:00Z");
        UUID customerId = UUID.randomUUID();

        Booking booking = booking(
                customerId,
                now.plus(Duration.ofHours(24)).minusSeconds(1),
                BookingStatus.CREATED
        );

        when(bookings.findById(booking.id())).thenReturn(Optional.of(booking));
        when(users.findIdByEmail("customer@example.com"))
                .thenReturn(Optional.of(customerId));

        assertThrows(
                BusinessRuleException.class,
                () -> service(
                        bookings,
                        offerings,
                        users,
                        
                        notificationPublisher,
                        now
                ).cancel(new CancelBookingCommand(
                        booking.id(),
                        "customer@example.com"
                ))
        );

        verify(bookings, never()).save(any());
        
    }

    @Test
    void shouldReturnAlreadyCancelledBookingWithoutSavingOrPublishing() {
        BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
        OfferingRepositoryPort offerings = mock(OfferingRepositoryPort.class);
        UserAccountPort users = mock(UserAccountPort.class);
        
        NotificationPublisherPort notificationPublisher = mock(NotificationPublisherPort.class);

        Instant now = Instant.parse("2030-10-10T12:00:00Z");
        UUID customerId = UUID.randomUUID();

        Booking booking = booking(
                customerId,
                now.minusSeconds(1),
                BookingStatus.CANCELLED
        );

        when(bookings.findById(booking.id())).thenReturn(Optional.of(booking));
        when(users.findIdByEmail("customer@example.com"))
                .thenReturn(Optional.of(customerId));

        Booking result = service(
                bookings,
                offerings,
                users,
                
                notificationPublisher,
                now
        ).cancel(new CancelBookingCommand(
                booking.id(),
                "customer@example.com"
        ));

        assertSame(booking, result);
        verify(bookings, never()).save(any());
        
    }

    private BookingService service(
            BookingRepositoryPort bookings,
            OfferingRepositoryPort offerings,
            UserAccountPort users,
            
            NotificationPublisherPort notificationPublisher,
            Instant now) {

        return new BookingService(
                bookings,
                offerings,
                users,
                
                cancellationPolicyAt(now),
                notificationPublisher
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
                status
        );
    }
}
