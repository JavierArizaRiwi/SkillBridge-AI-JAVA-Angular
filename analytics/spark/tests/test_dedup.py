"""Tests for deterministic deduplication (dedup.py)."""
from __future__ import annotations

from datetime import datetime, timezone

from pyspark.sql import SparkSession
from pyspark.sql.types import StringType, StructField, StructType, TimestampType

from skillbridge_analytics.dedup import deduplicate


def _df(spark: SparkSession, rows: list[tuple]):
    schema = StructType([
        StructField("eventId", StringType()),
        StructField("occurredAt", TimestampType()),
        StructField("eventType", StringType()),
    ])
    return spark.createDataFrame(rows, schema)


def test_no_duplicates_unchanged(spark):
    rows = [
        ("e1", datetime(2026, 11, 1, 10, tzinfo=timezone.utc), "BookingCreated"),
        ("e2", datetime(2026, 11, 1, 11, tzinfo=timezone.utc), "BookingCreated"),
    ]
    result = deduplicate(_df(spark, rows))
    assert result.count() == 2


def test_exact_duplicates_keeps_one(spark):
    t = datetime(2026, 11, 1, 10, tzinfo=timezone.utc)
    rows = [
        ("e1", t, "BookingCreated"),
        ("e1", t, "BookingCreated"),
        ("e1", t, "BookingCreated"),
    ]
    result = deduplicate(_df(spark, rows))
    assert result.count() == 1


def test_tiebreak_on_earliest_occurred_at(spark):
    rows = [
        ("e1", datetime(2026, 11, 1, 12, tzinfo=timezone.utc), "BookingCreated"),
        ("e1", datetime(2026, 11, 1, 10, tzinfo=timezone.utc), "BookingCreated"),
    ]
    result = deduplicate(_df(spark, rows))
    assert result.count() == 1
    kept = result.collect()[0]
    # Spark returns naive datetimes (UTC) — compare hour to avoid tz-aware vs naive mismatch
    assert kept["occurredAt"].hour == 10
