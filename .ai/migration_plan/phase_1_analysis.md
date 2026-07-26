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

- [ ] Every `.kt` file under `scout/scout/core/src/main` is grepped for JVM-only
      imports (`java.*`, `javax.*`) and the results are tabulated (file → API → KMP
      replacement strategy) in `context_project.md`.
- [ ] Confirm whether `class-keys` or `compiled-keys` is the default build variant
      (check `withCompiledKeys` in `features.gradle`) and document the implication for
      KMP (only `class-keys` is portable).
- [ ] Confirm the Maven group (`com.yandex.scout`) vs Kotlin package namespace
      (`scout.*`) are documented as two independent axes — decide (with the user) in
      this phase whether package renaming to a new namespace (e.g. `dev.gekko.scout` or
      similar) is required for the fork, or whether the existing `scout.*` package is
      kept.
- [ ] Verify license compatibility: original license is Apache 2.0 (`LICENSE` file),
      confirm the fork's added copyright line format matches what is already present in
      root `README.md` (`Copyright 2026 gekkothedolphin@gmail.com`) and propagate the
      same pattern requirement to `scripts/license_header` if it generates headers for
      new files.
- [ ] List every JVM-only downstream module (`inspector`, `validator`,
      `classgraph-collector`, `scout-gradle-plugin`, `measures/*`) with an explicit
      decision recorded: "stays JVM-only" or "needs multiplatform equivalent" — no
      module should be left undecided.
- [ ] Identify the minimum Kotlin version required by the KMP Gradle plugin compatible
      with the current toolchain, and record whether a Kotlin version bump (from
      `1.8.22`) is required before Phase 2 can start.
- [ ] Get explicit user confirmation on open questions before Phase 2 starts: (a)
      package renaming decision, (b) Kotlin version bump decision, (c) which additional
      targets beyond `jvm` are in scope (native/iOS, JS/Wasm) for the first iteration.

## ⚠️ Anti-patterns (forbidden on this phase)

- Do **not** modify any source file under `scout/scout/**` or `scout/tools/**` in this
  phase — this is a read-only analysis phase.
- Do **not** assume the Maven group and Kotlin package are the same thing — verify both
  independently, they may need different decisions.
- Do **not** silently rename or remove `Copyright 2023 Yandex LLC` headers when
  reviewing files — only note where new lines should be appended in a later phase.
- Do **not** guess which JVM APIs have multiplatform equivalents — verify each one
  individually against the actual Kotlin/kotlinx-atomicfu documentation before
  recording a replacement strategy.
- Do **not** proceed to Phase 2 while any checklist item above is unchecked or any open
  question is unresolved with the user.
