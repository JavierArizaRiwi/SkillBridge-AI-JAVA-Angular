package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.NotificationMessage;
import com.riwi.skillbridge.application.port.out.NotificationSenderPort;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationServiceTest {

    @Test
    void shouldDelegateToNotificationSender() {
        NotificationSenderPort sender = mock(NotificationSenderPort.class);
        NotificationService service = new NotificationService(sender);
        NotificationMessage message = NotificationMessage.bookingCreated(UUID.randomUUID(), UUID.randomUUID());

        service.process(message);

        verify(sender).send(message);
    }

    @Test
    void shouldPropagateSenderFailureSoRetryCanKickIn() {
        NotificationSenderPort sender = mock(NotificationSenderPort.class);
        NotificationService service = new NotificationService(sender);
        NotificationMessage message = NotificationMessage.bookingCreated(UUID.randomUUID(), UUID.randomUUID());
        IllegalStateException failure = new IllegalStateException("boom");
        doThrow(failure).when(sender).send(message);

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> service.process(message));

        assertSame(failure, thrown);
    }
}
