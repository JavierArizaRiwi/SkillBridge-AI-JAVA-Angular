"""
manifest.py — reads and validates manifest.json for a JSONL dataset.

Contract: docs/contracts/analytics-events.md §4.
Rules enforced:
  - Required fields: origin, exportedAt, topics, eventCount.
  - 'origin' must be 'production' or 'synthetic'.
  - Mixing production and synthetic datasets is forbidden.
  - 'generator' key may only be present in synthetic datasets.
"""
from __future__ import annotations

import json
from pathlib import Path
from typing import Literal

VALID_ORIGINS: set[str] = {"production", "synthetic"}


class ManifestError(ValueError):
    """Raised when a manifest.json violates the analytics-events contract."""


def load(dataset_dir: str | Path) -> dict:
    """
    Load and validate the manifest.json in *dataset_dir*.

    Returns the parsed manifest dict on success.
    Raises ManifestError on any violation.
    """
    path = Path(dataset_dir) / "manifest.json"
    if not path.exists():
        raise ManifestError(f"manifest.json not found in {dataset_dir}")

    with path.open(encoding="utf-8") as fh:
        try:
            manifest = json.load(fh)
        except json.JSONDecodeError as exc:
            raise ManifestError(f"manifest.json is not valid JSON: {exc}") from exc

    _validate(manifest, path)
    return manifest


def _validate(manifest: dict, path: Path) -> None:
    for field in ("origin", "exportedAt", "topics", "eventCount"):
        if field not in manifest:
            raise ManifestError(f"manifest.json missing required field '{field}' in {path}")

    origin: str = manifest["origin"]
    if origin not in VALID_ORIGINS:
        raise ManifestError(
            f"manifest.json has invalid origin '{origin}'; must be one of {VALID_ORIGINS}"
        )

    if origin == "production" and "generator" in manifest:
        raise ManifestError(
            "manifest.json: 'generator' key is only allowed for synthetic datasets"
        )


def assert_no_mixed_origins(manifests: list[dict]) -> None:
    """
    Assert that all manifests in a multi-dataset run share the same origin.
    Mixing 'production' and 'synthetic' is forbidden (contract §4).
    """
    origins = {m["origin"] for m in manifests}
    if len(origins) > 1:
        raise ManifestError(
            f"Mixed dataset origins detected: {origins}. "
            "production and synthetic datasets must never be combined."
        )
