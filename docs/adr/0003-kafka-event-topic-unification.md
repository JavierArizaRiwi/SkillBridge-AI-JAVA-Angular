# ADR-0003: Unification of Kafka Topics for Domain and Audit Events

## Status
Accepted (De Facto in codebase) — Documented for Formal Architectural Alignment.

## Date
2026-10-21

## Context
Initially, architectural guidelines recommended separating event streaming into two distinct Kafka topics:
1. `booking-events`: Dedicated strictly to core domain lifecycle events (`BookingCreated`, `BookingCancelled`).
2. `audit-events`: Dedicated to user activity and security audits (`UserRegistered`, `UserLoggedIn`, `OfferingCreated`).

However, the independent microservice [audit-service/backend/](../../audit-service/backend) implements [KafkaAuditConsumer.java](../../audit-service/backend/src/main/java/com/riwi/audit/consumer/KafkaAuditConsumer.java) with a single listener binding:
```java
@KafkaListener(topics = "${app.kafka.topic.booking-events}", groupId = "${spring.kafka.consumer.group-id}")
public void consume(BusinessEvent event) { ... }
```
Because `audit-service` is deployed independently and operational constraints restricted simultaneous code refactoring on both sides, publishing security events to a separate `audit-events` topic prevented those events from being indexed by MongoDB and displayed in the audit admin dashboard.

## Decision
Unify event emission in the core backend configuration so that both business events and audit events are published to the topic identified by `${app.kafka.topic.booking-events}` (defaulting to `booking-events`):

```yaml
# backend/src/main/resources/application.yml
app:
  kafka:
    topic:
      booking-events: ${KAFKA_TOPIC_BOOKING_EVENTS:booking-events}
      audit-events: ${KAFKA_TOPIC_AUDIT_EVENTS:booking-events}
```

All published events continue to encapsulate domain data inside the polymorphic container `BusinessEvent<T>`, allowing the audit consumer to ingest and filter events dynamically using the `eventType` attribute.

## Consequences
- **Positive**: All domain and security events are immediately captured by `KafkaAuditConsumer` and visible in the audit dashboard without requiring dual consumer threads or multi-topic subscriptions.
- **Positive**: Simplifies Kafka topic management and partitioning in the local and test environments.
- **Negative / Trade-off**: High-volume domain events and administrative audit events share the same partition log, preventing fine-grained retention policies (e.g. keeping audit logs for years while pruning booking telemetry after months).
- **Future Evolution**: Once `audit-service/backend` is updated to listen to a list of topics (`topics = "#{'${app.kafka.topics}'.split(',')}"`), the topics can be cleanly bifurcated without losing backward compatibility.
