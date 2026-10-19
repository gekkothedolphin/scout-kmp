# Phase 1 — Analysis

## 🎯 Goal

Produce a complete, verified inventory of the current codebase before touching any
production code: package namespaces (`scout.*` vs Maven `com.yandex.scout`), every
JVM-only dependency inside `scout/core`, and the licensing implications of forking
Yandex Scout under Apache 2.0. This phase produces **no code changes** — only findings
recorded in this file's checklist and, if needed, follow-up notes appended to
`context_project.md`.

## 📍 Starting state (read before starting)

- `scout/settings.gradle`, `scout/modules.gradle`, `scout/versions.gradle`,
  `scout/libraries.gradle`, `scout/features.gradle` — module graph and version pins.
- `scout/scout/core/build.gradle` — note the `class-keys` vs `compiled-keys` source set
  switch (`withCompiledKeys` flag).
- `scout/scout/core/src/main/kotlin/scout/**/*.kt` — full core source tree.
- `scout/scout/core/src/class-keys/kotlin/scout/**` and
  `scout/scout/core/src/compiled-keys/kotlin/scout/**` — the two key-encoding variants.
- `scout/scout/inspector/src/main/kotlin/**`, `scout/scout/validator/src/main/kotlin/**`
  — downstream JVM-only modules that consume `core`.
- Root `LICENSE`, `AUTHORS`, `CONTRIBUTING.md`, and root `README.md` License section —
  fork attribution and copyright rules already established.
- `scripts/license_header` — the existing license header tooling/template.
- `.ai/context_project.md` — already-collected findings; do not duplicate work, extend
  it instead.

## 📋 Checklist (Definition of Done)

- [x] Every `.kt` file under `scout/scout/core/src/main` is grepped for JVM-only
      imports (`java.*`, `javax.*`) and the results are tabulated (file → API → KMP
      replacement strategy) in `context_project.md`. **Status: done** — see the table in
      `context_project.md`.
- [x] Confirm whether `class-keys` or `compiled-keys` is the default build variant
      (check `withCompiledKeys` in `features.gradle`) and document the implication for
      KMP (only `class-keys` is portable). **Status: done** — default is `class-keys`
      (`withCompiledKeys` is `false` unless `-Dscout.compile.keys=true` is passed);
      `compiled-keys`/`scout-gradle-plugin` fate is **deferred to Phase 4** (data-driven
      decision, see `.ai/README.md` "Confirmed migration decisions").
- [x] Confirm the Maven group (`com.yandex.scout`) vs Kotlin package namespace
      (`scout.*`) are documented as two independent axes — decide (with the user) in
      this phase whether package renaming to a new namespace (e.g. `dev.gekko.scout` or
      similar) is required for the fork, or whether the existing `scout.*` package is
      kept. **Status: done** — rename `scout.*` → `dev.gekko.scout.*` confirmed, to be
      executed as part of the copy into `scout-kmp/core` in Phase 2. Maven group for the
      new `scout-kmp/` project: ~~`dev.gekko.scout`~~ (2026-08-02), changed on 2026-10-19
      to **`io.github.gekkothedolphin`** (Maven Central namespace verification); the
      Kotlin package stays `dev.gekko.scout`. The original `scout/build.gradle`'s
      `com.yandex.scout` group is never changed — `scout/` is frozen.
- [x] Verify license compatibility: original license is Apache 2.0 (`LICENSE` file),
      confirm the fork's added copyright line format matches what is already present in
      root `README.md` (`Copyright 2026 gekkothedolphin@gmail.com`) and propagate the
      same pattern requirement to `scripts/license_header` if it generates headers for
      new files.
- [x] List every JVM-only downstream module (`inspector`, `validator`,
      `classgraph-collector`, `scout-gradle-plugin`, `measures/*`) with an explicit
      decision recorded: "stays JVM-only" or "needs multiplatform equivalent" — no
      module should be left undecided. **Status: done** — `inspector` and
      `validator` are confirmed JVM-only (classpath-reflection based, see
      `design_philosophy.md` on the validator algorithm using ClassGraph + reflective
      `Component` instantiation, which is not portable to Native/JS) and **stay
      untouched** as the proven JVM/Android validation path. iOS/Native validation is
      solved by a **new, separate KSP-based collector** (see `.ai/README.md` "Confirmed
      migration decisions" and `phase_3_platforms.md`/`phase_4_tooling_jvm.md`), not by
      porting ClassGraph/reflection.
- [x] Identify the minimum Kotlin version required by the KMP Gradle plugin compatible
      with the current toolchain, and record whether a Kotlin version bump (from
      `1.8.22`) is required before Phase 2 can start. **Status: done** — bump to
      current stable Kotlin confirmed; pick the exact target version at the start of
      Phase 2 (verify latest stable at that time rather than hardcoding a version here
      that may already be outdated).
- [x] Capture a benchmark baseline using the existing `measures/jvm-benchmarks` suite
      against the current, unmodified JVM-only codebase, and archive results. **Status:
      done (2026-08-02)** — full suite run (10 scenarios, 68 benchmark methods, JDK 11,
      `BUILD SUCCESSFUL in 48m 14s`), archived at `.ai/benchmarks/baseline/`
      (`control.csv`, `compare.txt`, `README.md` with environment metadata).
- [x] Get explicit user confirmation on open questions before Phase 2 starts: ~~(a)
      package renaming decision~~ done, ~~(b) Kotlin version bump decision~~ done, ~~(c)
      which additional targets beyond `jvm` are in scope~~ done (iOS confirmed, JS/Wasm
      deferred), ~~(d) iOS graph-validation strategy~~ done (new KSP-based collector,
      test-only, existing JVM validator untouched), ~~(e) Maven group for `scout-kmp/`~~
      done (`dev.gekko.scout`). **All Phase 1 items resolved — Phase 2 may start.**

## ⚠️ Anti-patterns (forbidden on this phase)

- Do **not** modify any source file under `scout/scout/**` or `scout/tools/**` in this
  phase, or in **any subsequent phase of this migration** — `scout/` is the permanently
  frozen JVM-only reference baseline; all new/ported code lives in a separate
  `scout-kmp/` directory (see `.ai/README.md` "Physical code location" decision).
- Do **not** assume the Maven group and Kotlin package are the same thing — verify both
  independently, they may need different decisions.
- Do **not** silently rename or remove `Copyright 2023 Yandex LLC` headers when
  reviewing files — only note where new lines should be appended in a later phase.
- Do **not** guess which JVM APIs have multiplatform equivalents — verify each one
  individually against the actual Kotlin/kotlinx-atomicfu documentation before
  recording a replacement strategy.
- Do **not** proceed to Phase 2 while any checklist item above is unchecked or any open
  question is unresolved with the user.
