"""
job.py — PySpark batch job entrypoint for SkillBridge AI demand analytics (HU-25).

Usage (local):
    python -m skillbridge_analytics.job \\
        --dataset  /path/to/dataset/ \\
        --output   /path/to/output/ \\
        [--quarantine /path/to/quarantine/]  # default: <dataset>/quarantine/
        [--cut-off 2026-11-10T23:59:59Z]     # default: now (UTC)
        [--master  local[*]]                 # default: local[*]

Usage (spark-submit):
    spark-submit --master spark://host:7077 \\
        src/skillbridge_analytics/job.py \\
        --dataset hdfs://... --output hdfs://...

Exit codes:
    0  success
    1  usage or manifest error
    2  Spark / data error
"""
from __future__ import annotations

import argparse
import logging
import sys
from datetime import datetime, timezone
from pathlib import Path

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s — %(message)s",
    datefmt="%Y-%m-%dT%H:%M:%SZ",
)
log = logging.getLogger("skillbridge.analytics.job")


def _parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(
        description="SkillBridge AI demand analytics batch job (HU-25)"
    )
    p.add_argument("--dataset",    required=True, help="Path to the JSONL dataset directory")
    p.add_argument("--output",     required=True, help="Path to the Parquet output directory")
    p.add_argument("--quarantine", default=None,  help="Path for quarantined future events (default: <dataset>/quarantine/)")
    p.add_argument("--cut-off",    default=None,  help="Batch cut-off in ISO-8601 UTC (default: now)")
    p.add_argument("--master",     default="local[*]", help="Spark master URL (default: local[*])")
    return p.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = _parse_args(argv)
    dataset_dir  = Path(args.dataset)
    output_dir   = Path(args.output)
    quarantine_dir = Path(args.quarantine) if args.quarantine else dataset_dir / "quarantine"
    cut_off = (
        datetime.fromisoformat(args.cut_off.replace("Z", "+00:00"))
        if args.cut_off
        else datetime.now(tz=timezone.utc)
    )

    # Import here so the module is importable without PySpark for testing manifest/pure logic
    from pyspark.sql import SparkSession

    from skillbridge_analytics import manifest as mf
    from skillbridge_analytics.dedup import deduplicate
    from skillbridge_analytics.metrics import compute
    from skillbridge_analytics.quarantine import split, write_quarantine
    from skillbridge_analytics.reader import read_events
    from skillbridge_analytics.writer import write_parquet

    # --- 1. Validate manifest -------------------------------------------------
    try:
        m = mf.load(dataset_dir)
        log.info("Manifest loaded: origin=%s eventCount=%s topics=%s",
                 m["origin"], m["eventCount"], m["topics"])
    except mf.ManifestError as exc:
        log.error("Manifest validation failed: %s", exc)
        return 1

    # --- 2. Build SparkSession ------------------------------------------------
    spark = (
        SparkSession.builder
        .appName("skillbridge-analytics-hu25")
        .master(args.master)
        # Dynamic partition overwrite so only touched date-partitions are replaced
        .config("spark.sql.sources.partitionOverwriteMode", "dynamic")
        # Always interpret and store timestamps in UTC (contract §3)
        .config("spark.sql.session.timeZone", "UTC")
        .getOrCreate()
    )
    spark.sparkContext.setLogLevel("WARN")

    try:
        # --- 3. Read ---------------------------------------------------------
        log.info("Reading events from %s", dataset_dir)
        raw = read_events(spark, dataset_dir)
        total_raw = raw.count()
        log.info("Raw events loaded: %d", total_raw)

        # --- 4. Deduplicate --------------------------------------------------
        deduped = deduplicate(raw)
        total_deduped = deduped.count()
        log.info("After deduplication: %d (dropped %d duplicates)",
                 total_deduped, total_raw - total_deduped)

        # --- 5. Quarantine future events -------------------------------------
        current, future = split(deduped, cut_off)
        quarantined = write_quarantine(future, quarantine_dir)
        log.info("Quarantined %d future events (cut-off=%s)", quarantined, cut_off.isoformat())

        # --- 6. Compute metrics ----------------------------------------------
        log.info("Computing daily metrics per offering")
        metrics_df = compute(current)
        metrics_df.cache()
        rows = metrics_df.count()
        log.info("Metrics computed: %d (offeringId, date) rows", rows)

        # --- 7. Write Parquet ------------------------------------------------
        output_dir.mkdir(parents=True, exist_ok=True)
        log.info("Writing Parquet to %s", output_dir)
        write_parquet(metrics_df, output_dir)
        log.info("Job completed successfully")

    except Exception as exc:
        log.exception("Job failed: %s", exc)
        return 2
    finally:
        spark.stop()

    return 0


if __name__ == "__main__":
    sys.exit(main())
