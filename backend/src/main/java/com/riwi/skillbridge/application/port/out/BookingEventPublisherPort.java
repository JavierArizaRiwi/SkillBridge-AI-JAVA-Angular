package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.application.port.out.event.BusinessEvent;

public interface BookingEventPublisherPort {
    void publish(BusinessEvent<?> event);
}
