package com.riwi.skillbridge.infrastructure.adapter.in.messaging;

import com.riwi.skillbridge.application.port.in.ProcessNotificationUseCase;
import com.riwi.skillbridge.application.port.out.NotificationMessage;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationConsumerTest {

    @Test
    void shouldDelegateValidMessageToUseCase() {
        ProcessNotificationUseCase useCase = mock(ProcessNotificationUseCase.class);
        NotificationConsumer consumer = new NotificationConsumer(useCase);
        NotificationMessage message = NotificationMessage.bookingCreated(UUID.randomUUID(), UUID.randomUUID());

        consumer.onNotification(message);

        verify(useCase).process(message);
    }

    @Test
    void shouldPropagateFailureSoListenerContainerRetriesAndRejects() {
        ProcessNotificationUseCase useCase = mock(ProcessNotificationUseCase.class);
        NotificationConsumer consumer = new NotificationConsumer(useCase);
        NotificationMessage message = NotificationMessage.bookingCreated(UUID.randomUUID(), UUID.randomUUID());
        doThrow(new IllegalStateException("boom")).when(useCase).process(message);

        assertThrows(IllegalStateException.class, () -> consumer.onNotification(message));
    }
}
