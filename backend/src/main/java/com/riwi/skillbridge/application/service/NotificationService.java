package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.ProcessNotificationUseCase;
import com.riwi.skillbridge.application.port.out.NotificationMessage;
import com.riwi.skillbridge.application.port.out.NotificationSenderPort;
import org.springframework.stereotype.Service;

@Service
public class NotificationService implements ProcessNotificationUseCase {

    private final NotificationSenderPort notificationSender;

    public NotificationService(NotificationSenderPort notificationSender) {
        this.notificationSender = notificationSender;
    }

    @Override
    public void process(NotificationMessage message) {
        notificationSender.send(message);
    }
}
