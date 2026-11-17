package com.riwi.skillbridge.infrastructure.adapter.in.scheduled;

import com.riwi.skillbridge.application.port.in.SendPaymentRemindersUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentReminderSchedulerTest {

    @Mock
    private SendPaymentRemindersUseCase sendPaymentRemindersUseCase;

    @InjectMocks
    private PaymentReminderScheduler scheduler;

    @Test
    @DisplayName("Debe delegar la ejecución en el caso de uso SendPaymentRemindersUseCase")
    void shouldDelegateExecutionToUseCase() {
        when(sendPaymentRemindersUseCase.sendPaymentReminders()).thenReturn(3);

        scheduler.executePaymentReminders();

        verify(sendPaymentRemindersUseCase, times(1)).sendPaymentReminders();
    }

    @Test
    @DisplayName("Debe ejecutar recordatorios al arrancar la aplicación")
    void shouldExecuteRemindersOnStartup() {
        when(sendPaymentRemindersUseCase.sendPaymentReminders()).thenReturn(1);

        scheduler.onStartup();

        verify(sendPaymentRemindersUseCase, times(1)).sendPaymentReminders();
    }

    @Test
    @DisplayName("No debe propagar excepciones si el caso de uso falla")
    void shouldCatchExceptionWithoutPropagating() {
        doThrow(new RuntimeException("Database error"))
                .when(sendPaymentRemindersUseCase).sendPaymentReminders();

        assertDoesNotThrow(() -> scheduler.executePaymentReminders());
    }
}

