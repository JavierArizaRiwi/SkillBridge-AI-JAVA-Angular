"""Shared pytest fixtures for the analytics engine tests."""
from __future__ import annotations

import os

import pytest
from pyspark.sql import SparkSession

# Force JVM (and Python) to treat all timestamps as UTC.
# Spark's to_timestamp / to_date functions follow the JVM default timezone
# unless overridden.  Setting TZ here (before SparkSession starts) makes
# the behaviour identical to production, where the container is also UTC.
os.environ.setdefault("TZ", "UTC")
# Apply to the current process immediately (affects time module + strftime)
try:
    import time as _time
    _time.tzset()
except AttributeError:
    pass  # Windows — tzset not available; TZ env-var still affects the JVM


@pytest.fixture(scope="session")
def spark() -> SparkSession:
    """Single SparkSession for all tests (session-scoped = created once)."""
    session = (
        SparkSession.builder
        .appName("skillbridge-analytics-tests")
        .master("local[2]")
        .config("spark.sql.shuffle.partitions", "2")
        .config("spark.sql.sources.partitionOverwriteMode", "dynamic")
        .config("spark.ui.enabled", "false")
        # Force UTC so timestamp parsing matches ISO-8601 Z strings regardless
        # of the JVM's local timezone (contract §3: dates are always UTC).
        .config("spark.sql.session.timeZone", "UTC")
        .getOrCreate()
    )
    session.sparkContext.setLogLevel("ERROR")
    yield session
    session.stop()
