package com.riwi.audit.repository;

import com.riwi.audit.model.BusinessEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataMongoTest
class AuditEventRepositoryTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
    }

    @Autowired
    AuditEventRepository repository;

    private BusinessEvent newEvent(String type) {
        return BusinessEvent.builder()
            .eventId(UUID.randomUUID())
            .eventType(type)
            .aggregateId("agg-1")
            .aggregateType("Booking")
            .occurredAt(Instant.now())
            .correlationId("corr-1")
            .version(1)
            .payload(Map.of("key", "value"))
            .build();
    }

    @Test
    void shouldPersistAndRetrieveEventById() {
        BusinessEvent event = newEvent("BookingCreated");
        repository.save(event);

        var found = repository.findById(event.getEventId());

        assertThat(found).isPresent();
        assertThat(found.get().getEventType()).isEqualTo("BookingCreated");
    }

    @Test
    void shouldListEventsOrderedByOccurredAtDescending() {
        BusinessEvent older = newEvent("BookingCreated");
        older.setOccurredAt(Instant.now().minusSeconds(60));
        BusinessEvent newer = newEvent("BookingCancelled");
        newer.setOccurredAt(Instant.now());

        repository.save(older);
        repository.save(newer);

        var events = repository.findAllByOrderByOccurredAtDesc();

        assertThat(events.get(0).getEventId()).isEqualTo(newer.getEventId());
        assertThat(events.get(1).getEventId()).isEqualTo(older.getEventId());
    }
}
