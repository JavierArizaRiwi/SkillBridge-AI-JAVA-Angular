-- HU-22: recommendation_daily_metrics table
-- One row per (offering_id, date) pair. Updated incrementally by the Kafka analytics consumer
-- every time a RecommendationGenerated, BookingCreated, or BookingCancelled event arrives.
-- The trend_score and forecast_next_7_days columns are recomputed on each update.

CREATE TABLE recommendation_daily_metrics (
    id                  UUID         PRIMARY KEY,
    date                DATE         NOT NULL,
    offering_id         UUID         NOT NULL REFERENCES offerings(id),
    recommendations     BIGINT       NOT NULL DEFAULT 0,
    unique_users        BIGINT       NOT NULL DEFAULT 0,
    bookings            BIGINT       NOT NULL DEFAULT 0,
    cancellations       BIGINT       NOT NULL DEFAULT 0,
    conversion_rate     DOUBLE PRECISION NOT NULL DEFAULT 0,
    trend_score         DOUBLE PRECISION NOT NULL DEFAULT 0,
    forecast_next_7_days DOUBLE PRECISION NOT NULL DEFAULT 0
);

-- Unique constraint: only one row per (offering, day)
CREATE UNIQUE INDEX uq_daily_metric_offering_date
    ON recommendation_daily_metrics(offering_id, date);

CREATE INDEX idx_daily_metrics_date        ON recommendation_daily_metrics(date);
CREATE INDEX idx_daily_metrics_offering_id ON recommendation_daily_metrics(offering_id);
CREATE INDEX idx_daily_metrics_trend_score ON recommendation_daily_metrics(trend_score DESC);
