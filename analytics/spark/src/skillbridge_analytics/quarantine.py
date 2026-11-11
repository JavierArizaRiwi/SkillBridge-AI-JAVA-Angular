"""
quarantine.py — split events into current and future (quarantine).

Contract (analytics-events.md §5, rule 3):
  Events with occurredAt strictly after the batch cut-off instant are moved to
  quarantine/.  They do NOT abort the job; their count is reported.
  The batch cut-off is passed explicitly so the split is deterministic.
"""
from __future__ import annotations

from datetime import datetime, timezone
from pathlib import Path

from pyspark.sql import DataFrame
from pyspark.sql import functions as F


def split(df: DataFrame, cut_off: datetime) -> tuple[DataFrame, DataFrame]:
    """
    Split *df* into (current, future) based on *cut_off* (UTC).

    Returns:
        current  — events with occurredAt <= cut_off
        future   — events with occurredAt >  cut_off  (to be written to quarantine/)
    """
    cut_ts = cut_off.astimezone(timezone.utc).replace(tzinfo=None)
    cut_col = F.lit(cut_ts).cast("timestamp")

    current = df.filter(F.col("occurredAt") <= cut_col)
    future  = df.filter(F.col("occurredAt") >  cut_col)
    return current, future


def write_quarantine(future_df: DataFrame, quarantine_dir: str | Path) -> int:
    """
    Write quarantined events to *quarantine_dir* as JSON lines.
    Returns the count of quarantined rows.
    """
    count = future_df.count()
    if count > 0:
        (
            future_df
            .coalesce(1)
            .write.mode("overwrite")
            .json(str(quarantine_dir))
        )
    return count
