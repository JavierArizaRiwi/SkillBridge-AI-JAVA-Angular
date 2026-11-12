package com.riwi.skillbridge.application.port.out.event;

public interface AuditEventPublisherPort {
    void publish(BusinessEvent<?> event);
}
