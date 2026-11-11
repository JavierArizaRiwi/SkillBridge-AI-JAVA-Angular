"""
reader.py — reads JSONL event files into a Spark DataFrame with schema enforcement.

Each line is a BusinessEvent envelope as defined in docs/contracts/analytics-events.md §1.
Lines that are not parseable JSON are dropped with a warning (not fatal for the job).
"""
from __future__ import annotations

from pathlib import Path

from pyspark.sql import DataFrame, SparkSession
from pyspark.sql import functions as F
from pyspark.sql.types import (
    ArrayType,
    DoubleType,
    LongType,
    StringType,
    StructField,
    StructType,
    TimestampType,
)

# ---------------------------------------------------------------------------
# Shared sub-schemas
# ---------------------------------------------------------------------------

RECOMMENDED_OFFERING_SCHEMA = StructType([
    StructField("offeringId", StringType(), nullable=True),
    StructField("score", DoubleType(), nullable=True),
])

# ---------------------------------------------------------------------------
# Payload schemas (additive — unknown fields are silently ignored by Spark)
# ---------------------------------------------------------------------------

RECOMMENDATION_GENERATED_SCHEMA = StructType([
    StructField("recommendationId", StringType(), nullable=True),
    StructField("userId", StringType(), nullable=True),
    StructField("inputType", StringType(), nullable=True),
    StructField("offerings", ArrayType(RECOMMENDED_OFFERING_SCHEMA), nullable=True),
    StructField("provider", StringType(), nullable=True),
    StructField("model", StringType(), nullable=True),
    StructField("latencyMs", LongType(), nullable=True),
])

BOOKING_CREATED_SCHEMA = StructType([
    StructField("bookingId", StringType(), nullable=True),
    StructField("offeringId", StringType(), nullable=True),
    StructField("customerId", StringType(), nullable=True),
    StructField("scheduledAt", StringType(), nullable=True),
    StructField("status", StringType(), nullable=True),
    StructField("recommendationId", StringType(), nullable=True),  # v2, nullable for v1
])

BOOKING_CANCELLED_SCHEMA = StructType([
    StructField("bookingId", StringType(), nullable=True),
    StructField("offeringId", StringType(), nullable=True),
    StructField("customerId", StringType(), nullable=True),
    StructField("status", StringType(), nullable=True),
    StructField("recommendationId", StringType(), nullable=True),  # v2, nullable for v1
])

# ---------------------------------------------------------------------------
# Envelope schema
# ---------------------------------------------------------------------------

ENVELOPE_SCHEMA = StructType([
    StructField("eventId", StringType(), nullable=False),
    StructField("eventType", StringType(), nullable=False),
    StructField("aggregateId", StringType(), nullable=True),
    StructField("aggregateType", StringType(), nullable=True),
    StructField("occurredAt", StringType(), nullable=False),   # read as string, cast later
    StructField("correlationId", StringType(), nullable=True),
    StructField("version", LongType(), nullable=True),
    StructField("payload", StringType(), nullable=True),        # raw JSON string; parsed per type
])


def read_events(spark: SparkSession, dataset_dir: str | Path) -> DataFrame:
    """
    Read all events-*.jsonl files in *dataset_dir* into a Spark DataFrame.

    Returns a DataFrame with ENVELOPE_SCHEMA columns plus a parsed
    ``occurredAt`` timestamp column (UTC). Lines that cannot be parsed
    are dropped.
    """
    pattern = str(Path(dataset_dir) / "events-*.jsonl")
    raw = spark.read.text(pattern)

    # Parse each line as a JSON envelope.  from_json with a schema is forgiving:
    # it returns null for unparseable lines rather than raising.
    df = raw.select(
        F.from_json(F.col("value"), ENVELOPE_SCHEMA).alias("e")
    ).select("e.*")

    # Drop rows where essential fields are null (unparseable lines produce null eventId)
    df = df.filter(F.col("eventId").isNotNull() & F.col("eventType").isNotNull())

    # Cast occurredAt to timestamp (ISO-8601)
    df = df.withColumn("occurredAt", F.to_timestamp("occurredAt"))
    df = df.filter(F.col("occurredAt").isNotNull())

    return df
