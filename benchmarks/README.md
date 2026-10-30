# Advanced API benchmarks

An independent, read-only benchmark suite for the main Spring Boot API in `backend/`.
It needs **Python 3.10+**, uses only the standard library, and requires no application,
Gradle, Docker Compose or database changes. Run commands below from the repository root.
On Windows, use `py -3` if `python` is not on PATH.

## Quick start

Start the existing application stack and seed its catalog using the root README first.

```powershell
python benchmarks/run.py --concurrency 1,4,8 --requests 300 --warmup 30
```

Each invocation creates a unique folder under `benchmarks/results/` with:

- `report.html`: standalone, offline summary with per-stage and per-endpoint results.
- `summary.json`: latency, TTFB, throughput, bandwidth, errors, configuration and gate verdicts.
- `samples.csv`: every measured request, including HTTP status and failure category.

No response bodies, access tokens or authorization headers are written to reports.
Results are ignored by the new benchmark-local `.gitignore`.

## Scenarios

| Scenario | Requests |
|---|---|
| `catalog` (default) | Anonymous home, search, track picker, first album and artist details when present |
| `personalized` | Authenticated catalog plus recommendations, liked tracks and playlists |
| `streaming` | Personalized workload plus playback metadata and a validated audio byte range |

Each included endpoint receives an equal share of requests, rounded by deterministic
round-robin assignment. IDs are discovered from `/home`; search uses `--query love`
by default. Empty album/artist collections omit those detail endpoints, and the exact
endpoint list is recorded. Streaming discovers the first playable home track or accepts
`--track-id <UUID>`. It validates HTTP 206, `Content-Range`, audio content type and body
length, including tracks shorter than the requested range. JSON routes validate HTTP
200, content type and their top-level response shape.

Protected scenarios require an **existing access token** from a test account, supplied
through an environment variable. The suite only issues GET requests: it does not log in,
register users, refresh tokens, create playlists or submit listening events.

```powershell
$env:BENCHMARK_TOKEN = '<existing-access-token>'
python benchmarks/run.py --scenario personalized --query jazz
python benchmarks/run.py --scenario streaming --range-bytes 262144 --concurrency 1,4,8
Remove-Item Env:BENCHMARK_TOKEN
```

The catalog scenario always remains anonymous, even when the token variable is set.
Access tokens expire after 15 minutes in this application; use a fresh token before a
long run. Expiration during a measured stage counts as failures. There are no retries
or redirects that could hide failures or forward a bearer token to another host.

## Quality gates and regression detection

Defaults are illustrative development budgets, not established project SLAs:

- p95 latency at most **1000 ms**, checked for each stage and each endpoint.
- Error rate at most **0.01** (1%), checked for each stage and each endpoint.
- Optional minimum successful throughput per stage with `--min-rps`.
- With a baseline, at most **20%** p95 regression for each endpoint at each concurrency.

```powershell
# Output paths must be new directories. Keep the workload settings identical.
python benchmarks/run.py --output benchmarks/results/baseline --max-p95-ms 800
python benchmarks/run.py --baseline benchmarks/results/baseline/summary.json --output benchmarks/results/candidate --max-p95-ms 800 --max-regression-pct 15
```

Only a passing report of the same schema with identical workload settings and discovered
endpoint paths is accepted as a baseline. Different queries, IDs, request counts, warm-up,
concurrency, range sizes or timeouts require a new baseline. Keep the server hardware,
dataset, account, network and background activity consistent too; the runner cannot
verify those conditions. Base URLs may differ to allow comparison of equivalent deployments.

Exit codes: **0** all gates passed; **1** measured gates failed (reports are still written);
**2** invalid configuration, preflight, baseline or report error; **130** interrupted.
Preflight validates every selected route before load generation. A preflight failure
produces no performance report because the intended workload cannot be exercised.

## Measurement model and limitations

- Concurrency values are sequential load steps, not a continuous arrival-rate ramp.
  Each worker sends one request at a time over its own persistent HTTP/1.1 connection.
- `--requests` is the total measured count **per stage**, not per worker or endpoint.
  `--warmup` runs before every stage and is excluded, as is preflight. Warm-up warms
  server caches; measured workers start fresh connections, so initial handshakes count.
- Latency measures connection setup through complete body receipt; JSON validation
  happens afterwards. Failed requests remain in latency percentiles. TTFB measures
  time through the first response body byte (or EOF for an empty body).
- Percentiles use the nearest-rank method. Use hundreds of requests per endpoint for
  meaningful tail estimates; tiny smoke runs only check functionality.
- Throughput uses wall time including worker startup and final request completion.
  Endpoint throughput divides by the entire stage duration. Bandwidth includes only
  bodies from successful responses, excluding headers.
- This is a closed-loop client: slow responses reduce the offered load. It does not
  correct coordinated omission or claim constant requests/sec. Client CPU, network,
  Python threads and connection overhead can limit measurements.
- `--timeout` is a socket-operation timeout, not a whole-run deadline. In-flight calls
  can delay shutdown. JSON bodies are capped at 4 MiB; requested audio ranges at 1 MiB.
- Audio requests repeatedly fetch the start of one track and can benefit from storage
  caches. This is not a full-song playback, seek-distribution or Android UI benchmark.
- Raw measurements stay in memory until the report is saved; choose request counts
  appropriate for available memory. The suite does not measure server CPU or memory.

Use `python benchmarks/run.py --help` for all options. Run against a development or
dedicated performance environment and increase concurrency gradually.

## Offline tests

```powershell
python -m unittest discover -s benchmarks -p 'test_*.py' -v
```

Tests start a loopback HTTP fixture and cover contracts, invalid ranges, timeouts,
truncated responses, exact request counts, warm-up exclusion, token handling, reports,
baseline compatibility and failing quality gates. They do not require Docker or music files.
