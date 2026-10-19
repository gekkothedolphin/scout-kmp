# Phase 4 — JVM Tooling Adaptation & Cross-Platform KSP Validator

## 🎯 Goal

This phase has two distinct halves — do not conflate them:

1. **JVM tooling adaptation**: create `scout-kmp/inspector`, `scout-kmp/validator`,
   `scout-kmp/tools/classgraph-collector`, and `scout-kmp/tools/scout-gradle-plugin`
   (including the `compiled-keys` bytecode-rewriting feature, if kept) as **new modules
   inside the `scout-kmp/` standalone project**, copied+adapted from their `scout/`
   originals so they work correctly against the new multiplatform `scout-kmp/core`
   produced in Phase 2. The original `scout/scout/inspector`, `scout/scout/validator`,
   `scout/tools/classgraph-collector`, `scout/tools/scout-gradle-plugin` are **never
   edited** — they remain the frozen JVM-only reference baseline (see `.ai/README.md`
   "Physical code location"). **None of these modules are being redesigned** — they
   stay JVM-only by design in their new `scout-kmp/` location too (see
   `design_philosophy.md` on why ClassGraph + reflection cannot run on Kotlin/Native).
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

- `.ai/migration_plan/phase_2_refactoring_shared.md` — confirm `scout-kmp/core`'s `jvm`
  target is stable and its `KClass<*>`-based key API (via the `DiKey`/`diKeyOf` seam) is
  final before starting this phase.
- `scout/scout/inspector/src/main/kotlin/scout/inspector/ScopeInspector.kt` — **read-only
  reference**; uses `java.util.Collections`; confirm no behavior change is needed
  beyond copying into `scout-kmp/inspector` with updated dependency coordinates and
  renamed package.
- `scout/scout/validator/src/main/kotlin/scout/validator/**` — **read-only reference**;
  heavy use of `java.lang.reflect.Method`, `InvocationTargetException`; this module is
  JVM-only by necessity, its `scout-kmp/validator` copy stays JVM-only too, do not
  attempt a multiplatform port.
- `scout/tools/classgraph-collector/src/main/**` — **read-only reference**; depends on
  the ClassGraph library (JVM classpath scanning); inherently JVM-only, copy as-is
  (updating only dependency coordinates) into `scout-kmp/tools/classgraph-collector`.
- `scout/tools/scout-gradle-plugin/src/main/**` — **read-only reference**; bcel-based
  bytecode rewriting plugin that implements `compiled-keys`; this is a **build-time
  tool**, not a library artifact, and stays JVM-only regardless of KMP adoption
  elsewhere; if kept (see decision below), it is copied into
  `scout-kmp/tools/scout-gradle-plugin` with its signature-matching logic updated.
- `scout/scout/core/src/compiled-keys/kotlin/scout/**` — the `compiled-keys` source set,
  **read-only reference only**, left untouched in `scout/` and never copied into
  `scout-kmp/core` in Phase 2; this phase decides its final fate for `scout-kmp`.
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

### Part A — JVM tooling adaptation (existing modules, copied into `scout-kmp/`, no redesign)

