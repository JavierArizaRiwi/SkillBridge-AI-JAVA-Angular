"""Tests for manifest.py (pure Python, no Spark needed)."""
from __future__ import annotations

import json
import pytest

from skillbridge_analytics.manifest import ManifestError, assert_no_mixed_origins, load


def test_load_valid_production(tmp_path):
    m = {
        "origin": "production",
        "exportedAt": "2026-11-10T10:00:00Z",
        "topics": ["booking-events"],
        "eventCount": 1000,
    }
    (tmp_path / "manifest.json").write_text(json.dumps(m))
    result = load(tmp_path)
    assert result["origin"] == "production"


def test_load_valid_synthetic(tmp_path):
    m = {
        "origin": "synthetic",
        "exportedAt": "2026-11-10T10:00:00Z",
        "topics": ["booking-events", "recommendation-events"],
        "eventCount": 500,
        "generator": {"name": "gen", "seed": 42},
    }
    (tmp_path / "manifest.json").write_text(json.dumps(m))
    result = load(tmp_path)
    assert result["generator"]["seed"] == 42


def test_missing_manifest(tmp_path):
    with pytest.raises(ManifestError, match="not found"):
        load(tmp_path)


def test_missing_required_field(tmp_path):
    m = {"origin": "production", "exportedAt": "2026-11-10T10:00:00Z", "topics": []}
    # missing 'eventCount'
    (tmp_path / "manifest.json").write_text(json.dumps(m))
    with pytest.raises(ManifestError, match="eventCount"):
        load(tmp_path)


def test_invalid_origin(tmp_path):
    m = {"origin": "unknown", "exportedAt": "2026-11-10T10:00:00Z", "topics": [], "eventCount": 0}
    (tmp_path / "manifest.json").write_text(json.dumps(m))
    with pytest.raises(ManifestError, match="invalid origin"):
        load(tmp_path)


def test_generator_key_in_production_forbidden(tmp_path):
    m = {
        "origin": "production",
        "exportedAt": "2026-11-10T10:00:00Z",
        "topics": [],
        "eventCount": 0,
        "generator": {"name": "oops", "seed": 1},
    }
    (tmp_path / "manifest.json").write_text(json.dumps(m))
    with pytest.raises(ManifestError, match="generator"):
        load(tmp_path)


def test_mixed_origins_raises():
    with pytest.raises(ManifestError, match="Mixed"):
        assert_no_mixed_origins([{"origin": "production"}, {"origin": "synthetic"}])


def test_same_origins_ok():
    assert_no_mixed_origins([{"origin": "synthetic"}, {"origin": "synthetic"}])  # no raise
