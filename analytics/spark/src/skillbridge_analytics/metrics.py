"""
metrics.py — compute daily metrics per offering from deduplicated events.

Implements the normative definitions from docs/contracts/analytics-events.md §3.

Metrics produced per (offeringId, date):
  recommendations         — distinct recommendationIds where the offering appears
  unique_users            — distinct userIds across those recommendations
  bookings_total          — BookingCreated events for the offering
  cancellations_total     — BookingCancelled events for the offering
  bookings_attributed     — BookingCreated with non-null recommendationId
  cancellations_attributed— BookingCancelled with non-null recommendationId
  effective_attributed    — max(0, bookings_attributed - cancellations_attributed)
  conversion_rate         — effective_attributed / recommendations  (0.0 if recs == 0, UNCAPPED)
  total_booking_rate      — max(0, bookings_total-cancellations_total)/recommendations (0.0 if recs==0)
"""
from __future__ import annotations

from pyspark.sql import DataFrame
from pyspark.sql import functions as F
from pyspark.sql.types import StringType, DoubleType

from skillbridge_analytics.reader import (
    BOOKING_CANCELLED_SCHEMA,
    BOOKING_CREATED_SCHEMA,
    RECOMMENDATION_GENERATED_SCHEMA,
)


# ---------------------------------------------------------------------------
# helpers
# ---------------------------------------------------------------------------

def _parse_payload(df: DataFrame, schema) -> DataFrame:
    """Parse the raw payload JSON string using *schema*, return flattened DF."""
    return df.withColumn("p", F.from_json(F.col("payload"), schema)).select(
        "eventId", "occurredAt", "p.*"
    )


def _utc_date(df: DataFrame) -> DataFrame:
    """Add a 'date' column (UTC date string yyyy-MM-dd) derived from occurredAt."""
    return df.withColumn("date", F.to_date(F.col("occurredAt")))


# ---------------------------------------------------------------------------
# per-event-type aggregations
# ---------------------------------------------------------------------------

def _recommendation_metrics(df: DataFrame) -> DataFrame:
    """
    One row per (offeringId, date) counting distinct recommendations and users.

    RecommendationGenerated v2 carries an 'offerings' array; each element has
    offeringId and score.  v1 events (offerings == null) contribute nothing per
    offering (contract §5 rule 2).
    """
    recs = _parse_payload(
        df.filter(F.col("eventType") == "RecommendationGenerated"),
        RECOMMENDATION_GENERATED_SCHEMA,
    )
    recs = _utc_date(recs)

    # Explode offerings array — events without offerings are dropped (nulls filtered below)
    exploded = recs.select(
        "date",
        "recommendationId",
        "userId",
        F.explode_outer("offerings").alias("offering"),
    ).filter(F.col("offering.offeringId").isNotNull())

    return exploded.groupBy("date", F.col("offering.offeringId").alias("offeringId")).agg(
        F.countDistinct("recommendationId").alias("recommendations"),
        F.countDistinct("userId").alias("unique_users"),
    )


def _booking_metrics(df: DataFrame) -> DataFrame:
    """One row per (offeringId, date) with total and attributed booking counts."""
    created = _parse_payload(
        df.filter(F.col("eventType") == "BookingCreated"),
        BOOKING_CREATED_SCHEMA,
    )
    created = _utc_date(created).filter(F.col("offeringId").isNotNull())

    cancelled = _parse_payload(
        df.filter(F.col("eventType") == "BookingCancelled"),
        BOOKING_CANCELLED_SCHEMA,
    )
    cancelled = _utc_date(cancelled).filter(F.col("offeringId").isNotNull())

    created_agg = created.groupBy("date", "offeringId").agg(
        F.count("*").alias("bookings_total"),
        F.count(F.when(F.col("recommendationId").isNotNull(), 1)).alias("bookings_attributed"),
    )
    cancelled_agg = cancelled.groupBy("date", "offeringId").agg(
        F.count("*").alias("cancellations_total"),
        F.count(F.when(F.col("recommendationId").isNotNull(), 1)).alias("cancellations_attributed"),
    )

    return created_agg.join(cancelled_agg, ["date", "offeringId"], "full").fillna(0, subset=[
        "bookings_total", "bookings_attributed", "cancellations_total", "cancellations_attributed"
    ])


# ---------------------------------------------------------------------------
# public API
# ---------------------------------------------------------------------------

def compute(df: DataFrame) -> DataFrame:
    """
    Compute all daily metrics from a deduplicated, current-window DataFrame.

    Returns one row per (offeringId, date) with the full metrics schema.
    """
    rec = _recommendation_metrics(df)
    bkg = _booking_metrics(df)

    joined = rec.join(bkg, ["date", "offeringId"], "full").fillna(0, subset=[
        "recommendations", "unique_users",
        "bookings_total", "cancellations_total",
        "bookings_attributed", "cancellations_attributed",
    ])

    # effective_attributed = max(0, bookings_attributed - cancellations_attributed)
    joined = joined.withColumn(
        "effective_attributed",
        F.greatest(F.lit(0).cast("long"), F.col("bookings_attributed") - F.col("cancellations_attributed")),
    )

    # Derived rates (UNCAPPED — may exceed 1.0, per contract §3)
    joined = joined.withColumn(
        "conversion_rate",
        F.when(F.col("recommendations") > 0,
               F.col("effective_attributed") / F.col("recommendations").cast(DoubleType())
        ).otherwise(F.lit(0.0)),
    )
    effective_total = F.greatest(
        F.lit(0).cast("long"),
        F.col("bookings_total") - F.col("cancellations_total"),
    )
    joined = joined.withColumn(
        "total_booking_rate",
        F.when(F.col("recommendations") > 0,
               effective_total / F.col("recommendations").cast(DoubleType())
        ).otherwise(F.lit(0.0)),
    )

    return joined.select(
        "date",
        "offeringId",
        "recommendations",
        "unique_users",
        "bookings_total",
        "cancellations_total",
        "bookings_attributed",
        "cancellations_attributed",
        "effective_attributed",
        "conversion_rate",
        "total_booking_rate",
    )
