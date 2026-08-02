# Phase 1 benchmark baseline — 2026-08-02

Captured from the **original, unmodified** `scout/` codebase (never edited, per the
"Physical code location" decision in `.ai/README.md`) using the existing
`scout/measures/jvm-benchmarks` JMH suite, before any Phase 2 work starts on
`scout-kmp/core`. This is the reference point for:

- Phase 4's benchmark comparison (`phase4-benchmark-comparison`) — measuring the actual
  regression, if any, introduced by the `KClass`-based `class-keys` port to `commonMain`.
- The `compiled-keys` fate decision on JVM/Android (`phase4-compiled-keys-decision`).

## How this was captured

Run manually by the user (not via this AI session, to survive session
interruptions — a first automated attempt was killed mid-run when the background
process wasn't fully detached from the agent session):

```bash
cd scout/
export JAVA_HOME=<JDK 11 path>   # module's Kotlin/Java toolchain requires JDK 11
nohup ./gradlew --no-daemon :measures:jvm-benchmarks:run \
  --args="get-call collect-call associate-call parent-access init-scope builder-mode get-constant get/warm/all get/cold/all graph-init/all" \
  > /tmp/scout_benchmark_baseline.log 2>&1 &
disown
```

Full run took **48m 14s** (`BUILD SUCCESSFUL`), all 68 benchmark result rows produced,
zero exceptions/failures in the build log.

## Files in this directory

- `control.csv` — raw JMH result values (ns/op, average time mode), one row per
  benchmark method, copied verbatim from
  `scout/measures/jvm-benchmarks/results/control.csv` (that directory is
  `.gitignore`d in `scout/`, hence archiving a copy here).
- `compare.txt` — the suite's own formatted comparison table (all rows show `new` since
  this is the first-ever run — there was no prior `control.csv` to diff against).

## Environment metadata

| | |
|---|---|
| Date | 2026-08-02 |
| Host OS | macOS 26.5.2, arm64 (Apple Silicon) |
| JDK used for the benchmark JVM | Amazon Corretto 11.0.19 (module's pinned toolchain: `JavaLanguageVersion.of(11)`) |
| Gradle | 8.2.1 (`--no-daemon`) |
| Kotlin | 1.8.22 (project's current, pre-migration pinned version) |
| Scout version under test | `scout/scout/core`, unmodified, `class-keys` variant (default; `withCompiledKeys=false`) |
| JMH | 1.36, Average Time mode, 2 warmup + 5 measurement iterations, 3 forks (per module's `accurate()` options in `Launcher.kt`) |

## Benchmark scenarios included (all 10, full suite)

Assessment: `get-call`, `collect-call`, `associate-call`, `parent-access`,
`init-scope`, `builder-mode`.

Comparison (Scout vs Dagger/Koin/Kodein/Katana): `get-constant`, `get/warm/all`
(5/25/125 nodes), `get/cold/all` (5/25/125 nodes), `graph-init/all` (5/25/125 nodes).

## Headline numbers (ns/op, lower is better) — for quick reference only, see `control.csv` for full precision

| Benchmark | Scout | Dagger | Koin | Kodein | Katana |
|---|---|---|---|---|---|
| `GetConstant` | 2.726 | 1.197 | 78.102 | 44.085 | 8.128 |
| `WarmGet5` | 17.689 | 6.318 | 553.675 | 300.126 | 57.522 |
| `WarmGet25` | 103.643 | 31.665 | 3028.923 | 2107.343 | 286.985 |
| `WarmGet125` | 535.211 | 145.809 | 20643.694 | 18490.866 | 1867.645 |
| `ColdGet5/25/125` | ~0.000-0.001 | ~0.000 | 0.001-0.021 | 0.000-0.019 | 0.000-0.002 |
| `GraphInit5/25/125` | ~0.000 (all frameworks) | — | — | — | — |

Scout is consistently competitive (faster than Koin/Kodein, slower than Dagger's
compile-time codegen, roughly in Katana's range) — matches the expectation set in
`design_philosophy.md`. This baseline is what Phase 4 will re-run against after the
`KClass`-based `commonMain` port to quantify any regression.
