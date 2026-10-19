# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repo is

A Kotlin Multiplatform fork of Yandex **Scout**, a runtime (non-codegen) manual DI library for Kotlin/Android. The repo is in the middle of a phased KMP migration, and the migration plan is written down.

**Before any task, read `.ai/README.md`** (decisions, current phase, rules, glossary) and the current phase file in `.ai/migration_plan/phase_*.md`. Read `.ai/design_philosophy.md` before you touch core APIs, the key encoding, or the `get`/`opt`/`getLazy`/`getProvider`/`collect`/`associate` family. It records experiments that were tried and reverted (qualifiers, "smart get" via `typeOf<T>()`, string keys). Do not bring them back. The decisions table in `.ai/README.md` is confirmed by the user, so don't re-argue it without a new instruction from them.

## Two independent Gradle projects

```
scout/       ORIGINAL JVM-only library. FROZEN: never edit anything here.
             It is the reference baseline for benchmarks and rollback.
scout-kmp/   NEW standalone KMP project (own settings.gradle.kts and wrapper).
             All migration work goes here.
.ai/         Agent knowledge base: plan, decisions, benchmark baseline, experiments.
docs/        User-facing docs for the original library (scope anatomy, validation, etc.).
```

- Code moves from `scout/` into `scout-kmp/` by **copying and adapting**: never `git mv`, never edit in place, and never add a Gradle dependency between the two projects.
- Kotlin package `scout.*` becomes `dev.gekko.scout.*`, and the Maven group becomes `dev.gekko.scout`. The rename happens as you copy. `scout/` keeps `com.yandex.scout` and `scout.*`.
- Targets for this iteration are JVM/Android and iOS (`iosX64`, `iosArm64`, `iosSimulatorArm64`). JS/Wasm is deferred.
- Modules are added to `scout-kmp/` one phase at a time. Right now only `:core` exists, and it holds a bootstrap placeholder. Phase 2 (porting `scout/scout/core` into `commonMain`) is next.
- Keep the original `Copyright 2023 Yandex LLC` headers. Only append `Copyright 2026 gekkothedolphin@gmail.com`.
- Everything committed (code, comments, commit messages, `.ai/`) must be in English.
- After meaningful progress, update the status in `.ai/README.md` and the checklist in the matching phase file. Tick an item `[x]` only after the build or tests actually pass. Keep each commit or PR to one phase.

## Commands

The new KMP project, where you'll work:
```bash
cd scout-kmp
./gradlew build                          # all targets
./gradlew :core:jvmTest                  # JVM tests
./gradlew :core:iosSimulatorArm64Test    # iOS simulator tests (macOS)
./gradlew :core:allTests                 # every target
./gradlew :core:jvmTest --tests "dev.gekko.scout.SomeTest.someMethod"   # single test
```
`kotlin.native.ignoreDisabledTargets=true` is set on purpose: `iosX64` can't link on Apple Silicon, but the target stays.

The original project (read-only reference; CI still runs it):
```bash
cd scout
./gradlew assemble                         # class-keys variant (default)
./gradlew test -Dscout.compile.keys=true   # compiled-keys variant
./gradlew :scout:core:test --tests "scout.SomeTest"
./gradlew :measures:jvm-benchmarks:run --args="get-call"   # also: collect-call, associate-call, parent-access, init-scope, builder-mode
```
The benchmark baseline taken on the unmodified `scout/` is archived in `.ai/benchmarks/baseline/`. Phase 4 compares against it.

License headers: `./scripts/check-license-headers.sh` (runs in CI; `--correct` adds missing headers). It compares the first 15 lines of every `*.kt` with `scripts/license_header`.

## Architecture (original `scout/`, the porting source)

- `scout/scout/core` holds the pure DI logic. A `Scope` holds `Definition`s in a registry and the instances they create. You can read from a scope only through a `Component` (`get`, `opt`, `getLazy`, `getProvider`, `collect`, `associate`). Instances are created by an `InstanceFactory` strategy: `singleton`, `reusable` (cached behind a `SoftReference`), or `factory`. Multibindings are explicit: `element<T>` contributes to a `List` and `mapping<K,V>` to a `Map`. Overriding a definition requires `allowOverride`. There are no qualifiers; tell same-type instances apart with wrapper classes.
- **Key variants** are separate source sets of `core`, chosen by the `-Dscout.compile.keys` property in `scout/features.gradle`:
  - `src/class-keys`: keys are `Class<*>`. This is the portable variant, and it becomes `KClass<*>` in `commonMain`.
  - `src/compiled-keys`: keys are `Int`, and `tools/scout-gradle-plugin` rewrites them into the bytecode with bcel. It is JVM-only. Phase 2 builds a `DiKey`/`diKeyOf` `expect`/`actual` seam, so Phase 4 can choose an `Int` actual for JVM based on benchmark data. iOS always uses `KClass` keys.
- `inspector`, `validator`, `tools/classgraph-collector` and `tools/scout-gradle-plugin` are JVM-only (reflection, ClassGraph, bcel) and stay that way. Validation on iOS will come from a new KSP collector (built with KotlinPoet) wired only into per-target test configurations (`kspJvmTest`, `kspIosArm64Test`, and so on). There is no `kspCommonTest`.
- The JVM APIs in `core` that need `expect`/`actual` (`ThreadConfinementCheck`, `ReusableInstanceFactory`, `ParentLookupUtils`) are listed in `.ai/context_project.md`.
