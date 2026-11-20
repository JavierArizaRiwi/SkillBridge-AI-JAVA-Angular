package com.riwi.audit.repository;

import com.riwi.audit.model.BusinessEvent;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AuditEventRepository {
    private static final int MAX_EVENTS = 500;
    private final List<BusinessEvent> events = Collections.synchronizedList(new LinkedList<>());

    public void save(BusinessEvent event) {
        events.add(0, event); // Add to the beginning (newest first)
        if (events.size() > MAX_EVENTS) {
            events.remove(events.size() - 1); // Remove the oldest
        }
    }

    public List<BusinessEvent> findAll() {
        return List.copyOf(events);
    }

    public Optional<BusinessEvent> findById(UUID eventId) {
        return events.stream()
                .filter(e -> e.getEventId() != null && e.getEventId().equals(eventId))
                .findFirst();
    }
}
