"""Offline contract and regression tests; never contact the application stack."""

from collections import Counter
import contextlib
import copy
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import io
import json
import os
from pathlib import Path
import tempfile
import threading
import time
import unittest
from unittest.mock import patch

import run as benchmark


TRACK = "11111111-1111-4111-8111-111111111111"
ALBUM = "22222222-2222-4222-8222-222222222222"
ARTIST = "33333333-3333-4333-8333-333333333333"


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    calls = []

    def log_message(self, *_):
        pass

    def handle(self):
        try:
            super().handle()
        except (ConnectionResetError, ConnectionAbortedError):
            # Timeout and oversized-body tests deliberately abandon the connection.
            pass

    def do_GET(self):
        self.calls.append((self.path, self.headers.get("Authorization"), self.headers.get("Range")))
        path = self.path.split("?", 1)[0]
        status, headers = 200, {"Content-Type": "application/json"}
        payload = []
        if path == "/home":
            payload = {"tracks": [{"id": TRACK, "playable": True}],
                       "albums": [{"id": ALBUM}], "artists": [{"id": ARTIST}]}
        elif path == "/search":
            payload = {"tracks": [], "albums": [], "artists": []}
        elif path.startswith("/albums/"):
            payload = {"album": {}, "tracks": []}
        elif path.startswith("/artists/"):
            payload = {"artist": {}, "tracks": []}
        elif path.endswith("/playback"):
            payload = {"streamUrl": f"/tracks/{TRACK}/stream", "durationSeconds": 60}
        elif path == "/unauthorized":
            status, payload = 401, {"message": "private response"}
        elif path == "/redirect":
            status, headers["Location"] = 302, "/home"
        elif path == "/wrong-shape":
            payload = {"error": "not a catalog"}
        elif path == "/slow":
            time.sleep(0.08)
        body = json.dumps(payload).encode()
        if path.endswith("/stream") or path in ("/bad-range", "/short-range", "/oversized"):
            requested = int(self.headers["Range"].split("-")[1]) + 1
            size = min(requested, 100)
            body = b"a" * size
            status = 206
            headers = {"Content-Type": "audio/mpeg", "Content-Range": f"bytes 0-{size - 1}/100"}
            if path == "/bad-range":
                headers["Content-Range"] = "bytes 1-100/100"
            if path == "/short-range":
                body = body[:-1]
            if path == "/oversized":
                body = b"a" * (requested + 1)
        if path == "/invalid-json":
            body = b"not JSON"
        if path == "/truncated":
            headers["Content-Length"] = str(len(body) + 5)
            headers["Connection"] = "close"
            self.close_connection = True
        self.send_response(status)
        for key, value in headers.items():
            self.send_header(key, value)
        if "Content-Length" not in headers:
            self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        try:
            self.wfile.write(body)
        except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError):
            pass


class BenchmarkTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()
        cls.url = f"http://127.0.0.1:{cls.server.server_port}"

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join()

    def request(self, path, kind="json", keys=(), timeout=1):
        client = benchmark.Client(self.url, timeout=timeout, range_bytes=128)
        try:
            return client.request(benchmark.Endpoint("test", path, kind, keys))[0]
        finally:
            client.close()

    def test_percentiles_and_failed_samples(self):
        samples = [benchmark.Sample("a", 200, 10, 3, 100, ""),
                   benchmark.Sample("a", 500, 90, 4, 20, "unexpected_status")]
        result = benchmark.summarize(samples, 2)
        self.assertEqual(result["latency_ms"], {"mean": 50, "p50": 10, "p95": 90, "p99": 90, "max": 90})
        self.assertEqual(result["error_rate"], 0.5)
        self.assertEqual(result["successful_requests_per_second"], 0.5)
        self.assertEqual(result["statuses"], {"200": 1, "500": 1})
        self.assertIsNone(benchmark.percentile([], 95))

    def test_contract_failures_and_redirects(self):
        for path, expected in [("/unauthorized", "unexpected_status"),
                               ("/redirect", "unexpected_status"),
                               ("/invalid-json", "JSONDecodeError"),
                               ("/wrong-shape", "invalid_json_shape"),
                               ("/truncated", "incomplete_body")]:
            with self.subTest(path=path):
                sample = (self.request(path, kind="list") if path == "/truncated"
                          else self.request(path, keys=("tracks",)))
                self.assertEqual(sample.error, expected)

    def test_audio_range_checks(self):
        sample = self.request(f"/tracks/{TRACK}/stream", "range")
        self.assertEqual(sample.error, "")
        self.assertEqual(sample.bytes, 100)  # Correctly accepts an object shorter than the requested range.
        for path, expected in [("/bad-range", "invalid_content_range"),
                               ("/short-range", "invalid_range_length"),
                               ("/oversized", "body_too_large")]:
            with self.subTest(path=path):
                self.assertEqual(self.request(path, "range").error, expected)

    def test_timeout_is_counted(self):
        sample = self.request("/slow", timeout=0.01)
        self.assertIn(sample.error, ("TimeoutError", "timeout"))
        self.assertGreater(sample.latency_ms, 0)

    def test_balanced_exact_request_counts(self):
        endpoints = [benchmark.Endpoint("tracks", "/tracks", "list"),
                     benchmark.Endpoint("search", "/search", keys=("tracks",))]
        samples, elapsed = benchmark.run_batch(self.url, "", endpoints, 3, 11, 1, 128)
        self.assertEqual(Counter(s.endpoint for s in samples), {"tracks": 6, "search": 5})
        self.assertTrue(all(not sample.error for sample in samples))
        self.assertGreater(elapsed, 0)

    def test_cli_reports_warmup_auth_and_baseline(self):
        with tempfile.TemporaryDirectory() as temp:
            first, second = Path(temp) / "first", Path(temp) / "second"
            args = ["--base-url", self.url, "--scenario", "streaming", "--concurrency", "1,2",
                    "--requests", "20", "--warmup", "10", "--max-p95-ms", "10000"]
            start = len(Handler.calls)
            secret = "test-secret-do-not-persist"
            with patch.dict(os.environ, {"BENCHMARK_TOKEN": secret}), contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(benchmark.main([*args, "--output", str(first)]), 0)
                self.assertEqual(benchmark.main([*args, "--baseline", str(first / "summary.json"),
                                                 "--max-regression-pct", "100000", "--output", str(second)]), 0)
            report = json.loads((first / "summary.json").read_text())
            self.assertTrue(report["passed"])
            self.assertEqual([s["summary"]["requests"] for s in report["stages"]], [20, 20])
            self.assertEqual(len((first / "samples.csv").read_text().splitlines()), 41)
            # 1 discovery + 10 preflight + 2 * (10 warmup + 20 measured), twice.
            self.assertEqual(len(Handler.calls) - start, 142)
            self.assertTrue(all(auth == "Bearer " + secret for _, auth, _ in Handler.calls[start:]))
            for folder in (first, second):
                for file in folder.iterdir():
                    self.assertNotIn(secret, file.read_text(encoding="utf-8"))
            self.assertIn("p95 vs baseline", (second / "report.html").read_text(encoding="utf-8"))

    def test_catalog_ignores_token_and_failed_gate_returns_one(self):
        start = len(Handler.calls)
        with tempfile.TemporaryDirectory() as temp, patch.dict(os.environ, {"BENCHMARK_TOKEN": "secret"}), \
                contextlib.redirect_stdout(io.StringIO()):
            code = benchmark.main(["--base-url", self.url, "--concurrency", "1", "--requests", "5",
                                   "--warmup", "0", "--min-rps", "1000000000", "--output", str(Path(temp) / "run")])
            self.assertEqual(code, 1)
        self.assertTrue(all(auth is None for _, auth, _ in Handler.calls[start:]))

    def test_gates_and_baseline_compatibility(self):
        args = benchmark.parse_args([])
        metrics = benchmark.summarize([benchmark.Sample("a", 200, 100, 10, 10, "")], 1)
        report = {"schema_version": 1, "workload": {"scenario": "catalog"}, "passed": True,
                  "stages": [{"concurrency": 1, "summary": metrics, "endpoints": {"a": metrics}}]}
        baseline = copy.deepcopy(report)
        baseline["stages"][0]["endpoints"]["a"]["latency_ms"]["p95"] = 50
        self.assertIn("regression", benchmark.evaluate(report, args, baseline)[0])
        baseline["workload"]["scenario"] = "streaming"
        with self.assertRaises(ValueError):
            benchmark.evaluate(report, args, baseline)
        bad = copy.deepcopy(report)
        bad["stages"][0]["endpoints"]["a"]["error_rate"] = 0.5
        self.assertTrue(any("error rate" in item for item in benchmark.evaluate(bad, args)))

    def test_invalid_arguments(self):
        for args in (["--concurrency", "0"], ["--concurrency", "2,2"], ["--timeout", "nan"],
                     ["--requests", "0"], ["--max-error-rate", "2"], ["--range-bytes", "0"],
                     ["--base-url", "http://user:secret@localhost"], ["--base-url", "ftp://localhost"],
                     ["--base-url", "http://localhost?token=secret"], ["--track-id", "bad"]):
            with self.subTest(args=args), contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit) as result:
                benchmark.parse_args(args)
            self.assertEqual(result.exception.code, 2)

    def test_missing_token_returns_setup_error(self):
        with patch.dict(os.environ, {}, clear=True), contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(benchmark.main(["--scenario", "streaming"]), 2)

    def test_report_directory_is_never_overwritten(self):
        with tempfile.TemporaryDirectory() as temp, self.assertRaises(FileExistsError):
            benchmark.write_reports({}, [], Path(temp))


if __name__ == "__main__":
    unittest.main()
