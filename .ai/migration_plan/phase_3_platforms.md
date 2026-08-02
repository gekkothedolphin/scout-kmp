# Phase 3 — Platform Targets (androidApp / iosApp)

## 🎯 Goal

Stand up concrete platform-consumer modules — **`androidApp` and `iosApp`** (confirmed
scope for this iteration; Compose Multiplatform/`composeApp` and JS/Wasm are explicitly
out of scope until a later phase) inside the **`scout-kmp/` standalone project**, that
depend on the new multiplatform `scout-kmp/core` module produced in Phase 2, proving the
library works end-to-end outside the JVM-only sample. This phase evolves the *idea*
behind `scout/samples/coffee-maker` into a real multiplatform showcase living in
`scout-kmp/` — the original `scout/samples/coffee-maker` is read for reference only and
is never edited (see `.ai/README.md` "Physical code location" decision).

## 📍 Starting state (read before starting)

- `.ai/migration_plan/phase_2_refactoring_shared.md` — **must be fully checked off**;
  `scout-kmp/core` must build and publish working `jvm` and iOS targets before this
  phase starts.
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
  **read-only reference**; reuse its dependency-graph structure as the reference example
  when creating platform apps under `scout-kmp/`, do not edit the original.
- `scout-kmp/settings.gradle.kts` — the new project's `include` list, set up in Phase 2;
  new platform modules (`androidApp`, `iosApp`) are added here.
- Repository layout (`ls` at repo root) — confirm `scout-kmp/androidApp/`,
  `scout-kmp/iosApp/`, `scout-kmp/composeApp/` don't exist yet; do not assume any
  starter template files are present, and do not create these under `scout/`.

## 📋 Checklist (Definition of Done)

- [ ] `androidApp` module created under `scout-kmp/` with the Android Gradle Plugin
      applied, depending on the shared module's `androidTarget()`/`jvm()` output, and
      able to build a debug APK that instantiates at least one `Scope`/`Component` from
      the shared library.
- [ ] `iosApp` module/target created under `scout-kmp/` with the shared module exposing
      an `iosArm64`/`iosSimulatorArm64`/`iosX64` (or current recommended Kotlin/Native
      iOS target set) framework, and a minimal Swift or Kotlin/Native entry point
      successfully calling into the shared `Scope`.
- [ ] `scout/samples/coffee-maker` is either re-created as a new sample module under
      `scout-kmp/` (copied+adapted, original left untouched) or explicitly kept as the
      sole JVM-only reference and cross-linked from the new `scout-kmp/` modules'
      README — decide and document which, do not leave both diverging silently, and do
      not edit the original `scout/samples/coffee-maker` either way.
- [ ] The `iosApp` module has at least one KSP-generated validation test running in
      `iosTest` (per Phase 4's KSP collector, once available) exercising the same
      dependency graph the JVM `validator` already checks — this is the concrete proof
      that the iOS validation strategy decided in Phase 1 actually works end-to-end, not
      just on paper.
- [ ] All new modules are registered in `scout-kmp/settings.gradle.kts` and build
      successfully via a single `./gradlew build` run from inside `scout-kmp/` (or
      platform-appropriate build command for iOS, e.g. `xcodebuild` invoked through the
      KMP-generated scheme).
- [ ] No modification was made to `scout-kmp/core`'s public API in this phase — if a gap
      is found, it is reported back to Phase 2 rather than patched ad-hoc here. No
      modification was made to `scout/` (the original) at all, ever, in this phase.

## ⚠️ Anti-patterns (forbidden on this phase)

- Do **not** create a `composeApp` module or any JS/Wasm target work in this phase —
  out of scope for this iteration per the confirmed decision in `.ai/README.md`; a new
  explicit user decision is required before expanding scope.
- Do **not** copy-paste a generic KMP wizard template without checking it against the
  versions already pinned in `.ai/context_project.md` (Kotlin version, AGP version) —
  version mismatches are a common source of unbuildable scaffolding.
- Do **not** modify `scout-kmp/core` internals in this phase to "make the sample
  easier" — any required API change must go back through Phase 2's checklist and be
  re-verified there. Do **not** modify `scout/scout/core` (the original) either, ever.
- Do **not** delete `scout/samples/coffee-maker` (the original) — it stays untouched
  permanently; only its `scout-kmp/`-side replacement, if created, is subject to normal
  iteration.
- Do **not** invent new Maven/package coordinates for the platform apps that diverge
  from the namespace decision already recorded in Phase 1/`context_project.md`.
