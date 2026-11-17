# Instructor Guide

## Suggested sequence

### Session 1 — Architecture and baseline
- Explain the business case and bounded context.
- Identify inbound/outbound ports.
- Run `docker compose up --build`.
- Inspect containers, networks, volumes and health checks.

### Session 2 — Persistence and migrations
- PostgreSQL as system of record.
- JPA adapters vs domain objects.
- Flyway and immutable migrations.
- Add one new field using `V2__...sql`.

### Session 3 — Cache
- Measure catalog latency.
- Inspect Redis keys and TTL.
- Force cache miss.
- Discuss invalidation and stale data.

### Session 4 — Messaging
- Create a booking.
- Inspect exchange, queue and routing key.
- Break the consumer intentionally.
- Observe retry and DLQ.
- Discuss idempotency and Outbox.

### Session 5 — Security
- Registration/login.
- Decode JWT claims.
- Add PROVIDER/ADMIN authorization.
- Discuss authentication vs authorization.

### Session 6 — AI
- Explain why the key belongs in backend infrastructure.
- Replace prompt behavior without changing controllers.
- Discuss privacy, rate limits, timeout and fallback.

### Session 7 — Observability
- Enable Prometheus/Grafana profile.
- Generate traffic.
- Build a latency/error/JVM dashboard.
- Define one SLI and one simple SLO.

### Session 8 — CI/CD and cloud
- Open PR and inspect CI.
- Provision managed cloud dependencies.
- Deploy backend and frontend.
- Verify CORS, TLS, health and cold-start behavior.

## Evaluation proposal

| Dimension | Weight |
|---|---:|
| Domain/hexagonal architecture | 20% |
| Business functionality | 15% |
| Security and validation | 10% |
| Persistence/migrations | 10% |
| Redis/cache design | 10% |
| RabbitMQ/event design | 10% |
| Testing/quality | 10% |
| Docker/CI/deployment | 10% |
| Observability/documentation | 5% |

Do not award architecture points only for folder names. Ask the team to replace an adapter or explain dependency direction.

## Oral defense questions

- What would break if Redis disappeared for five minutes?
- How do you know a cache hit occurred?
- Why is RabbitMQ not called directly from a controller?
- What happens between database commit and event publish?
- How would you make event processing idempotent?
- What belongs in a domain service vs an application service?
- Why is the Gemini adapter infrastructure?
- Why does production configuration not belong in the Docker image?
- Which component is the source of truth for a booking?
- When would you split a module into a microservice?
