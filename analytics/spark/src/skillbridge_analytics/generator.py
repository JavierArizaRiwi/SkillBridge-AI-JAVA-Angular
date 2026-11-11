"""
generator.py — deterministic synthetic dataset generator.

Produces events-*.jsonl + manifest.json in a target directory.
Fixed seed guarantees identical output for the same parameters, so the
comparator can run against the same golden dataset reproducibly.

Usage:
    python -m skillbridge_analytics.generator \\
        --out data/sample/ \\
        --offerings 3 \\
        --events 500 \\
        --seed 42 \\
        [--duplicate-pct 0.25]   # fraction of events to duplicate (default 0)
"""
from __future__ import annotations

import argparse
import json
import random
import sys
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path


def _iso(dt: datetime) -> str:
    return dt.strftime("%Y-%m-%dT%H:%M:%SZ")


def _make_offering_ids(n: int, rng: random.Random) -> list[str]:
    return [str(uuid.UUID(int=rng.getrandbits(128))) for _ in range(n)]


def _envelope(event_id: str, event_type: str, aggregate_id: str,
              occurred_at: datetime, payload: dict) -> dict:
    return {
        "eventId": event_id,
        "eventType": event_type,
        "aggregateId": aggregate_id,
        "aggregateType": "Recommendation" if "Recommendation" in event_type else "Booking",
        "occurredAt": _iso(occurred_at),
        "correlationId": str(uuid.uuid4()),
        "version": 2,
        "payload": payload,
    }


def generate(
    out_dir: str | Path,
    n_offerings: int = 3,
    offerings: int | None = None,        # short alias for n_offerings (tests use this)
    n_events: int = 500,
    events: int | None = None,           # short alias for n_events (tests use this)
    seed: int = 42,
    duplicate_pct: float = 0.0,
    base_date: datetime | None = None,
) -> dict:
    """
    Generate a synthetic dataset and return the manifest dict.

    *out_dir* is created if it does not exist.
    Accepts both ``n_offerings``/``n_events`` (original names) and
    ``offerings``/``events`` (short aliases used by test helpers).
    """
    # Resolve short aliases so both calling conventions work
    if offerings is not None:
        n_offerings = offerings
    if events is not None:
        n_events = events

    rng = random.Random(seed)
    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    base = base_date or datetime(2026, 11, 1, tzinfo=timezone.utc)
    offering_ids = _make_offering_ids(n_offerings, rng)
    user_ids = [str(uuid.UUID(int=rng.getrandbits(128))) for _ in range(max(5, n_events // 10))]

    event_list: list[dict] = []

    for _ in range(n_events):
        t = base + timedelta(
            days=rng.randint(0, 13),
            hours=rng.randint(0, 23),
            minutes=rng.randint(0, 59),
        )
        etype = rng.choice(
            ["RecommendationGenerated"] * 5
            + ["BookingCreated"] * 3
            + ["BookingCancelled"] * 2
        )
        eid = str(uuid.UUID(int=rng.getrandbits(128)))
        uid = rng.choice(user_ids)
        oid = rng.choice(offering_ids)

        if etype == "RecommendationGenerated":
            rec_id = str(uuid.UUID(int=rng.getrandbits(128)))
            n_recommended = rng.randint(1, min(3, n_offerings))
            recs = rng.sample(offering_ids, n_recommended)
            payload = {
                "recommendationId": rec_id,
                "userId": uid,
                "inputType": rng.choice(["TEXT", "VOICE", "IMAGE"]),
                "offerings": [
                    {"offeringId": o, "score": round(rng.uniform(0.5, 1.0), 3)}
                    for o in recs
                ],
                "provider": "gemini",
                "model": "gemini-3.8-flash",
                "latencyMs": rng.randint(100, 2000),
            }
            agg_id = rec_id
        elif etype == "BookingCreated":
            rec_id = str(uuid.UUID(int=rng.getrandbits(128))) if rng.random() > 0.5 else None
            payload = {
                "bookingId": str(uuid.UUID(int=rng.getrandbits(128))),
                "offeringId": oid,
                "customerId": uid,
                "scheduledAt": _iso(t + timedelta(days=30)),
                "status": "CREATED",
                "recommendationId": rec_id,
            }
            agg_id = payload["bookingId"]
        else:  # BookingCancelled
            rec_id = str(uuid.UUID(int=rng.getrandbits(128))) if rng.random() > 0.5 else None
            payload = {
                "bookingId": str(uuid.UUID(int=rng.getrandbits(128))),
                "offeringId": oid,
                "customerId": uid,
                "status": "CANCELLED",
                "recommendationId": rec_id,
            }
            agg_id = payload["bookingId"]

        event_list.append(_envelope(eid, etype, agg_id, t, payload))

    # Inject duplicates (same eventId + payload → pure duplicates)
    if duplicate_pct > 0:
        n_dups = int(len(event_list) * duplicate_pct)
        dups = [dict(e) for e in rng.choices(event_list, k=n_dups)]
        event_list.extend(dups)
        rng.shuffle(event_list)

    # Write events-0001.jsonl
    jsonl_path = out_dir / "events-0001.jsonl"
    with jsonl_path.open("w", encoding="utf-8") as fh:
        for e in event_list:
            fh.write(json.dumps(e, ensure_ascii=False) + "\n")

    manifest = {
        "origin": "synthetic",
        "exportedAt": _iso(datetime.now(tz=timezone.utc)),
        "topics": ["recommendation-events", "booking-events"],
        "eventCount": len(event_list),
        "generator": {"name": "skillbridge_analytics.generator", "seed": seed},
    }
    (out_dir / "manifest.json").write_text(
        json.dumps(manifest, indent=2, ensure_ascii=False)
    )
    return manifest


def _cli(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(
        description="Generate synthetic SkillBridge analytics dataset"
    )
    p.add_argument("--out", required=True, help="Output directory")
    p.add_argument("--offerings", type=int, default=3)
    p.add_argument("--events", type=int, default=500)
    p.add_argument("--seed", type=int, default=42)
    p.add_argument(
        "--duplicate-pct", type=float, default=0.0,
        help="Fraction of events to duplicate (0.0 – 1.0)",
    )
    args = p.parse_args(argv)
    m = generate(
        args.out,
        n_offerings=args.offerings,
        n_events=args.events,
        seed=args.seed,
        duplicate_pct=args.duplicate_pct,
    )
    print(f"Generated {m['eventCount']} events → {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(_cli())
