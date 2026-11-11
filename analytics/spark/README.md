# SkillBridge AI — Demand Analytics PySpark Engine (HU-25)

Batch job that computes daily demand metrics per offering from exported
`BusinessEvent` JSONL files.  It is fully isolated from the transactional
backend: no OLTP connection, no Kafka dependency, no Spring Boot code.

---

## Table of contents

1. [When to use this engine](#1-when-to-use-this-engine)
2. [System requirements](#2-system-requirements)
3. [Quick start (local)](#3-quick-start-local)
4. [Running with Docker](#4-running-with-docker)
5. [Input format](#5-input-format)
6. [Output format](#6-output-format)
7. [Architecture & pipeline](#7-architecture--pipeline)
8. [Running tests](#8-running-tests)
9. [Integration points for my companion's work](#9-integration-points-for-my-companions-work)
10. [Known limitations](#10-known-limitations)

---

## 1. When to use this engine

| Condition | Recommendation |
|---|---|
| **≤ 500 000 events/day** AND **≤ 100 GB** historical JSONL | Use a DuckDB query or plain SQL — Spark start-up overhead is not worth it |
| **> 500 000 events/day** OR **> 100 GB** historical JSONL | Use this engine — Spark parallelism amortises cost |
| Multi-day historical backfill | Always use this engine |

The 500 k / 100 GB thresholds come from observed Spark cold-start overhead on
the target hardware (~10 s).  Below these thresholds a DuckDB script finishes
in well under 1 s.

---

## 2. System requirements

| Component | Minimum version | Notes |
|---|---|---|
| Python | 3.11 | 3.12 tested and supported |
| JDK | 17 | 21 (LTS) also works; bundled in the Docker image |
| PySpark | 3.5.4 | pinned in `requirements.txt` and `pyproject.toml` |
| Memory (driver) | 512 MB | 1 GB recommended for datasets > 1 M events |
| Docker | 24+ | Optional; only needed for containerised runs |

> **Note:** the Docker image (`Dockerfile`) bundles JDK 17 so no host JDK
> installation is required when using Docker.

---

## 3. Quick start (local)

```bash
# 1. Create a virtual environment and install dependencies
cd analytics/spark
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
pip install -e .

# 2. Generate a small synthetic dataset (optional — for testing)
TZ=UTC python -m skillbridge_analytics.generator \
    --out data/sample/ \
    --offerings 3 \
    --events 500 \
    --seed 42

# 3. Run the job
TZ=UTC python -m skillbridge_analytics.job \
    --dataset data/sample/ \
    --output data/output/ \
    --quarantine data/quarantine/

# 4. Inspect the Parquet output
python -c "
import pyspark.sql
spark = pyspark.sql.SparkSession.builder.master('local').getOrCreate()
spark.read.parquet('data/output/').show(5)
"
```

> **`TZ=UTC` is required** when running outside Docker on a machine with a
> non-UTC local timezone.  The Docker image is already set to UTC.

### spark-submit (standalone / cluster)

```bash
spark-submit \
    --master spark://host:7077 \
    src/skillbridge_analytics/job.py \
    --dataset hdfs://namenode/skillbridge/events/ \
    --output  hdfs://namenode/skillbridge/metrics/
```

### CLI reference

```
python -m skillbridge_analytics.job --help

Options:
  --dataset    PATH    Path to the JSONL dataset directory (required)
  --output     PATH    Path to the Parquet output directory (required)
  --quarantine PATH    Quarantine dir for future events (default: <dataset>/quarantine/)
  --cut-off    ISO8601 Batch cut-off in UTC (default: now)
  --master     URL     Spark master (default: local[*])
```

Exit codes: `0` success · `1` manifest/usage error · `2` Spark/data error.

---

## 4. Running with Docker

```bash
# Build the image (from the repository root)
docker build -t skillbridge-analytics analytics/spark/

# Run against a local dataset
docker run --rm \
    -v /path/to/dataset:/data/dataset:ro \
    -v /path/to/output:/data/output \
    -v /path/to/quarantine:/data/quarantine \
    skillbridge-analytics \
    --dataset /data/dataset \
    --output  /data/output \
    --quarantine /data/quarantine

# Or use the Docker Compose analytics profile (from the repo root)
# This is the recommended way in shared environments:
docker compose --profile analytics run analytics-spark
```

Memory limits are controlled by the `ANALYTICS_DRIVER_MEMORY_MB` environment
variable (default: `1024` = 1 GB):

```bash
docker compose --profile analytics run \
    -e ANALYTICS_DRIVER_MEMORY_MB=2048 \
    analytics-spark
```

Dataset/output directories are set via:

| Variable | Default |
|---|---|
| `ANALYTICS_DATASET_DIR` | `./analytics/spark/data/sample` |
| `ANALYTICS_OUTPUT_DIR` | `./analytics/spark/output` |
| `ANALYTICS_QUARANTINE_DIR` | `./analytics/spark/data/quarantine` |

---

## 5. Input format

```
<dataset>/
  manifest.json         ← required; describes the batch
  events-0001.jsonl     ← one BusinessEvent per line, UTF-8
  events-0002.jsonl     ← additional shards (optional)
```

### manifest.json

```json
{
  "origin": "production | synthetic",
  "exportedAt": "2026-11-09T12:00:00Z",
  "topics": ["booking-events", "recommendation-events"],
  "eventCount": 50000,
  "generator": { "name": "...", "seed": 0 }
}
```

`generator` is only allowed for `synthetic` datasets (validated at startup).

### Event envelope (BusinessEvent)

```json
{
  "eventId":       "uuid",
  "eventType":     "RecommendationGenerated | BookingCreated | BookingCancelled",
  "aggregateId":   "uuid",
  "aggregateType": "Recommendation | Booking",
  "occurredAt":    "2026-11-01T10:30:00Z",
  "correlationId": "uuid",
  "version":       2,
  "payload":       "{ ... }"
}
```

Lines that cannot be parsed as valid JSON are silently dropped (never fatal).
Full schema in `docs/contracts/analytics-events.md`.

---

## 6. Output format

```
<output>/
  date=2026-11-01/
    part-00001-….snappy.parquet
  date=2026-11-02/
    part-00002-….snappy.parquet
  …
```

One row per `(offeringId, date)`:

| Column | Type | Description |
|---|---|---|
| `date` | date | UTC calendar date of `occurredAt` (partition key) |
| `offeringId` | string | UUID of the offering |
| `recommendations` | long | Distinct `RecommendationGenerated` events including this offering |
| `unique_users` | long | Distinct `userId` values across those recommendations |
| `bookings_total` | long | `BookingCreated` events for this offering |
| `cancellations_total` | long | `BookingCancelled` events for this offering |
| `bookings_attributed` | long | `BookingCreated` with non-null `recommendationId` |
| `cancellations_attributed` | long | `BookingCancelled` with non-null `recommendationId` |
| `effective_attributed` | long | `max(0, bookings_attributed − cancellations_attributed)` |
| `conversion_rate` | double | `effective_attributed / recommendations` (0.0 if no recs) |
| `total_booking_rate` | double | `max(0, bookings_total − cancellations_total) / recommendations` |

`conversion_rate` may exceed 1.0 (bookings from one day attributed to
recommendations from another day — not truncated, per contract §3).

**Idempotence:** re-running the job on the same input overwrites only the
affected date partitions (`spark.sql.sources.partitionOverwriteMode=dynamic`).
Other partitions are left intact.

**Quarantine:** events with `occurredAt` strictly after the batch cut-off are
written to `<quarantine>/` as JSON and excluded from metrics.  The job
completes successfully and logs their count.

---

## 7. Architecture & pipeline

```
JSONL files
    │
    ▼
reader.py         ─── schema enforcement, drop malformed lines
    │
    ▼
manifest.py       ─── validate origin, required fields, no mixed datasets
    │
    ▼
dedup.py          ─── Window(partitionBy=eventId, orderBy=occurredAt ASC)
    │                   keeps earliest; ties broken by raw value column
    ▼
quarantine.py     ─── split(cut_off): current ≤ cut_off, future > cut_off
    │                   future events → write_quarantine(quarantine_dir/)
    ▼
metrics.py        ─── compute():
    │                   _recommendation_metrics()  → explode offerings array
    │                   _booking_metrics()          → created + cancelled aggs
    │                   full join on (date, offeringId)
    │                   derived: effective_attributed, conversion_rate, total_booking_rate
    ▼
writer.py         ─── write_parquet(output_dir, partitionBy="date", mode="overwrite")
```

All Spark operations are pure DataFrames — no RDD usage, no UDFs with Python
closures, no calls to external services.

---

## 8. Running tests

```bash
cd analytics/spark
source .venv/bin/activate   # or: TZ=UTC python -m pytest tests/

TZ=UTC python -m pytest tests/ -v
```

**Test suite (16 tests):**

| File | Tests | Covers |
|---|---|---|
| `test_manifest.py` | 7 | Manifest validation — all rules from §4 of the contract |
| `test_dedup.py` | 3 | Deduplication — no dups, exact dups, tiebreak by `occurredAt` |
| `test_metrics.py` | 6 | Golden dataset (Scenario 1.1), idempotence (1.2), empty batch (1.3a), zero-recs (1.3b), midnight UTC boundary |

> **`TZ=UTC` is required** when running tests outside Docker.  The JVM
> inherits the OS timezone at startup; without this, timestamp parsing shifts
> and date bucketing tests fail.

All 16 tests pass on Python 3.12 + PySpark 3.5.4 + OpenJDK 21.

---

## 9. Integration points for my companion's work

The following items are **out of scope for this engine** and belong to the
companion developer's frente:

| Item | Description | Integration point |
|---|---|---|
| Official synthetic data generator | Authoritative generator for QA and acceptance datasets | Should produce `events-*.jsonl` + `manifest.json` compatible with §4 of the contract. The internal `generator.py` in this module is a lightweight test helper, **not** the QA reference generator. |
| Reference engine | Independent Python/SQL implementation of the same metrics | Will run against the same JSONL files; must produce identical `(offeringId, date, metric)` values |
| Comparator | Spark output vs. reference output diff | Reads both Parquet outputs and fails on any numeric difference > 1e-9 |
| QA datasets | Shared golden datasets for acceptance tests | Must use the contract envelope schema (§1) and `manifest.json` format (§4) |
| Operational documentation | Docker Compose README, runbook | Will extend this README or add a companion `OPERATIONS.md` |

**No action required from this engine** for those items.  The interfaces are:
- Input: `events-*.jsonl` + `manifest.json` as documented in §5 above.
- Output: Parquet files partitioned by `date` as documented in §6 above.

---

## 10. Known limitations

- **spark-submit packaging not tested** in CI (no Spark cluster available).
  Local `local[*]` mode is fully verified.  Cluster mode is architecturally
  supported by `job.py --master` but requires a live cluster to validate.
- **Delta Lake dependency present** in `requirements.txt` (`delta-spark==3.3.0`)
  but not yet used by the job.  It was included anticipating future merge
  semantics — it has no runtime effect and can be removed if unused.
- **No `.gitignore` for `.venv/`** at module level — added via the root
  `.gitignore`.  Do not commit the `.venv/` directory.
