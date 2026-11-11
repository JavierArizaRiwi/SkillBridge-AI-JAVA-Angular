"""
Tests for the metrics computation (metrics.py) — covers the golden dataset scenario
described in Scenario 1.1 of the spec.
"""
from __future__ import annotations

import json
from datetime import datetime, timezone
from pathlib import Path

import pytest
from pyspark.sql import SparkSession
from pyspark.sql.types import LongType, StringType, StructField, StructType, TimestampType

from skillbridge_analytics.metrics import compute
from skillbridge_analytics.reader import read_events
from skillbridge_analytics.dedup import deduplicate
from skillbridge_analytics.generator import generate


OFFERING_A = "11111111-1111-1111-1111-111111111111"
DATE_STR    = "2026-10-20"


def _make_golden_dataset(tmp_path: Path) -> Path:
    """
    Build the golden dataset described in Scenario 1.1:
      - 200 RecommendationGenerated, each including OFFERING_A
      - 100 BookingCreated for OFFERING_A, 50 attributed
      - 20  BookingCancelled for OFFERING_A, 10 attributed
    All on DATE_STR (UTC).
    """
    import uuid, random
    rng = random.Random(99)
    t_base = datetime(2026, 10, 20, 10, tzinfo=timezone.utc)
    events = []

    def ts(offset_min=0):
        from datetime import timedelta
        return (t_base + timedelta(minutes=offset_min)).strftime("%Y-%m-%dT%H:%M:%SZ")

    # 200 RecommendationGenerated
    for i in range(200):
        rec_id = str(uuid.UUID(int=rng.getrandbits(128)))
        events.append({
            "eventId": str(uuid.UUID(int=rng.getrandbits(128))),
            "eventType": "RecommendationGenerated",
            "aggregateId": rec_id,
            "aggregateType": "Recommendation",
            "occurredAt": ts(i),
            "correlationId": str(uuid.uuid4()),
            "version": 2,
            "payload": json.dumps({
                "recommendationId": rec_id,
                "userId": str(uuid.UUID(int=rng.getrandbits(128))),
                "inputType": "TEXT",
                "offerings": [{"offeringId": OFFERING_A, "score": 0.9}],
                "provider": None,
                "model": None,
                "latencyMs": 300,
            }),
        })

    # 100 BookingCreated — 50 attributed
    for i in range(100):
        attributed = i < 50
        events.append({
            "eventId": str(uuid.UUID(int=rng.getrandbits(128))),
            "eventType": "BookingCreated",
            "aggregateId": str(uuid.UUID(int=rng.getrandbits(128))),
            "aggregateType": "Booking",
            "occurredAt": ts(300 + i),
            "correlationId": str(uuid.uuid4()),
            "version": 2,
            "payload": json.dumps({
                "bookingId": str(uuid.UUID(int=rng.getrandbits(128))),
                "offeringId": OFFERING_A,
                "customerId": str(uuid.UUID(int=rng.getrandbits(128))),
                "scheduledAt": ts(1000 + i),
                "status": "CREATED",
                "recommendationId": str(uuid.UUID(int=rng.getrandbits(128))) if attributed else None,
            }),
        })

    # 20 BookingCancelled — 10 attributed
    for i in range(20):
        attributed = i < 10
        events.append({
            "eventId": str(uuid.UUID(int=rng.getrandbits(128))),
            "eventType": "BookingCancelled",
            "aggregateId": str(uuid.UUID(int=rng.getrandbits(128))),
            "aggregateType": "Booking",
            "occurredAt": ts(500 + i),
            "correlationId": str(uuid.uuid4()),
            "version": 2,
            "payload": json.dumps({
                "bookingId": str(uuid.UUID(int=rng.getrandbits(128))),
                "offeringId": OFFERING_A,
                "customerId": str(uuid.UUID(int=rng.getrandbits(128))),
                "status": "CANCELLED",
                "recommendationId": str(uuid.UUID(int=rng.getrandbits(128))) if attributed else None,
            }),
        })

    out = tmp_path / "golden"
    out.mkdir()
    with (out / "events-0001.jsonl").open("w") as fh:
        for e in events:
            fh.write(json.dumps(e) + "\n")
    manifest = {
        "origin": "synthetic",
        "exportedAt": "2026-11-10T10:00:00Z",
        "topics": ["booking-events", "recommendation-events"],
        "eventCount": len(events),
        "generator": {"name": "test", "seed": 99},
    }
    (out / "manifest.json").write_text(json.dumps(manifest))
    return out


def test_golden_scenario_1_1(spark, tmp_path):
    """Scenario 1.1: exact aggregates for 200 recs, 100 bookings (50 attr), 20 cancels (10 attr)."""
    dataset = _make_golden_dataset(tmp_path)
    df = read_events(spark, dataset)
    df = deduplicate(df)
    result = compute(df)

    row = result.filter(result.offeringId == OFFERING_A).collect()
    assert len(row) == 1, "Expected exactly one row for OFFERING_A"
    r = row[0]

    assert r["recommendations"]          == 200
    assert r["bookings_total"]           == 100
    assert r["cancellations_total"]      == 20
    assert r["bookings_attributed"]      == 50
    assert r["cancellations_attributed"] == 10
    assert r["effective_attributed"]     == 40          # 50 - 10
    assert abs(r["conversion_rate"]    - 0.20) < 1e-9  # 40/200
    assert abs(r["total_booking_rate"] - 0.40) < 1e-9  # 80/200


