#!/usr/bin/env python3
"""Read-only HTTP benchmark for the music API; Python standard library only."""

import argparse
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
import csv
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
import html
import http.client
import json
import math
import os
from pathlib import Path
import platform
import re
import statistics
import sys
import time
from urllib.parse import quote, urlencode, urlsplit
from uuid import UUID, uuid4


@dataclass(frozen=True)
class Endpoint:
    name: str
    path: str
    kind: str = "json"
    keys: tuple = ()


@dataclass
class Sample:
    endpoint: str
    status: int
    latency_ms: float
    ttfb_ms: float | None
    bytes: int
    error: str


class Client:
    """One persistent HTTP/1.1 connection per worker. No retries or redirects."""

    def __init__(self, base_url, token="", timeout=10, range_bytes=65536):
        self.url = urlsplit(base_url)
        self.token = token
        self.timeout = timeout
        self.range_bytes = range_bytes
        self.connection = None

    def close(self):
        if self.connection:
            self.connection.close()
            self.connection = None

    def request(self, endpoint):
        start = time.perf_counter()
        status, size, ttfb, payload = 0, 0, None, None
        error = ""
        try:
            if self.connection is None:
                factory = (http.client.HTTPSConnection if self.url.scheme == "https"
                           else http.client.HTTPConnection)
                self.connection = factory(self.url.hostname, self.url.port, timeout=self.timeout)
            headers = {"Accept": "audio/*" if endpoint.kind == "range" else "application/json",
                       "Accept-Encoding": "identity", "User-Agent": "music-benchmark/1"}
            if self.token:
                headers["Authorization"] = "Bearer " + self.token
            if endpoint.kind == "range":
                headers["Range"] = f"bytes=0-{self.range_bytes - 1}"
            self.connection.request("GET", self.url.path.rstrip("/") + endpoint.path, headers=headers)
            response = self.connection.getresponse()
            status = response.status
            # Include connect/TLS, server time and the first body byte in TTFB.
            first = response.read(1)
            ttfb = (time.perf_counter() - start) * 1000
            limit = self.range_bytes if endpoint.kind == "range" else 4 * 1024 * 1024
            body = first + response.read(limit)
            size = len(body)
            latency = (time.perf_counter() - start) * 1000
            if size > limit:
                error = "body_too_large"
            elif status != (206 if endpoint.kind == "range" else 200):
                error = "unexpected_status"
            elif endpoint.kind == "range":
                match = re.fullmatch(r"bytes 0-(\d+)/(\d+)", response.getheader("Content-Range", ""))
                if not match:
                    error = "invalid_content_range"
                else:
                    end, total = map(int, match.groups())
                    if total <= 0 or end != min(self.range_bytes, total) - 1 or size != end + 1:
                        error = "invalid_range_length"
                if not response.getheader("Content-Type", "").lower().startswith("audio/"):
                    error = "invalid_audio_type"
            else:
                if "application/json" not in response.getheader("Content-Type", "").lower():
                    error = "invalid_json_type"
                else:
                    payload = json.loads(body)
                    if endpoint.kind == "list":
                        if not isinstance(payload, list):
                            error = "invalid_json_shape"
                    elif not isinstance(payload, dict) or any(key not in payload for key in endpoint.keys):
                        error = "invalid_json_shape"
            content_length = response.getheader("Content-Length")
            if content_length is not None and int(content_length) != size and not error:
                error = "incomplete_body"
            if error:
                self.close()
        except (OSError, http.client.HTTPException, ValueError, UnicodeError) as exc:
            # Never persist exception messages, response bodies or authorization headers.
            error = type(exc).__name__
            latency = (time.perf_counter() - start) * 1000
            self.close()
        return Sample(endpoint.name, status, latency, ttfb, size, error), payload


