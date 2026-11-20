package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.application.port.out.NotificationMessage;

public interface ProcessNotificationUseCase {
    void process(NotificationMessage message);
}
