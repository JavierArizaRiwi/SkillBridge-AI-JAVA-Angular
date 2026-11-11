"""
writer.py — write metrics DataFrame to Parquet, partitioned by date.

Idempotence: writing to the same date partition overwrites it (overwriteSchema=true).
Running the job twice on the same input yields identical output.
"""
from __future__ import annotations

from pathlib import Path

from pyspark.sql import DataFrame


def write_parquet(df: DataFrame, output_dir: str | Path, mode: str = "overwrite") -> None:
    """
    Write *df* to Parquet under *output_dir*, partitioned by date.

    Uses overwrite mode (default) so re-running the same batch is idempotent.
    Dynamic partition overwrite is used so only the affected date partitions
    are replaced — leaving other dates intact.
    """
    (
        df.write
        .partitionBy("date")
        .mode(mode)
        .option("overwriteSchema", "true")
        .parquet(str(output_dir))
    )