def workload(client, scenario, query, track_id):
    home = Endpoint("home", "/home", keys=("tracks", "albums", "artists"))
    sample, catalog = client.request(home)
    if sample.error:
        raise ValueError(f"Preflight /home failed: {sample.error} (HTTP {sample.status})")
    endpoints = [home, Endpoint("search", "/search?" + urlencode({"q": query}),
                               keys=("tracks", "albums", "artists")),
                 Endpoint("tracks", "/tracks?" + urlencode({"query": query}), "list")]
    for kind in ("albums", "artists"):
        rows = catalog.get(kind)
        if not isinstance(rows, list):
            raise ValueError(f"Preflight /home has invalid {kind}")
        if rows:
            identifier = str(UUID(str(rows[0]["id"])))
            key = "album" if kind == "albums" else "artist"
            endpoints.append(Endpoint(key, f"/{kind}/{identifier}", keys=(key, "tracks")))
    if scenario in ("personalized", "streaming"):
        endpoints.extend([
            Endpoint("recommendations", "/recommendations?limit=20", "list"),
            Endpoint("library", "/me/library/tracks", "list"),
            Endpoint("playlists", "/me/playlists", "list"),
        ])
    if scenario == "streaming":
        if not track_id:
            tracks = catalog.get("tracks", [])
            track_id = next((row["id"] for row in tracks if row.get("playable") is True), None)
        if not track_id:
            raise ValueError("Streaming requires a playable catalog track or --track-id")
        identifier = quote(str(UUID(str(track_id))), safe="")
        endpoints.extend([
            Endpoint("playback", f"/tracks/{identifier}/playback", keys=("streamUrl", "durationSeconds")),
            Endpoint("audio_range", f"/tracks/{identifier}/stream", "range"),
        ])
    for endpoint in endpoints:
        sample, _ = client.request(endpoint)
        if sample.error:
            raise ValueError(f"Preflight {endpoint.name} failed: {sample.error} (HTTP {sample.status})")
    return endpoints


def run_batch(base_url, token, endpoints, concurrency, count, timeout, range_bytes):
    def worker(worker_id):
        client = Client(base_url, token, timeout, range_bytes)
        samples = []
        try:
            for index in range(worker_id, count, concurrency):
                sample, _ = client.request(endpoints[index % len(endpoints)])
                samples.append(sample)
        finally:
            client.close()
        return samples

    start = time.perf_counter()
    with ThreadPoolExecutor(max_workers=concurrency) as pool:
        groups = list(pool.map(worker, range(concurrency)))
    return [sample for group in groups for sample in group], time.perf_counter() - start


def percentile(values, percent):
    """Nearest-rank percentile; empty populations are unknown, never zero."""
    if not values:
        return None
    ordered = sorted(values)
    return ordered[max(0, math.ceil(len(ordered) * percent / 100) - 1)]


def summarize(samples, elapsed):
    latencies = [s.latency_ms for s in samples]
    successful = [s for s in samples if not s.error]
    return {
        "requests": len(samples), "failures": len(samples) - len(successful),
        "error_rate": (len(samples) - len(successful)) / len(samples) if samples else 1.0,
        "elapsed_seconds": elapsed,
        "requests_per_second": len(samples) / elapsed if elapsed else 0,
        "successful_requests_per_second": len(successful) / elapsed if elapsed else 0,
        "mib_per_second": sum(s.bytes for s in successful) / (1024 * 1024 * elapsed) if elapsed else 0,
        "latency_ms": {"mean": statistics.fmean(latencies) if latencies else None,
                       "p50": percentile(latencies, 50), "p95": percentile(latencies, 95),
                       "p99": percentile(latencies, 99), "max": max(latencies, default=None)},
        "ttfb_p95_ms": percentile([s.ttfb_ms for s in samples if s.ttfb_ms is not None], 95),
        "statuses": dict(Counter(str(s.status) for s in samples)),
        "errors": dict(Counter(s.error for s in samples if s.error)),
    }


def evaluate(report, args, baseline=None):
    failures = []
    for stage in report["stages"]:
        label = f"concurrency={stage['concurrency']}"
        for name, metrics in [("all", stage["summary"]), *stage["endpoints"].items()]:
            if not metrics["requests"]:
                failures.append(f"{label}/{name}: no samples")
            elif metrics["latency_ms"]["p95"] > args.max_p95_ms:
                failures.append(f"{label}/{name}: p95 exceeds {args.max_p95_ms:g} ms")
            if metrics["error_rate"] > args.max_error_rate:
                failures.append(f"{label}/{name}: error rate exceeds {args.max_error_rate:g}")
        if stage["summary"]["successful_requests_per_second"] < args.min_rps:
            failures.append(f"{label}: successful throughput below {args.min_rps:g} req/s")
    if baseline is not None:
        if (baseline.get("schema_version") != 1 or baseline.get("workload") != report["workload"]
                or baseline.get("passed") is not True):
            raise ValueError("Baseline must be a passing schema-v1 report with an identical workload")
        old_stages = baseline.get("stages", [])
        if [s["concurrency"] for s in old_stages] != [s["concurrency"] for s in report["stages"]]:
            raise ValueError("Baseline concurrency stages do not match")
        for stage, old in zip(report["stages"], old_stages):
            for name, metrics in stage["endpoints"].items():
                previous = old["endpoints"][name]["latency_ms"]["p95"]
                if not isinstance(previous, (int, float)) or not math.isfinite(previous) or previous <= 0:
                    raise ValueError("Baseline contains an invalid p95")
                change = (metrics["latency_ms"]["p95"] / previous - 1) * 100
                metrics["p95_change_percent"] = change
                if change > args.max_regression_pct:
                    failures.append(f"concurrency={stage['concurrency']}/{name}: p95 regression {change:.1f}%")
    return failures


