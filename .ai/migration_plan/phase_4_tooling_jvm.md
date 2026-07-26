# Phase 4 — JVM Tooling Adaptation & Cross-Platform KSP Validator

## 🎯 Goal

This phase has two distinct halves — do not conflate them:

1. **JVM tooling adaptation**: adapt the existing JVM-only tooling ecosystem —
   `scout/inspector`, `scout/validator`, `tools/classgraph-collector`, and
   `tools/scout-gradle-plugin` (including the `compiled-keys` bytecode-rewriting
   feature) — so it continues to work correctly against the new multiplatform `core`
   produced in Phase 2. **None of these existing modules are being replaced or ported**
   — they stay JVM-only by design (see `design_philosophy.md` on why ClassGraph +
   reflection cannot run on Kotlin/Native).
2. **New cross-platform KSP validator (new work, not a port)**: build a new,
   additional component-collection tool using Kotlin Symbol Processing, wired only into
   test compilations (`kspJvmTest`, `kspIosX64Test`, `kspIosArm64Test`,
   `kspIosSimulatorArm64Test` — KSP has no single `kspCommonTest` configuration, it is
   always per-target, see `.ai/README.md`), to unblock automated graph validation on
   iOS/Native where ClassGraph/reflection cannot run at all. This generates direct,
   non-reflective Kotlin invocation code at compile time and must never be wired into
   `commonMain`/production compilations, matching Scout's founding principle that graph
   validation belongs in tests, not in shipped codegen (see `design_philosophy.md`).

## 📍 Starting state (read before starting)

- `.ai/migration_plan/phase_2_refactoring_shared.md` — confirm `core`'s `jvm` target is
  stable and its `KClass<*>`-based key API is final before starting this phase.
- `scout/scout/inspector/src/main/kotlin/scout/inspector/ScopeInspector.kt` — uses
  `java.util.Collections`; confirm no behavior change is needed beyond dependency
  wiring to the new `core`.
- `scout/scout/validator/src/main/kotlin/scout/validator/**` — heavy use of
  `java.lang.reflect.Method`, `InvocationTargetException`; this module is JVM-only by
  necessity, do not attempt multiplatform port.
- `scout/tools/classgraph-collector/src/main/**` — depends on the ClassGraph library
  (JVM classpath scanning); inherently JVM-only.
- `scout/tools/scout-gradle-plugin/src/main/**` — bcel-based bytecode rewriting plugin
  that implements `compiled-keys`; this is a **build-time tool**, not a library
  artifact, and stays JVM-only regardless of KMP adoption elsewhere.
- `scout/scout/core/src/compiled-keys/kotlin/scout/**` — the `compiled-keys` source set
  left untouched since Phase 2; this phase decides its final fate.
- `.ai/context_project.md` — "compiled-keys" glossary entry and JVM-dependency table.
- `.ai/design_philosophy.md` — "`KClass` → `Int` key evolution" section explains
  **why** `compiled-keys` exists (measured JVM class-loading/hashing/caching costs of
  `KClass` at ~6000-node scale) — read this before deciding its fate below. Also read
  the validation algorithm section: the original approach is (1) find `Component`
  subclasses via ClassGraph, (2) reflectively instantiate them, (3) call every method
  and force-evaluate any `Lazy`/`Provider` result — the new KSP collector must
  reproduce steps (2) and (3) via **generated source code**, not reflection, since
  Kotlin/Native has no `Method.invoke` equivalent.
- `scout/scout/validator/src/main/kotlin/scout/validator/tools/method/MethodInvoker.kt`
  and `MethodArgumentProducer.kt` — study this JVM reflection-based invocation logic
  as the behavioral spec the new KSP-generated code must replicate (not copy — the
  mechanism is fundamentally different, but the observable behavior, e.g. how
  `Lazy`/`Provider` results are force-evaluated, must match).
