package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.SendPaymentRemindersUseCase;
import com.riwi.skillbridge.application.port.out.BookingRepositoryPort;
import com.riwi.skillbridge.application.port.out.NotificationMessage;
import com.riwi.skillbridge.application.port.out.NotificationPublisherPort;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.BookingStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class PaymentReminderService implements SendPaymentRemindersUseCase {

    private static final Logger log = LoggerFactory.getLogger(PaymentReminderService.class);

    private final BookingRepositoryPort bookingRepository;
    private final NotificationPublisherPort notificationPublisher;
    private final Clock clock;

    public PaymentReminderService(BookingRepositoryPort bookingRepository,
                                  NotificationPublisherPort notificationPublisher,
                                  Clock clock) {
        this.bookingRepository = bookingRepository;
        this.notificationPublisher = notificationPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public int sendPaymentReminders() {
        Instant now = clock.instant();
        List<Booking> pendingBookings = bookingRepository.findByStatus(BookingStatus.CREATED);
        int sentCount = 0;

        for (Booking booking : pendingBookings) {
            if (booking.isEligibleForPaymentReminder(now)) {
                log.info("Sending daily payment reminder for bookingId={} customerId={}",
                    booking.id(), booking.customerId());

                notificationPublisher.publish(
                    NotificationMessage.paymentReminder(
                        booking.id(),
                        booking.customerId(),
                        booking.paymentDeadline()
                    )
                );

                Booking updated = booking.withLastPaymentReminderSentAt(now);
                bookingRepository.save(updated);
                sentCount++;
            }
        }

        log.info("Payment reminder job finished. Reminders sent: {}", sentCount);
        return sentCount;
    }
}

