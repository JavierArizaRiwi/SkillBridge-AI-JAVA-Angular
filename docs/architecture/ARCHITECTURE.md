# Architecture

## Architectural style

The backend uses **Ports & Adapters / Hexagonal Architecture**.

```text
HTTP / Rabbit Consumers
        |
        v
  inbound adapters
        |
        v
  application ports
        |
        v
   use-case services
        |
        v
      domain
        |
        v
  outbound ports
   /   |   |   \
 JPA Redis Rabbit AI
```

The dependency rule is intentional: domain and application code do not depend on PostgreSQL, Redis, RabbitMQ, Gemini or Angular.

## Real reasons for each technology

- **PostgreSQL:** system of record for users, services and bookings.
- **Redis:** Cache-Aside implementation for the public service catalog.
- **RabbitMQ:** `BookingCreated` is published after persistence and consumed asynchronously. Extend the consumer with email, audit, payments, WhatsApp or another microservice.
- **Gemini:** implemented behind `AiRecommendationPort`; replaceable without changing the use case.
- **Nginx:** serves the Angular production build and reverse-proxies `/api/*` to Spring Boot in the containerized topology.
- **Prometheus + Grafana:** operational visibility through Spring Boot Actuator and Micrometer.

## Deliberate extension points

Students should extend this starter rather than collapse the layers. Suggested additions:

1. Provider role and offering CRUD.
2. Availability/calendar aggregate.
3. Booking confirmation/cancellation state machine.
4. Payment port and mock payment adapter.
5. Notification port with email/WhatsApp adapter.
6. Dead-letter queue and idempotent consumers.
7. Outbox pattern for guaranteed event publishing.
8. AI response cache with a short TTL and normalized prompt key.
9. Rate limiting at Nginx or application level.
10. Testcontainers integration tests.