- [KSP multiplatform docs](https://kotlinlang.org/docs/ksp-multiplatform.html) — confirms
  KSP configuration is per-target (`kspJvmTest`, `kspIosX64Test`, etc.), no unified
  `kspCommonTest`. Re-verify against the KSP version compatible with whichever Kotlin
  version was chosen in Phase 2 (KSP releases are pinned to specific Kotlin versions).
- `evant/kotlin-inject` and `zacsweers/metro` (GitHub, both Apache 2.0) — reference-only
  architecture study for multiplatform KSP processor design (per-target wiring,
  annotation-driven collection). Do **not** add either as a runtime dependency of
  Scout.

## 📋 Checklist (Definition of Done)

### Part A — JVM tooling adaptation (existing modules, no replacement)

- [ ] `scout/scout/inspector` and `scout/scout/validator` build.gradle files are
      updated (if not already done in Phase 2) to depend on the `jvm` target artifact of
      the multiplatform `core`, and both modules' existing test suites pass unmodified.
- [ ] `tools/classgraph-collector` compiles against the new `core` `jvm` artifact with
      no source changes beyond dependency coordinates.
- [ ] Explicit decision recorded (with user sign-off) on whether to **swap the JVM/
      Android `DiKey`/`diKeyOf` actual** (the `expect`/`actual` seam already built in
      Phase 2, currently `KClass`-backed on both platforms) **to an `Int`-backed
      implementation** using `compiled-keys`: either (a) swap it, keeping
      `compiled-keys` as a JVM-only opt-in layered behind the existing seam — the iOS
      actual and all `commonMain` call sites remain untouched since they only ever call
      `diKeyOf`, or (b) leave JVM/Android on the same plain-`KClass` actual as iOS
      permanently and deprecate/remove `compiled-keys` + `scout-gradle-plugin`. **This
      decision must be based on the benchmark comparison below, not made speculatively,
      and only decides which actual implementation JVM/Android uses — the abstraction
      itself is already built and is not being changed.**
- [ ] **Benchmark comparison against the Phase-1 baseline is completed first**: re-run
      the same `measures/jvm-benchmarks` suite (cold/warm get, graph init at 5/25/125
      nodes, constant get, etc.) against the new multiplatform `core`'s `jvm` target,
      and compare against the baseline captured in Phase 1 (`.ai/benchmarks/baseline/`).
      Document the measured regression (if any) from the `KClass`-based `class-keys`
      port before deciding `compiled-keys`'s fate — this is the data point the Phase 1/2
      deferral was explicitly waiting for.
- [ ] If option (a) is chosen: `scout-gradle-plugin`'s bytecode rewriting logic is
      verified to still correctly target the `jvm`-compiled classfiles produced by the
      new multiplatform build (Kotlin/JVM classfile output format must not have changed
      in a way that breaks bcel parsing), **and** its hardcoded method signature
      matching (`"(Ljava/lang/Class;)I"`) is updated to match the new `diKeyOf(KClass<T>):
      DiKey` portal introduced in Phase 2 — this rewrite is required regardless of the
      compiled-keys fate decision, because the signature change alone already breaks the
      plugin's pattern matching.
- [ ] If option (b) is chosen: `compiled-keys` source set, its build.gradle
      `withCompiledKeys` conditional, and the `scout-gradle-plugin` module are removed or
      clearly marked deprecated, with `release-compiled.sh` and `release-plugin.sh`
      scripts updated or retired accordingly.
- [ ] `measures/jvm-benchmarks` and `measures/graph-generator-plugin` are updated to
      reference the new `core` coordinates and still run successfully, since they
      benchmark real performance characteristics that must not silently regress due to
      the multiplatform refactor.

### Part B — New cross-platform KSP validator (new module, additive)

- [ ] A new Gradle module (e.g. `tools/ksp-component-collector`) is created containing
      a KSP `SymbolProcessorProvider` that scans for `Component` subclasses (or a
      dedicated marker annotation, e.g. `@ScoutComponent`, if plain-subclass detection
      proves awkward in KSP — decide and document which detection strategy is used) and
      generates, via **KotlinPoet**, a source file per compilation containing direct
      (non-reflective) calls to every discovered `Component`'s public methods, forcing
      evaluation of any `Lazy`/`Provider` return values — replicating the *observable
      behavior* of `MethodInvoker`/`MethodArgumentProducer` without using reflection.
- [ ] The new module's Gradle wiring uses the **correct per-target KSP configuration
      names** — `kspJvmTest`, `kspIosX64Test`, `kspIosArm64Test`,
      `kspIosSimulatorArm64Test` (verify exact names via
      `./gradlew :<module>:dependencies | grep ksp` as recommended by KSP docs) — and
      is **never** added to any `main`/production configuration (`kspJvm`, `kspIosX64`,
      etc.) in `commonMain`-consuming modules.
- [ ] The generated invocation code is proven to actually run and fail correctly: write
      a deliberately broken test fixture (e.g. a `Component` requesting an unregistered
      type) and confirm the KSP-generated test fails with a clear error on **both** the
      `jvmTest` and an iOS simulator test target — parity with the existing JVM
      validator's failure behavior is the acceptance bar, not just "it compiles".
- [ ] License and licensing-header compliance: the new module's source files carry the
      fork's standard Apache 2.0 header (see `.ai/context_project.md` code style
      section); no code is copied from `kotlin-inject`/`Metro` — architecture patterns
      only, not verbatim source.
