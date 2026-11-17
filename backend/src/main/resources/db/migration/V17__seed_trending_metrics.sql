-- HU-22: demo trending data so /api/ai/trending shows results out of the box in every environment.
-- Dates are relative to the day the migration runs (last 14 days), so they fall inside the
-- 14-day window DemandAnalyticsService reads (recent = last 7 days, previous = the 7 before).
-- Idempotent: ON CONFLICT keeps any real metric already recorded for that offering/day.
-- trend_score and forecast_next_7_days stay 0: DemandAnalyticsService computes them on read.

INSERT INTO recommendation_daily_metrics
    (id, date, offering_id, recommendations, unique_users, bookings, cancellations,
     conversion_rate, trend_score, forecast_next_7_days)
SELECT gen_random_uuid(),
       CURRENT_DATE - d,
       p.offering_id,
       v.recs,
       v.recs / 2 + v.bks,
       v.bks,
       c.canc,
       CASE WHEN v.recs > 0 THEN GREATEST(v.bks - c.canc, 0)::double precision / v.recs ELSE 0 END,
       0,
       0
FROM (VALUES
    -- offering_id, recs/day previous 7d, recs/day recent 7d, bookings/day previous, bookings/day recent
    ('11111111-1111-1111-1111-111111111111'::uuid,  8, 14, 1, 3),  -- Java Backend: strong growth
    ('16161616-1616-1616-1616-161616161616'::uuid,  4, 12, 1, 3),  -- Generative AI & RAG: rising fast
    ('22222222-2222-2222-2222-222222222222'::uuid,  7, 10, 1, 2),  -- Angular: steady growth
    ('77777777-7777-7777-7777-777777777777'::uuid,  5,  9, 0, 2),  -- Kubernetes: new demand
    ('55555555-5555-5555-5555-555555555555'::uuid,  6,  8, 1, 2),  -- Node.js & NestJS
    ('99999999-9999-9999-9999-999999999999'::uuid,  5,  7, 1, 1),  -- AWS & Terraform
    ('12121212-1212-1212-1212-121212121212'::uuid,  6,  6, 2, 2),  -- PostgreSQL tuning: flat
    ('cccccccc-cccc-cccc-cccc-cccccccccccc'::uuid,  9,  5, 3, 1)   -- Flutter: declining
) AS p(offering_id, recs_prev, recs_recent, bks_prev, bks_recent)
CROSS JOIN generate_series(0, 13) AS d
CROSS JOIN LATERAL (SELECT
        (CASE WHEN d <= 6 THEN p.recs_recent ELSE p.recs_prev END) + (d % 3) AS recs,
        CASE WHEN d <= 6 THEN p.bks_recent ELSE p.bks_prev END AS bks
) AS v
CROSS JOIN LATERAL (SELECT CASE WHEN d % 6 = 0 AND v.bks > 1 THEN 1 ELSE 0 END AS canc) AS c
WHERE EXISTS (SELECT 1 FROM offerings o WHERE o.id = p.offering_id)
ON CONFLICT (offering_id, date) DO NOTHING;
