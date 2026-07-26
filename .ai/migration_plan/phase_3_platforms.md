# Phase 3 — Platform Targets (androidApp / iosApp / composeApp)

## 🎯 Goal

Stand up concrete platform-consumer modules (`androidApp`, `iosApp`, and/or
`composeApp` for Compose Multiplatform, pending user confirmation from Phase 1) that
depend on the new multiplatform `scout/core` shared module produced in Phase 2,
proving the library works end-to-end outside the JVM-only sample. This phase evolves
`scout/samples/coffee-maker` into a real multiplatform showcase rather than inventing
a brand-new app from scratch.

## 📍 Starting state (read before starting)

- `.ai/migration_plan/phase_2_refactoring_shared.md` — **must be fully checked off**;
  `core` must build and publish a working `jvm` (and any additional approved) target
  before this phase starts.
- `.ai/context_project.md` — confirm the "Navigation / UI framework" section's decision
  on Compose Multiplatform (or other UI approach) — **do not assume**; if Phase 1 did
  not get explicit user confirmation on this, stop and ask before proceeding.
- `scout/samples/coffee-maker/src/main/**` and `src/test/**` — current JVM-only sample,
  the closest existing analog to a consumer app; reuse its dependency-graph structure as
  the reference example when creating platform apps.
- `scout/settings.gradle` — current `include` list; new platform modules must be added
  here (or in a new root `settings.gradle.kts`/`.gradle`, decide consistently with
  Phase 1's Kotlin DSL decision, if any).
- Root repository layout (`ls` at repo root) — confirm no `androidApp/`, `iosApp/`,
  `composeApp/` directories exist yet; do not assume any starter template files are
  present.

## 📋 Checklist (Definition of Done)

- [ ] Explicit user confirmation obtained (if not already recorded in Phase 1) on:
      which platform targets to stand up first (Android only, Android + iOS, or full
      Compose Multiplatform), before creating any new module directory.
- [ ] `androidApp` module created (if approved) with Android Gradle Plugin applied,
      depending on the shared module's `androidTarget()`/`jvm()` output, and able to
      build a debug APK that instantiates at least one `Scope`/`Component` from the
      shared library.
- [ ] `iosApp` module/target created (if approved) with the shared module exposing a
      `iosArm64`/`iosSimulatorArm64`/`iosX64` (or current recommended Kotlin/Native iOS
      target set) framework, and a minimal Swift or Kotlin/Native entry point
      successfully calling into the shared `Scope`.
- [ ] `composeApp` module created (if Compose Multiplatform was approved) sharing UI
      code across Android/iOS/Desktop as agreed, consuming the shared DI module.
- [ ] `scout/samples/coffee-maker` is either migrated into one of the new platform
      modules or explicitly kept as the JVM-only reference and cross-linked from the new
      modules' README — decide and document which, do not leave both diverging silently.
- [ ] All new modules are registered in the root Gradle settings file and build
      successfully via a single `./gradlew build` (or platform-appropriate build
      command for iOS, e.g. `xcodebuild` invoked through the KMP-generated scheme).
- [ ] No modification was made to `scout/core`'s public API in this phase — if a gap is
      found, it is reported back to Phase 2 rather than patched ad-hoc here.

## ⚠️ Anti-patterns (forbidden on this phase)

- Do **not** create `androidApp`/`iosApp`/`composeApp` directories or scaffolding
  before getting explicit user confirmation on which platforms/UI framework to target —
  this phase's starting state may otherwise conflict with the user's actual product
  intent.
- Do **not** copy-paste a generic KMP wizard template without checking it against the
  versions already pinned in `.ai/context_project.md` (Kotlin version, AGP version) —
  version mismatches are a common source of unbuildable scaffolding.
- Do **not** modify `scout/scout/core` internals in this phase to "make the sample
  easier" — any required API change must go back through Phase 2's checklist and be
  re-verified there.
- Do **not** delete `scout/samples/coffee-maker` until its replacement (if any) is
  fully building and passing tests.
- Do **not** invent new Maven/package coordinates for the platform apps that diverge
  from the namespace decision already recorded in Phase 1/`context_project.md`.