- [ ] Documentation added (module README or KDoc) explicitly stating: (a) this tool
      exists only to unblock iOS/Native validation, (b) the JVM/Android path continues
      to use the original `scout/validator` + `classgraph-collector`, (c) it is
      test-scope only and must never appear in a production/main dependency graph.

- [ ] Full root `./gradlew build` (all modules, both `scout` and `tools` composite
      builds) succeeds.

## ⚠️ Anti-patterns (forbidden on this phase)

- Do **not** attempt to port `java.lang.reflect`-based validator/inspector logic into
  `commonMain` — reflection-heavy tooling is legitimately JVM-only; forcing a common
  abstraction here adds complexity with no multiplatform benefit. Build the new KSP
  collector as a **separate, additive** module instead of modifying `scout/validator`.
- Do **not** decide the fate of `compiled-keys` unilaterally — this is a
  backward-compatibility-affecting decision for existing JVM consumers of the library
  and requires explicit user sign-off.
- Do **not** break the existing `release-base.sh` / `release-compiled.sh` /
  `release-plugin.sh` / `release-all.sh` publishing scripts without updating them in the
  same change — a partially-updated release pipeline is worse than an unchanged one.
- Do **not** silently change benchmark methodology in `measures/jvm-benchmarks` while
  "just updating dependencies" — flag any measured performance change explicitly.
- Do **not** write `kspCommonTest` (or any other made-up "common" KSP configuration
  name) in Gradle scripts — it does not exist; KSP multiplatform configuration is
  always per-target (`ksp<Target>Test`). Verify actual configuration names with
  `./gradlew :<module>:dependencies | grep ksp` rather than assuming.
- Do **not** wire the new KSP processor into any `main`/production KSP configuration
  (`kspJvm`, `kspIosX64`, `kspCommonMainMetadata`, etc.) — this directly violates the
  confirmed decision that commonMain must remain free of generated code, and
  reintroduces the exact kapt-style build-time cost Scout was created to avoid (see
  `design_philosophy.md`).
- Do **not** add `kotlin-inject` or `Metro` as a runtime/compile dependency of any
  Scout module — they are reference material for KSP architecture only; depending on
  them would couple Scout to a second, unrelated DI framework.
- Do **not** copy source code from `kotlin-inject`/`Metro` verbatim, even though both
  are Apache 2.0 licensed — reimplement the narrow logic Scout actually needs; verbatim
  copying creates unnecessary license attribution obligations and coupling to their
  release cadence.
