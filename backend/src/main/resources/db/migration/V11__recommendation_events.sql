-- HU-22: recommendation_events table
-- Stores one row per AI recommendation lifecycle event (RecommendationRequested / RecommendationGenerated).
-- event_id maps to BusinessEvent.eventId and is the primary key to guarantee idempotency:
-- if the same Kafka message is delivered twice, the second INSERT fails on the PK constraint.

CREATE TABLE recommendation_events (
    id                 UUID         PRIMARY KEY,           -- BusinessEvent.eventId (idempotency key)
    recommendation_id  UUID         NOT NULL,              -- logical ID shared by Requested + Generated pair
    user_id            UUID         NOT NULL,              -- who triggered the recommendation
    offering_id        UUID,                               -- null until we implement offering-level tracking
    event_type         VARCHAR(60)  NOT NULL,              -- 'RecommendationRequested' | 'RecommendationGenerated'
    input_type         VARCHAR(20)  NOT NULL DEFAULT 'TEXT', -- TEXT | VOICE | IMAGE
    model_used         VARCHAR(120),                       -- e.g. 'gemini-3.8-flash'
    latency_ms         BIGINT,                             -- populated on RecommendationGenerated only
    occurred_at        TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_rec_events_recommendation_id ON recommendation_events(recommendation_id);
CREATE INDEX idx_rec_events_user_id           ON recommendation_events(user_id);
CREATE INDEX idx_rec_events_occurred_at       ON recommendation_events(occurred_at);