- [ ] `scout-kmp/inspector` and `scout-kmp/validator` are created as copies of
      `scout/scout/inspector`/`scout/scout/validator` (package renamed, build files
      rewritten as `.gradle.kts` depending on `scout-kmp/core`'s `jvm` target artifact),
      and both modules' existing test suites (also copied) pass unmodified in their new
      location.
- [ ] `scout-kmp/tools/classgraph-collector` is created as a copy of
      `scout/tools/classgraph-collector`, compiling against `scout-kmp/core`'s `jvm`
      artifact with no source changes beyond dependency coordinates and package rename.
- [ ] Explicit decision recorded (with user sign-off) on whether to **swap the JVM/
      Android `DiKey`/`diKeyOf` actual** (the `expect`/`actual` seam already built in
      Phase 2, currently `KClass`-backed on both platforms, inside `scout-kmp/core`)
      **to an `Int`-backed implementation** using `compiled-keys`: either (a) swap it,
      creating `scout-kmp/tools/scout-gradle-plugin` (copied+adapted from
      `scout/tools/scout-gradle-plugin`) as a JVM-only opt-in layered behind the existing
      seam — the iOS actual and all `commonMain` call sites remain untouched since they
      only ever call `diKeyOf`, or (b) leave JVM/Android on the same plain-`KClass`
      actual as iOS permanently and never create a `scout-kmp` copy of
      `scout-gradle-plugin` at all. **This decision must be based on the benchmark
      comparison below, not made speculatively, and only decides which actual
      implementation JVM/Android uses — the abstraction itself is already built and is
      not being changed. Either way, `scout/tools/scout-gradle-plugin` itself is never
      edited — it remains the untouched original.**
- [ ] **Benchmark comparison against the Phase-1 baseline is completed first**: run the
      same `measures/jvm-benchmarks` suite methodology (cold/warm get, graph init at
      5/25/125 nodes, constant get, etc.) — copied into `scout-kmp/measures/jvm-benchmarks`
      and adapted to depend on `scout-kmp/core` — and compare against the baseline
      captured in Phase 1 from the original, untouched `scout/measures/jvm-benchmarks`
      (`.ai/benchmarks/baseline/`). Document the measured regression (if any) from the
      `KClass`-based `class-keys` port before deciding `compiled-keys`'s fate for
      `scout-kmp` — this is the data point the Phase 1/2 deferral was explicitly waiting
      for.
- [ ] Android performance data point: the Phase 1 baseline was measured on HotSpot
      (Corretto 11, macOS arm64), but Android runs on ART with a different JIT and GC,
      so the ~9.2x `KClass`-vs-`Int` gap may differ there. If the `compiled-keys`
      decision is motivated by Android, run at least the `get`/`graph-init` scenarios on
      a real device (for example with Jetpack Microbenchmark) for both `DiKey` actuals
      before asking the user to decide.
- [ ] If option (a) is chosen: `scout-kmp/tools/scout-gradle-plugin`'s bytecode
      rewriting logic is verified to correctly target the `jvm`-compiled classfiles
      produced by the `scout-kmp/core` build (Kotlin/JVM classfile output format must not
      differ in a way that breaks bcel parsing), **and** its hardcoded method signature
      matching (`"(Ljava/lang/Class;)I"`) is updated to match the new
      `diKeyOf(KClass<T>): DiKey` portal introduced in Phase 2 — this rewrite is
      required regardless of the compiled-keys fate decision, because the signature
      change alone already breaks the plugin's pattern matching.
- [ ] If option (a) is chosen: build-tool compatibility is verified, not assumed. The
      original plugin rewrites class files in a `doLast` action on compile tasks. Check
      it against KMP task names (`compileKotlinJvm`, Android compile tasks), the Gradle
      configuration cache and build cache, the current Android Gradle Plugin (the legacy
      Transform API is gone; `AsmClassVisitorFactory` is the supported hook), and the
      bytecode that Kotlin 2.x emits for inlined `T::class` + `diKeyOf(...)` call sites.
- [ ] If option (b) is chosen: no `scout-kmp/tools/scout-gradle-plugin` module is
      created at all; document in `scout-kmp`'s own README that `compiled-keys` was
      evaluated and intentionally not ported, with a link to the benchmark comparison
      data justifying the decision.
- [ ] `scout-kmp/measures/jvm-benchmarks` and (if needed) a `scout-kmp` equivalent of
      `measures/graph-generator-plugin` are created referencing `scout-kmp/core`'s
      coordinates and run successfully, since they benchmark real performance
      characteristics that must not silently regress relative to the Phase 1 baseline.
      The original `scout/measures/*` stays untouched, still runnable as the baseline
      reference forever.

### Part B — New cross-platform KSP validator (new module, additive)

- [ ] The component discovery strategy is the one validated by the Phase 2 Step 2.0
      spike (`.ai/experiments/ksp-collector-spike/RESULTS.md`). KSP on a test
      compilation cannot simply enumerate classes from `main` or from other modules, so
      do not start this part without that result.
- [ ] A new Gradle module (e.g. `scout-kmp/tools/ksp-component-collector`) is created
      containing
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
      fork-only Apache 2.0 header `scripts/license_header_fork` (new code; copied
      modules use `scripts/license_header_modified`, see `.ai/context_project.md`); no code is copied from `kotlin-inject`/`Metro` — architecture patterns
      only, not verbatim source.
- [ ] Documentation added (module README or KDoc) explicitly stating: (a) this tool
      exists only to unblock iOS/Native validation, (b) the JVM/Android path continues
      to use the original `scout/validator` + `classgraph-collector`, (c) it is
      test-scope only and must never appear in a production/main dependency graph.

- [ ] The `iosApp` sample (Phase 3) has at least one KSP-generated validation test
      running on an iOS simulator target, exercising the same dependency graph the JVM
      `validator` checks. This is the end-to-end proof that the iOS validation strategy
      works, not just on paper. (Moved here from Phase 3, 2026-10-19.)

- [ ] Full `./gradlew build` inside `scout-kmp/` (all modules) succeeds, and `scout/`
      still builds unmodified. If a Gradle plugin module is created, it is wired as an
      included build (`pluginManagement { includeBuild(...) }`), because a plugin cannot
      be applied in the same build that compiles it.

## ⚠️ Anti-patterns (forbidden on this phase)

- Do **not** edit any file under `scout/` (the original) in this phase, or any phase —
  it is the permanently frozen reference baseline. All work happens by creating new
  files/modules under `scout-kmp/`.
- Do **not** attempt to port `java.lang.reflect`-based validator/inspector logic into
  `commonMain` — reflection-heavy tooling is legitimately JVM-only; forcing a common
  abstraction here adds complexity with no multiplatform benefit. Build the new KSP
  collector as a **separate, additive** module (`scout-kmp/tools/ksp-component-collector`)
  instead of modifying `scout-kmp/validator` or the original `scout/validator`.
- Do **not** decide the fate of `compiled-keys` unilaterally — this is a
  backward-compatibility-affecting decision for existing JVM consumers of the library
  and requires explicit user sign-off.
- Do **not** break the existing `scout/release-base.sh` / `scout/release-compiled.sh` /
  `scout/release-plugin.sh` / `scout/release-all.sh` publishing scripts — they are part
  of the frozen `scout/` original and are never edited. `scout-kmp/` gets its own,
  separate publishing scripts (Phase 5 concern) for the new Maven coordinates.
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
