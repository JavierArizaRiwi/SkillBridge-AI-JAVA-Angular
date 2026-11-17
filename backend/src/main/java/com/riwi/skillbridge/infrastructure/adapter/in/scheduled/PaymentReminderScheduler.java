package com.riwi.skillbridge.infrastructure.adapter.in.scheduled;

import com.riwi.skillbridge.application.port.in.SendPaymentRemindersUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "notifications.payment-reminder.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentReminderScheduler.class);

    private final SendPaymentRemindersUseCase sendPaymentRemindersUseCase;

    public PaymentReminderScheduler(SendPaymentRemindersUseCase sendPaymentRemindersUseCase) {
        this.sendPaymentRemindersUseCase = sendPaymentRemindersUseCase;
    }

    /**
     * Revisa recordatorios pendientes al arrancar la aplicación.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("Checking pending payment reminders on application startup");
        executePaymentReminders();
    }

    /**
     * Tarea programada para ejecutarse a diario a las 08:00 AM UTC (configurable por cron).
     */
    @Scheduled(cron = "${notifications.payment-reminder.cron:0 0 8 * * *}")
    public void executePaymentReminders() {
        log.info("Executing scheduled daily payment reminders job");
        try {
            int remindersSent = sendPaymentRemindersUseCase.sendPaymentReminders();
            log.info("Scheduled payment reminders completed. Total sent: {}", remindersSent);
        } catch (Exception e) {
            log.error("Error executing payment reminders scheduled task", e);
        }
    }
}