def write_reports(report, samples, output):
    output.mkdir(parents=True, exist_ok=False)
    (output / "summary.json").write_text(json.dumps(report, indent=2, allow_nan=False) + "\n", encoding="utf-8")
    with (output / "samples.csv").open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=["concurrency", *Sample.__dataclass_fields__])
        writer.writeheader()
        writer.writerows(samples)
    rows = []
    for stage in report["stages"]:
        for name, metrics in [("all", stage["summary"]), *stage["endpoints"].items()]:
            latency = metrics["latency_ms"]
            cells = [stage["concurrency"], name, metrics["requests"], metrics["failures"],
                     f"{metrics['successful_requests_per_second']:.2f}",
                     *(f"{latency[key]:.2f}" if latency[key] is not None else "N/A"
                       for key in ("p50", "p95", "p99")),
                     f"{metrics['p95_change_percent']:+.1f}%" if "p95_change_percent" in metrics else "N/A"]
            rows.append("<tr>" + "".join(f"<td>{html.escape(str(c))}</td>" for c in cells) + "</tr>")
    verdict = "PASS" if report["passed"] else "FAIL"
    issues = "".join(f"<li>{html.escape(item)}</li>" for item in report["gate_failures"])
    page = f"""<!doctype html>
<html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width">
<title>Music API benchmark — {verdict}</title>
<style>body{{font:16px system-ui;margin:40px;color:#182235;background:#f6f8fc}}
table{{border-collapse:collapse;background:white;width:100%}}td,th{{padding:12px;text-align:left;border-bottom:1px solid #dce2ec}}
.scroll{{overflow:auto}}code{{overflow-wrap:anywhere}}h1{{color:#244f90}}</style>
<h1>Music API benchmark: {verdict}</h1><p>{html.escape(report['created_at'])}</p>
<p>Scenario: <strong>{html.escape(report['workload']['scenario'])}</strong></p>
<ul>{issues}</ul><div class="scroll"><table><thead><tr>
<th>Concurrency</th><th>Endpoint</th><th>Requests</th><th>Failures</th><th>Success req/s</th>
<th>p50 ms</th><th>p95 ms</th><th>p99 ms</th><th>p95 vs baseline</th>
</tr></thead><tbody>{''.join(rows)}</tbody></table></div>
<p>Warm-up and preflight excluded. Latencies include failed requests. Endpoint throughput uses the full stage duration.</p>
<p>See summary.json for TTFB, bandwidth, status counts, workload and gate settings; samples.csv contains individual measurements.</p>
</html>"""
    (output / "report.html").write_text(page, encoding="utf-8")


