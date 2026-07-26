# Phase 4 — JVM-only Tooling Adaptation

## 🎯 Goal

Adapt the JVM-only tooling ecosystem around Scout — `scout/inspector`,
`scout/validator`, `tools/classgraph-collector`, and `tools/scout-gradle-plugin`
(including the `compiled-keys` bytecode-rewriting feature) — so it continues to work
correctly against the new multiplatform `core` produced in Phase 2, without attempting
to force genuinely JVM-reflection-based code into `commonMain`.

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

## 📋 Checklist (Definition of Done)

- [ ] `scout/scout/inspector` and `scout/scout/validator` build.gradle files are
      updated (if not already done in Phase 2) to depend on the `jvm` target artifact of
      the multiplatform `core`, and both modules' existing test suites pass unmodified.
- [ ] `tools/classgraph-collector` compiles against the new `core` `jvm` artifact with
      no source changes beyond dependency coordinates.
- [ ] Explicit decision recorded (with user sign-off) on the fate of `compiled-keys`:
      either (a) kept as a JVM-only opt-in feature layered on top of the multiplatform
      `core`'s `jvm` target only, with clear documentation that it is unavailable on
      other targets, or (b) deprecated/removed in favor of `class-keys` for the
      multiplatform-first fork. Do not leave this undecided.
- [ ] If option (a) is chosen: `scout-gradle-plugin`'s bytecode rewriting logic is
      verified to still correctly target the `jvm`-compiled classfiles produced by the
      new multiplatform build (Kotlin/JVM classfile output format must not have changed
      in a way that breaks bcel parsing).
- [ ] If option (b) is chosen: `compiled-keys` source set, its build.gradle
      `withCompiledKeys` conditional, and the `scout-gradle-plugin` module are removed or
      clearly marked deprecated, with `release-compiled.sh` and `release-plugin.sh`
      scripts updated or retired accordingly.
- [ ] `measures/jvm-benchmarks` and `measures/graph-generator-plugin` are updated to
      reference the new `core` coordinates and still run successfully, since they
      benchmark real performance characteristics that must not silently regress due to
      the multiplatform refactor.
- [ ] Full root `./gradlew build` (all modules, both `scout` and `tools` composite
      builds) succeeds.

## ⚠️ Anti-patterns (forbidden on this phase)

- Do **not** attempt to port `java.lang.reflect`-based validator/inspector logic into
  `commonMain` — reflection-heavy tooling is legitimately JVM-only; forcing a common
  abstraction here adds complexity with no multiplatform benefit.
- Do **not** decide the fate of `compiled-keys` unilaterally — this is a
  backward-compatibility-affecting decision for existing JVM consumers of the library
  and requires explicit user sign-off.
- Do **not** break the existing `release-base.sh` / `release-compiled.sh` /
  `release-plugin.sh` / `release-all.sh` publishing scripts without updating them in the
  same change — a partially-updated release pipeline is worse than an unchanged one.
- Do **not** silently change benchmark methodology in `measures/jvm-benchmarks` while
  "just updating dependencies" — flag any measured performance change explicitly.
