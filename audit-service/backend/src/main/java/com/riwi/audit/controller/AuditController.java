package com.riwi.audit.controller;

import com.riwi.audit.model.BusinessEvent;
import com.riwi.audit.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditEventRepository auditEventRepository;

    @GetMapping("/events")
    public List<BusinessEvent> getEvents() {
        return auditEventRepository.findAll();
    }

    @GetMapping("/events/{eventId}")
    public ResponseEntity<BusinessEvent> getEvent(@PathVariable UUID eventId) {
        return auditEventRepository.findById(eventId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        List<BusinessEvent> events = auditEventRepository.findAll();
        
        long bookingCreated = events.stream().filter(e -> "BookingCreated".equals(e.getEventType())).count();
        long bookingCancelled = events.stream().filter(e -> "BookingCancelled".equals(e.getEventType())).count();
        long otherEvents = events.size() - bookingCreated - bookingCancelled;
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalEvents", events.size());
        stats.put("bookingCreated", bookingCreated);
        stats.put("bookingCancelled", bookingCancelled);
        stats.put("otherEvents", otherEvents);
        
        return stats;
    }
}
