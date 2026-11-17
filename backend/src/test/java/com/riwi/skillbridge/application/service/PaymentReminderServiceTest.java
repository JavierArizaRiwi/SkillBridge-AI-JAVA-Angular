package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.BookingRepositoryPort;
import com.riwi.skillbridge.application.port.out.NotificationMessage;
import com.riwi.skillbridge.application.port.out.NotificationPublisherPort;
import com.riwi.skillbridge.application.port.out.NotificationType;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.BookingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentReminderServiceTest {

    @Mock
    private BookingRepositoryPort bookingRepository;

    @Mock
    private NotificationPublisherPort notificationPublisher;

    private final Instant fixedNow = Instant.parse("2026-11-10T10:00:00Z");
    private Clock clock;
    private PaymentReminderService service;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(fixedNow, ZoneOffset.UTC);
        service = new PaymentReminderService(bookingRepository, notificationPublisher, clock);
    }

    @Test
    @DisplayName("Debe enviar recordatorio y actualizar fecha de recordatorio para reserva elegible")
    void shouldSendReminderAndPersistUpdateForEligibleBooking() {
        UUID bookingId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        // Sesión en 4 días (vencimiento de pago en 3.5 días -> dentro de los 7 días)
        Booking eligible = new Booking(bookingId, UUID.randomUUID(), customerId,
                fixedNow.plus(Duration.ofDays(4)), BookingStatus.CREATED, 0);

        when(bookingRepository.findByStatus(BookingStatus.CREATED)).thenReturn(List.of(eligible));

        int sent = service.sendPaymentReminders();

        assertEquals(1, sent);

        ArgumentCaptor<NotificationMessage> messageCaptor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notificationPublisher, times(1)).publish(messageCaptor.capture());
        NotificationMessage captured = messageCaptor.getValue();
        assertEquals(bookingId, captured.bookingId());
        assertEquals(customerId, captured.userId());
        assertEquals(NotificationType.PAYMENT_REMINDER, captured.notificationType());
        assertEquals(eligible.paymentDeadline(), captured.paymentDeadline());

        ArgumentCaptor<Booking> bookingCaptor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository, times(1)).save(bookingCaptor.capture());
        Booking saved = bookingCaptor.getValue();
        assertEquals(fixedNow, saved.lastPaymentReminderSentAt());
    }

    @Test
    @DisplayName("No debe enviar recordatorio si ya se envió uno en las últimas 24 horas")
    void shouldNotSendReminderIfAlreadyRemindedToday() {
        Booking alreadyReminded = new Booking(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                fixedNow.plus(Duration.ofDays(4)), BookingStatus.CREATED, 0, fixedNow.minus(Duration.ofHours(8)));

        when(bookingRepository.findByStatus(BookingStatus.CREATED)).thenReturn(List.of(alreadyReminded));

        int sent = service.sendPaymentReminders();

        assertEquals(0, sent);
        verifyNoInteractions(notificationPublisher);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Debe enviar recordatorio diario si el anterior fue hace más de 24 horas")
    void shouldSendReminderIfPreviousWasSentMoreThan24HoursAgo() {
        Booking remindedYesterday = new Booking(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                fixedNow.plus(Duration.ofDays(3)), BookingStatus.CREATED, 0, fixedNow.minus(Duration.ofHours(26)));

        when(bookingRepository.findByStatus(BookingStatus.CREATED)).thenReturn(List.of(remindedYesterday));

        int sent = service.sendPaymentReminders();

        assertEquals(1, sent);
        verify(notificationPublisher, times(1)).publish(any());
        verify(bookingRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("No debe enviar recordatorio si faltan más de 7 días para el vencimiento")
    void shouldNotSendReminderIfMoreThan7DaysRemaining() {
        Booking farInFuture = new Booking(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                fixedNow.plus(Duration.ofDays(10)), BookingStatus.CREATED, 0);

        when(bookingRepository.findByStatus(BookingStatus.CREATED)).thenReturn(List.of(farInFuture));

        int sent = service.sendPaymentReminders();

        assertEquals(0, sent);
        verifyNoInteractions(notificationPublisher);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("No debe enviar recordatorio si el plazo de pago ya expiró")
    void shouldNotSendReminderIfPaymentWindowExpired() {
        // Sesión en 5 horas -> ya venció la ventana de 12 horas
        Booking expired = new Booking(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                fixedNow.plus(Duration.ofHours(5)), BookingStatus.CREATED, 0);

        when(bookingRepository.findByStatus(BookingStatus.CREATED)).thenReturn(List.of(expired));

        int sent = service.sendPaymentReminders();

        assertEquals(0, sent);
        verifyNoInteractions(notificationPublisher);
        verify(bookingRepository, never()).save(any());
    }
}

