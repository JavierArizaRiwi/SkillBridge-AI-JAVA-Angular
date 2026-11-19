package com.riwi.skillbridge.application.port.out;

public interface NotificationSenderPort {
    void send(NotificationMessage message);
}