def test_idempotent_with_duplicates(spark, tmp_path):
    """Scenario 1.2: 25% duplicates — dedup must yield same metrics as the clean dataset."""
    # Generate clean and duplicate datasets with the same seed
    clean = tmp_path / "clean"
    with_dups = tmp_path / "dups"
    generate(clean, offerings=3, events=200, seed=7, duplicate_pct=0.0)
    generate(with_dups, offerings=3, events=200, seed=7, duplicate_pct=0.25)

    def run(d):
        return compute(deduplicate(read_events(spark, d))).orderBy("date", "offeringId").collect()

    r_clean = run(clean)
    r_dups  = run(with_dups)

    assert len(r_clean) == len(r_dups), "Row count must be identical after deduplication"
    for a, b in zip(r_clean, r_dups):
        assert a["offeringId"]            == b["offeringId"]
        assert a["recommendations"]       == b["recommendations"]
        assert a["bookings_total"]        == b["bookings_total"]
        assert a["bookings_attributed"]   == b["bookings_attributed"]


def test_empty_batch_returns_no_rows(spark, tmp_path):
    """Scenario 1.3a: empty JSONL file → empty metrics."""
    d = tmp_path / "empty"
    d.mkdir()
    (d / "events-0001.jsonl").write_text("")
    (d / "manifest.json").write_text(json.dumps({
        "origin": "synthetic", "exportedAt": "2026-11-10T10:00:00Z",
        "topics": [], "eventCount": 0,
        "generator": {"name": "test", "seed": 0},
    }))
    df = read_events(spark, d)
    result = compute(df)
    assert result.count() == 0


def test_zero_recommendations_gives_zero_conversion(spark, tmp_path):
    """Scenario 1.3b: bookings with no recommendations → conversionRate = 0.0."""
    import uuid, random
    rng = random.Random(5)
    oid = str(uuid.UUID(int=rng.getrandbits(128)))
    events = []
    for _ in range(5):
        events.append({
            "eventId": str(uuid.UUID(int=rng.getrandbits(128))),
            "eventType": "BookingCreated",
            "aggregateId": str(uuid.UUID(int=rng.getrandbits(128))),
            "aggregateType": "Booking",
            "occurredAt": "2026-11-01T10:00:00Z",
            "correlationId": "c",
            "version": 2,
            "payload": json.dumps({
                "bookingId": str(uuid.UUID(int=rng.getrandbits(128))),
                "offeringId": oid,
                "customerId": str(uuid.UUID(int=rng.getrandbits(128))),
                "scheduledAt": "2026-12-01T10:00:00Z",
                "status": "CREATED",
                "recommendationId": None,
            }),
        })
    d = tmp_path / "norecs"
    d.mkdir()
    with (d / "events-0001.jsonl").open("w") as fh:
        for e in events:
            fh.write(json.dumps(e) + "\n")
    (d / "manifest.json").write_text(json.dumps({
        "origin": "synthetic", "exportedAt": "2026-11-10T10:00:00Z",
        "topics": ["booking-events"], "eventCount": len(events),
        "generator": {"name": "test", "seed": 5},
    }))
    df = read_events(spark, d)
    result = compute(deduplicate(df))
    row = result.filter(result.offeringId == oid).collect()[0]
    assert row["recommendations"] == 0
    assert row["conversion_rate"] == 0.0


def test_midnight_utc_boundary(spark, tmp_path):
    """Edge case: events at 23:59:59Z vs 00:00:00Z must land in different date buckets."""
    import uuid
    oid = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
    events = [
        {
            "eventId": "00000001-0000-0000-0000-000000000001",
            "eventType": "BookingCreated", "aggregateId": "b1", "aggregateType": "Booking",
            "occurredAt": "2026-11-01T23:59:59Z", "correlationId": "c", "version": 2,
            "payload": json.dumps({"bookingId": "b1", "offeringId": oid,
                "customerId": "u1", "scheduledAt": "2026-12-01T10:00:00Z",
                "status": "CREATED", "recommendationId": None}),
        },
        {
            "eventId": "00000001-0000-0000-0000-000000000002",
            "eventType": "BookingCreated", "aggregateId": "b2", "aggregateType": "Booking",
            "occurredAt": "2026-11-02T00:00:00Z", "correlationId": "c", "version": 2,
            "payload": json.dumps({"bookingId": "b2", "offeringId": oid,
                "customerId": "u2", "scheduledAt": "2026-12-02T10:00:00Z",
                "status": "CREATED", "recommendationId": None}),
        },
    ]
    d = tmp_path / "midnight"
    d.mkdir()
    with (d / "events-0001.jsonl").open("w") as fh:
        for e in events:
            fh.write(json.dumps(e) + "\n")
    (d / "manifest.json").write_text(json.dumps({
        "origin": "synthetic", "exportedAt": "2026-11-10T10:00:00Z",
        "topics": ["booking-events"], "eventCount": 2,
        "generator": {"name": "test", "seed": 0},
    }))
    result = compute(read_events(spark, d)).orderBy("date").collect()
    assert len(result) == 2
    dates = [str(r["date"]) for r in result]
    assert "2026-11-01" in dates
    assert "2026-11-02" in dates