def parse_args(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--scenario", choices=("catalog", "personalized", "streaming"), default="catalog")
    parser.add_argument("--concurrency", default="1,4,8", help="Comma-separated worker counts (1..256)")
    parser.add_argument("--requests", type=int, default=300, help="Measured requests per stage")
    parser.add_argument("--warmup", type=int, default=30, help="Discarded requests before each stage")
    parser.add_argument("--query", default="love")
    parser.add_argument("--track-id", help="Optional streaming track UUID; otherwise discovered from /home")
    parser.add_argument("--token-env", default="BENCHMARK_TOKEN", help="Environment variable holding an existing access token")
    parser.add_argument("--timeout", type=float, default=10, help="Socket-operation timeout in seconds")
    parser.add_argument("--range-bytes", type=int, default=65536)
    parser.add_argument("--max-p95-ms", type=float, default=1000)
    parser.add_argument("--max-error-rate", type=float, default=0.01, help="Fraction between 0 and 1")
    parser.add_argument("--min-rps", type=float, default=0, help="Minimum successful requests/sec per stage")
    parser.add_argument("--baseline", type=Path, help="Previous summary.json with the same workload")
    parser.add_argument("--max-regression-pct", type=float, default=20)
    parser.add_argument("--output", type=Path, help="New report directory (must not already exist)")
    args = parser.parse_args(argv)
    try:
        args.concurrency = [int(value) for value in args.concurrency.split(",")]
        if (not args.concurrency or len(set(args.concurrency)) != len(args.concurrency)
                or any(c < 1 or c > 256 for c in args.concurrency)):
            raise ValueError("Concurrency must contain unique integers between 1 and 256")
        if args.requests < max(args.concurrency) or args.warmup < 0:
            raise ValueError("Requests must cover every worker; warmup must be nonnegative")
        for key in ("timeout", "max_p95_ms", "max_error_rate", "min_rps", "max_regression_pct"):
            value = getattr(args, key)
            if not math.isfinite(value) or value < 0:
                raise ValueError(f"{key} must be finite and nonnegative")
        if args.timeout == 0 or args.max_p95_ms == 0 or args.max_error_rate > 1:
            raise ValueError("Timeout and p95 must be positive; error rate must be <= 1")
        if not 1 <= args.range_bytes <= 1024 * 1024:
            raise ValueError("Range bytes must be between 1 and 1048576")
        url = urlsplit(args.base_url)
        if (url.scheme not in ("http", "https") or not url.hostname or url.username or url.password
                or url.query or url.fragment or any(ord(c) < 33 for c in args.base_url)):
            raise ValueError("Base URL must be HTTP(S), without credentials, query or fragment")
        _ = url.port
        if args.track_id:
            args.track_id = str(UUID(args.track_id))
        if args.output and args.output.exists():
            raise ValueError("Output directory already exists; choose a new path")
    except ValueError as exc:
        parser.error(str(exc))
    return args


def main(argv=None):
    args = parse_args(argv)
    try:
        token = os.environ.get(args.token_env, "").strip() if args.scenario != "catalog" else ""
        if args.scenario != "catalog" and not token:
            raise ValueError(f"{args.scenario} requires an access token in {args.token_env}")
        baseline = json.loads(args.baseline.read_text(encoding="utf-8")) if args.baseline else None
        client = Client(args.base_url, token, args.timeout, args.range_bytes)
        try:
            endpoints = workload(client, args.scenario, args.query, args.track_id)
        finally:
            client.close()
        if args.requests < len(endpoints):
            raise ValueError("Requests per stage must cover every endpoint")
        report = {
            "schema_version": 1, "created_at": datetime.now(timezone.utc).isoformat(),
            "base_url": args.base_url, "python": platform.python_version(), "platform": platform.platform(),
            "workload": {"scenario": args.scenario, "endpoints": [asdict(e) for e in endpoints],
                         "requests_per_stage": args.requests, "warmup_per_stage": args.warmup,
                         "range_bytes": args.range_bytes, "timeout": args.timeout,
                         "concurrency": args.concurrency, "connection_mode": "http1-persistent-per-worker"},
            "gates": {key: getattr(args, key) for key in
                      ("max_p95_ms", "max_error_rate", "min_rps", "max_regression_pct")},
            "baseline_used": baseline is not None, "stages": [],
        }
        # Normalize tuples to JSON arrays before baseline compatibility checks.
        report = json.loads(json.dumps(report))
        if baseline is not None and (baseline.get("schema_version") != 1
                                    or baseline.get("workload") != report["workload"]
                                    or baseline.get("passed") is not True):
            raise ValueError("Baseline must be a passing schema-v1 report with an identical workload")
        raw = []
        for concurrency in args.concurrency:
            print(f"Stage {concurrency} workers: warmup={args.warmup}, measured={args.requests}", flush=True)
            batch_args = (args.base_url, token, endpoints, concurrency)
            warmup, _ = run_batch(*batch_args, args.warmup, args.timeout, args.range_bytes)
            if any(s.error for s in warmup):
                print("Warning: warm-up contained failed requests (excluded from metrics).", file=sys.stderr)
            samples, elapsed = run_batch(*batch_args, args.requests, args.timeout, args.range_bytes)
            summary = summarize(samples, elapsed)
            report["stages"].append({
                "concurrency": concurrency, "summary": summary,
                "endpoints": {e.name: summarize([s for s in samples if s.endpoint == e.name], elapsed)
                              for e in endpoints},
            })
            raw.extend({"concurrency": concurrency, **asdict(s)} for s in samples)
            print(f"  p95={summary['latency_ms']['p95']:.2f} ms; "
                  f"success={summary['successful_requests_per_second']:.2f} req/s; failures={summary['failures']}")
        report["gate_failures"] = evaluate(report, args, baseline)
        report["passed"] = not report["gate_failures"]
        output = args.output or Path(__file__).resolve().parent / "results" / (
            datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid4().hex[:8])
        write_reports(report, raw, output)
        print(f"{'PASS' if report['passed'] else 'FAIL'} — report: {output.resolve() / 'report.html'}")
        for failure in report["gate_failures"]:
            print("  " + failure)
        return 0 if report["passed"] else 1
    except (OSError, ValueError, KeyError, TypeError, AttributeError) as exc:
        print(f"Benchmark setup/report error: {exc}", file=sys.stderr)
        return 2
    except KeyboardInterrupt:
        print("Benchmark interrupted; no complete report was generated.", file=sys.stderr)
        return 130


if __name__ == "__main__":
    sys.exit(main())
