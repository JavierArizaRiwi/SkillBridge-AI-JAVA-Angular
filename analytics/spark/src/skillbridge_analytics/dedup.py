"""
dedup.py — deterministic deduplication by eventId.

Contract (analytics-events.md §5, rule 1):
  If two events share the same eventId but differ in other fields, keep the one
  with the smallest occurredAt. If occurredAt also ties, keep the one with the
  lexicographically smallest canonical JSON (smallest value column acts as a
  stable tiebreaker on the raw line).

This is a pure, deterministic function over the DataFrame — no side effects.
"""
from __future__ import annotations

from pyspark.sql import DataFrame, Window
from pyspark.sql import functions as F


def deduplicate(df: DataFrame) -> DataFrame:
    """
    Return a DataFrame where each eventId appears at most once.

    Tiebreaker order (ascending, so row_number == 1 is the keeper):
      1. occurredAt  (earliest)
      2. value       (lexicographically smallest raw JSON — only present if
                      the reader passed the raw line through; falls back to
                      eventId itself if 'value' column is absent)
    """
    tiebreaker = "value" if "value" in df.columns else "eventId"

    window = Window.partitionBy("eventId").orderBy(
        F.col("occurredAt").asc(),
        F.col(tiebreaker).asc(),
    )
    deduped = (
        df.withColumn("_rn", F.row_number().over(window))
        .filter(F.col("_rn") == 1)
        .drop("_rn")
    )
    return deduped
