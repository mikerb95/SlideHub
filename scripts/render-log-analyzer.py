#!/usr/bin/env python3
"""Fetch and summarize Render logs for SlideHub services.

Usage examples:
  RENDER_API_KEY=... RENDER_OWNER_ID=... ./scripts/render-log-analyzer.py
  ./scripts/render-log-analyzer.py --minutes 60 --services slidehub-ui slidehub-ai

The script:
  1. Reads service names from render.yaml by default.
  2. Resolves each service through the Render API.
  3. Pulls logs for a time window.
  4. Filters probable errors and groups repeated failures.
  5. Prints a concise report and writes a JSON artifact under target/render-logs/.
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any, Iterable


ROOT_DIR = Path(__file__).resolve().parents[1]
DEFAULT_RENDER_YAML = ROOT_DIR / "render.yaml"
OUTPUT_DIR = ROOT_DIR / "target" / "render-logs"

ERROR_PATTERNS = [
    re.compile(r"\b(exception|error|fatal|panic|stacktrace|caused by)\b", re.IGNORECASE),
    re.compile(r"\b(5\d\d|bad gateway|service unavailable|gateway timeout|connection refused|timeout)\b", re.IGNORECASE),
    re.compile(r"\b(beancreationexception|unsatisfieddependencyexception|sqlsyntaxerrorexception|constraintviolationexception)\b", re.IGNORECASE),
]

UUID_RE = re.compile(r"\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b", re.IGNORECASE)
ISO_RE = re.compile(r"\b\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z\b")
NUM_RE = re.compile(r"\b\d+\b")
WS_RE = re.compile(r"\s+")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Analyze Render logs for SlideHub services.")
    parser.add_argument("--minutes", type=int, default=60, help="Time window in minutes (default: 60)")
    parser.add_argument("--limit", type=int, default=100, help="Max logs per service (default: 100)")
    parser.add_argument(
        "--log-type",
        choices=["app", "request", "build"],
        help="Optional Render log type filter. Use 'build' to inspect deploy/build logs only.",
    )
    parser.add_argument(
        "--services",
        nargs="*",
        help="Service names or IDs. Default: names discovered from render.yaml.",
    )
    parser.add_argument(
        "--render-yaml",
        default=str(DEFAULT_RENDER_YAML),
        help="Path to render.yaml (default: repo root render.yaml)",
    )
    parser.add_argument(
        "--owner-id",
        default=os.getenv("RENDER_OWNER_ID", ""),
        help="Render workspace/owner ID. Falls back to RENDER_OWNER_ID env var.",
    )
    parser.add_argument(
        "--output-dir",
        default=str(OUTPUT_DIR),
        help="Directory for JSON artifacts (default: target/render-logs)",
    )
    return parser.parse_args()


def read_render_service_names(render_yaml_path: Path) -> list[str]:
    if not render_yaml_path.exists():
        raise FileNotFoundError(f"render.yaml not found: {render_yaml_path}")

    names: list[str] = []
    expecting_name = False
    for raw_line in render_yaml_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if line.startswith("- type:"):
            expecting_name = True
            continue
        if expecting_name and line.startswith("name:"):
            _, value = line.split(":", 1)
            names.append(value.strip())
            expecting_name = False
            continue
        if line.startswith("-") and not line.startswith("- type:"):
            expecting_name = False
    return names


def http_get_json(url: str, api_key: str) -> Any:
    request = urllib.request.Request(
        url,
        headers={
            "Accept": "application/json",
            "Authorization": f"Bearer {api_key}",
        },
        method="GET",
    )
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            payload = response.read().decode("utf-8")
            return json.loads(payload)
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace") if exc.fp else ""
        raise RuntimeError(f"Render API HTTP {exc.code} for {url}: {body[:500]}") from exc


def unwrap_service_entry(entry: dict[str, Any]) -> dict[str, Any]:
    """Render APIs often return wrapped objects like {cursor, service: {...}}."""
    if not isinstance(entry, dict):
        return {}
    for key in ("service", "owner", "resource"):
        value = entry.get(key)
        if isinstance(value, dict):
            return value
    return entry


def list_services(api_key: str, name: str | None = None) -> list[dict[str, Any]]:
    query: dict[str, Any] = {"limit": 100}
    if name:
        query["name"] = name
    url = "https://api.render.com/v1/services?" + urllib.parse.urlencode(query, doseq=True)
    data = http_get_json(url, api_key)
    if isinstance(data, list):
        return [unwrap_service_entry(item) for item in data]
    if isinstance(data, dict):
        items = data.get("items") or data.get("services") or data.get("data") or []
        if isinstance(items, list):
            return [unwrap_service_entry(item) for item in items]
    return []


def resolve_service(api_key: str, service_ref: str) -> dict[str, Any]:
    if service_ref.startswith("srv-"):
        return {"id": service_ref, "name": service_ref}

    services = list_services(api_key, service_ref)
    exact = [svc for svc in services if svc.get("name") == service_ref]
    if exact:
        return exact[0]
    if services:
        return services[0]
    raise RuntimeError(f"Service not found in Render API: {service_ref}")


def fetch_logs(api_key: str, owner_id: str, resource_ids: Iterable[str], minutes: int, limit: int) -> dict[str, Any]:
    end = dt.datetime.now(dt.timezone.utc)
    start = end - dt.timedelta(minutes=minutes)
    query: list[tuple[str, str]] = [
        ("ownerId", owner_id),
        ("startTime", start.isoformat()),
        ("endTime", end.isoformat()),
        ("direction", "backward"),
        ("limit", str(limit)),
    ]
    # Optional filter for deploy/build-only analysis.
    # Keep the API call flexible: if no log type is requested we fetch all types.
    # The Render API accepts type=app|request|build.
    if getattr(fetch_logs, "log_type", None):
        query.append(("type", str(getattr(fetch_logs, "log_type"))))
    for resource_id in resource_ids:
        query.append(("resource", resource_id))

    url = "https://api.render.com/v1/logs?" + urllib.parse.urlencode(query)
    return http_get_json(url, api_key)


def is_error_log(message: str) -> bool:
    return any(pattern.search(message) for pattern in ERROR_PATTERNS)


def normalize_signature(message: str) -> str:
    first_line = message.splitlines()[0] if message else ""
    signature = ISO_RE.sub("<timestamp>", first_line)
    signature = UUID_RE.sub("<uuid>", signature)
    signature = NUM_RE.sub("<n>", signature)
    signature = WS_RE.sub(" ", signature).strip()
    return signature[:220]


def summarize_logs(service_name: str, log_items: list[dict[str, Any]]) -> dict[str, Any]:
    matches: list[dict[str, Any]] = []
    signatures: Counter[str] = Counter()
    latest_by_signature: dict[str, dict[str, Any]] = {}

    for item in log_items:
        message = str(item.get("message", ""))
        if not message:
            continue
        if is_error_log(message):
            signature = normalize_signature(message)
            signatures[signature] += 1
            latest_by_signature[signature] = item
            matches.append(item)

    grouped = [
        {
            "signature": signature,
            "count": count,
            "latest": latest_by_signature[signature],
        }
        for signature, count in signatures.most_common()
    ]

    return {
        "service": service_name,
        "error_count": len(matches),
        "top_errors": grouped[:10],
        "sample_logs": matches[:20],
    }


def main() -> int:
    args = parse_args()
    api_key = os.getenv("RENDER_API_KEY", "").strip()
    if not api_key:
        print("[ERROR] Set RENDER_API_KEY in the environment.", file=sys.stderr)
        return 1

    owner_id = args.owner_id.strip()
    if not owner_id:
        print("[ERROR] Set RENDER_OWNER_ID or pass --owner-id.", file=sys.stderr)
        return 1

    render_yaml = Path(args.render_yaml)
    service_refs = args.services or read_render_service_names(render_yaml)
    if not service_refs:
        print("[ERROR] No services found to analyze.", file=sys.stderr)
        return 1

    resolved_services: list[dict[str, Any]] = []
    for ref in service_refs:
        try:
            resolved = resolve_service(api_key, ref)
            resolved = unwrap_service_entry(resolved)
            resolved_services.append(resolved)
        except Exception as exc:
            print(f"[WARN] Could not resolve service '{ref}': {exc}", file=sys.stderr)

    if not resolved_services:
        print("[ERROR] None of the services could be resolved.", file=sys.stderr)
        return 1

    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    report: dict[str, Any] = {
        "generatedAt": dt.datetime.now(dt.timezone.utc).isoformat(),
        "minutes": args.minutes,
        "limit": args.limit,
        "services": [],
    }

    print(f"[INFO] Querying logs for {len(resolved_services)} services")
    setattr(fetch_logs, "log_type", args.log_type)
    for svc in resolved_services:
        service_name = svc.get("name", svc.get("id", "unknown"))
        resource_id = svc.get("id")
        print(f"[INFO] Fetching logs for {service_name} ({resource_id})")
        try:
            payload = fetch_logs(api_key, owner_id, [resource_id], args.minutes, args.limit)
        except Exception as exc:
            print(f"[WARN] Failed to fetch logs for {service_name}: {exc}", file=sys.stderr)
            continue

        if isinstance(payload, dict):
            log_items = payload.get("logs") or []
        else:
            log_items = []

        if not isinstance(log_items, list):
            log_items = []

        summary = summarize_logs(service_name, log_items)
        summary["resourceId"] = resource_id
        summary["logCount"] = len(log_items)
        report["services"].append(summary)

    artifact_path = output_dir / f"render-log-report-{dt.datetime.now().strftime('%Y%m%d-%H%M%S')}.json"
    artifact_path.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")

    print("\n=== Render Log Summary ===")
    print(f"Window: last {args.minutes} minutes")
    print(f"Artifact: {artifact_path}")
    for svc in report["services"]:
        print(f"\n[{svc['service']}] logs={svc['logCount']} probableErrors={svc['error_count']}")
        if not svc["top_errors"]:
            print("  no probable error signatures found")
            continue
        for entry in svc["top_errors"][:5]:
            latest_msg = str(entry["latest"].get("message", "")).splitlines()[0][:140]
            ts = entry["latest"].get("timestamp", "")
            print(f"  - x{entry['count']} {entry['signature']}")
            if latest_msg:
                print(f"    latest: {ts} | {latest_msg}")

    print("\n[INFO] Done.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
