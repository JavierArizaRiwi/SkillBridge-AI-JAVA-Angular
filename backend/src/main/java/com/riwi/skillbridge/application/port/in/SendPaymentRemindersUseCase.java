package com.riwi.skillbridge.application.port.in;

public interface SendPaymentRemindersUseCase {
    /**
     * Revisa todas las reservas en estado CREATED y envía una notificación
     * diaria de recordatorio de pago a aquellas que se encuentren a 7 días
     * o menos del vencimiento del plazo de pago.
     *
     * @return El número de recordatorios enviados exitosamente.
     */
    int sendPaymentReminders();
}

