# Phase 3 — Platform Targets (androidApp / iosApp)

## 🎯 Goal

Stand up concrete platform-consumer modules — **`androidApp` and `iosApp`** (confirmed
scope for this iteration; Compose Multiplatform/`composeApp` and JS/Wasm are explicitly
out of scope until a later phase) — that depend on the new multiplatform
`scout/core` shared module produced in Phase 2, proving the library works end-to-end
outside the JVM-only sample. This phase evolves `scout/samples/coffee-maker` into a
real multiplatform showcase rather than inventing a brand-new app from scratch.

## 📍 Starting state (read before starting)

- `.ai/migration_plan/phase_2_refactoring_shared.md` — **must be fully checked off**;
  `core` must build and publish working `jvm` and iOS targets before this phase starts.
- `.ai/README.md` — "Confirmed migration decisions" table: platform scope is
  **JVM/Android + iOS only** for this iteration; do not add `composeApp`/JS/Wasm work
  without a new, explicit user decision.
- `.ai/migration_plan/phase_1_analysis.md` — iOS graph-validation strategy is now
  **resolved**: a new KSP-based component collector (test-only, `kspJvmTest`/
  `kspIosX64Test`/etc.) replaces ClassGraph/reflection for non-JVM validation; the
  existing JVM `validator`/`classgraph-collector` stay untouched. See
  `phase_4_tooling_jvm.md` for the KSP module's implementation checklist — if it is not
  ready yet when this phase needs to validate the iOS consumer's graph, coordinate with
  Phase 4 rather than falling back to an undocumented "skip validation" shortcut.
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

- [ ] `androidApp` module created with Android Gradle Plugin applied,
      depending on the shared module's `androidTarget()`/`jvm()` output, and able to
      build a debug APK that instantiates at least one `Scope`/`Component` from the
      shared library.
- [ ] `iosApp` module/target created with the shared module exposing an
      `iosArm64`/`iosSimulatorArm64`/`iosX64` (or current recommended Kotlin/Native iOS
      target set) framework, and a minimal Swift or Kotlin/Native entry point
      successfully calling into the shared `Scope`.
- [ ] `scout/samples/coffee-maker` is either migrated into one of the new platform
      modules or explicitly kept as the JVM-only reference and cross-linked from the new
      modules' README — decide and document which, do not leave both diverging silently.
- [ ] The `iosApp` module has at least one KSP-generated validation test running in
      `iosTest` (per Phase 4's KSP collector, once available) exercising the same
      dependency graph the JVM `validator` already checks — this is the concrete proof
      that the iOS validation strategy decided in Phase 1 actually works end-to-end, not
      just on paper.
- [ ] All new modules are registered in the root Gradle settings file and build
      successfully via a single `./gradlew build` (or platform-appropriate build
      command for iOS, e.g. `xcodebuild` invoked through the KMP-generated scheme).
- [ ] No modification was made to `scout/core`'s public API in this phase — if a gap is
      found, it is reported back to Phase 2 rather than patched ad-hoc here.

## ⚠️ Anti-patterns (forbidden on this phase)

- Do **not** create a `composeApp` module or any JS/Wasm target work in this phase —
  out of scope for this iteration per the confirmed decision in `.ai/README.md`; a new
  explicit user decision is required before expanding scope.
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
