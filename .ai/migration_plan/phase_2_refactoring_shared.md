# Phase 2 — Shared Module Refactoring

## 🎯 Goal

Create a genuine Kotlin Multiplatform module — **`scout-kmp/core`**, inside a new
standalone Gradle project (`scout-kmp/`, own `settings.gradle.kts` + Gradle wrapper) —
with a `commonMain` source set containing all platform-agnostic DI logic, backed by
`jvmMain` and `iosMain`/`iosArm64Main`/`iosSimulatorArm64Main`/`iosX64Main` actuals
(confirmed target set: **JVM/Android + iOS** for this iteration; JS/Wasm explicitly
out of scope) for the handful of platform-specific APIs identified during analysis.

**This is a copy-and-adapt operation, not a move.** The original `scout/scout/core` is
**never edited** — it stays exactly as-is, permanently, as the JVM-only reference
baseline for benchmark comparisons and rollback safety (see `.ai/README.md` "Physical
code location" decision). Code is read from `scout/scout/core`, then written into
`scout-kmp/core` with the package rename and Kotlin version already applied at copy
time — there is no separate "rename scout/ in place" step anymore.

Downstream JVM-only modules (`inspector`, `validator`, `classgraph-collector`) are
**not** touched in this phase — they keep compiling against the original, untouched
`scout/scout/core` for now. Porting them to depend on the new `scout-kmp/core` (by
creating `scout-kmp/inspector`, `scout-kmp/validator`, etc. as copies) is Phase 4's
responsibility (see `phase_4_tooling_jvm.md` Part A), once the new core's public API is
stable and benchmark-compared.

## 📍 Starting state (read before starting)

- `.ai/migration_plan/phase_1_analysis.md` — **must be fully checked off** before
  starting this phase; re-read its findings table (JVM API → replacement strategy).
- `.ai/context_project.md` — package/namespace and Kotlin version decisions recorded
  during Phase 1.
- `.ai/README.md` — "Physical code location" decision: `scout-kmp/` is a new,
  standalone top-level directory/Gradle project, sibling to `scout/`, not a
  modification of it.
- `scout/scout/core/build.gradle` — current JVM-only build script; **read for
  reference only**, do not edit. `scout-kmp/core/build.gradle.kts` is a brand-new file
  applying `org.jetbrains.kotlin.multiplatform` from scratch.
- `scout/scout/core/src/main/kotlin/scout/**` — files to **copy** (not move) into
  `scout-kmp/core/src/commonMain/kotlin/dev/gekko/scout/**`, renaming the package as
  part of the copy.
- `scout/scout/core/src/class-keys/kotlin/scout/**` — key definitions to adapt
  (`Class<*>` → `KClass<*>`) and copy into the same `commonMain` location.
- `scout/scout/core/src/compiled-keys/kotlin/scout/**` — **read-only reference only**;
  do not copy or attempt to port this variant in this phase (see Phase 4 for its fate —
  Phase 4 may later add an `actual` implementation informed by this code, but that is
  new code written against the `DiKey`/`diKeyOf` seam, not a copy of this source set).
- `scout/scout/core/src/test/**` — existing JUnit5 tests, to be **copied and adapted**
  to `kotlin.test` under `scout-kmp/core/src/commonTest`/`jvmTest`.
- (Not needed yet) `scout/scout/inspector/build.gradle`, `scout/scout/validator/build.gradle`
  — these stay untouched in this phase; their `scout-kmp/` equivalents are Phase 4 work.

## 📋 Checklist (Definition of Done)

- [ ] **Bootstrap the `scout-kmp/` standalone Gradle project**: create
      `scout-kmp/settings.gradle.kts` (module name `scout-kmp`, `pluginManagement`/
      `dependencyResolutionManagement` repositories: `google()`, `mavenCentral()`,
      `gradlePluginPortal()`), copy a Gradle wrapper into `scout-kmp/gradle/` +
      `scout-kmp/gradlew` (the current stable Gradle version, independent of `scout/`'s
      pinned `8.2.1` if a newer wrapper is needed for the chosen Kotlin version), and a
      root `scout-kmp/build.gradle.kts`. This project has **no dependency on `scout/`'s
      Gradle build** — it is fully independent.
- [ ] Kotlin version for `scout-kmp/` is set to the current stable release (verify the
      actual latest stable version at execution time — do not hardcode a version from
      this planning document, which may already be outdated). This is independent of
      `scout/`'s own pinned `1.8.22`, which remains unchanged forever.
- [ ] `scout-kmp/core/build.gradle.kts` applies `org.jetbrains.kotlin.multiplatform`,
      with `jvm()` and the iOS target set (`iosArm64()`, `iosSimulatorArm64()`,
      `iosX64()` or the current recommended Kotlin/Native default target list)
      declared.
- [ ] `scout-kmp/core/src/commonMain` contains (copied and adapted from
      `scout/scout/core/src/main`, package renamed to `dev.gekko.scout.*`): `Component`,
      `Scope`, `Injector`, `Provider`, `Definition`/`Registry`, `InstanceFactory` and its
      `Single`/`Factory` strategies, `Accessor`/`Interceptor` family, all `ScopeBuilder`
      variants, and all `error`/`exception` types — verified by `grep` that none of
      these files import `java.*`/`javax.*` after the copy, and that zero references to
      the bare `package scout` / `import scout.` remain (all renamed to
      `dev.gekko.scout.*`).
- [ ] **Key abstraction seam** (`expect`/`actual`): introduce `expect class DiKey` in
      `commonMain`, replacing today's `Class<*>`-based `ObjectKey`/`CollectionKey`/
      `AssociationKey` typealiases, plus a single creation portal
      `expect fun <T : Any> diKeyOf(kClass: KClass<T>): DiKey`. **All** key creation in
      `Registry`/`InstanceFactory`/`Component` internals must route through `diKeyOf` —
      grep to confirm zero direct `KClass`/`Class` literals used as keys anywhere else
      in `commonMain`. This is what lets Phase 4 swap the JVM/Android implementation
      later without touching any call site.
- [ ] Both `jvmMain` and `iosMain` initially implement the seam identically:
      `actual typealias DiKey = KClass<*>` with a trivial `actual fun <T : Any>
      diKeyOf(kClass: KClass<T>): DiKey = kClass` on **both** platforms. Do **not**
      implement an `Int`-backed JVM actual in this phase — see anti-patterns below, that
      is explicitly Phase 4's decision, made from benchmark data, not built speculatively
      here.
- [ ] Every `T::class.java` call site in the moved code is replaced with `T::class`,
      verified by grepping for `::class.java` inside `commonMain` (expected: zero
      matches).
- [ ] Read `.ai/design_philosophy.md`'s "`KClass` → `Int` key evolution" section and the
      2026-07-26 empirical benchmark section before finalizing this item. The measured
      data shows: on JVM, `KClass`-keyed `HashMap` lookups are ~9.2x slower than
      `Int`-keyed (confirms the seam is worth keeping ready for a future swap); on iOS,
      the overhead is only ~1.11x (confirms plain `KClass` is fine there permanently, and
      for a different reason than "LLVM AOT" — `KClass` identity is actually stable on
      Kotlin/Native, unlike on JVM). Document this trade-off explicitly in the module's
      public docs/KDoc rather than presenting `commonMain` `class-keys` as a drop-in
      performance equivalent on JVM.
- [ ] `ThreadConfinementCheck` is implemented as an `expect class` in `commonMain` with
      a `jvmMain actual` (using the strategy recorded in Phase 1 — `kotlinx-atomicfu` or
      equivalent) and an `iosMain actual` (Kotlin/Native has no `java.util.concurrent`,
      confirm the atomicfu or `kotlin.native.concurrent` equivalent works correctly
      under the new Kotlin/Native memory model).
- [ ] `ReusableInstanceFactory`'s `SoftReference` usage is abstracted behind an
      `expect`/`actual` type, with the `jvmMain actual` preserving current soft-reference
      semantics exactly (no behavior change for existing JVM consumers), and the
      `iosMain actual` implemented using `kotlin.native.ref.WeakReference` (note: this is
      a *weak*, not *soft*, reference — Kotlin/Native has no soft-reference equivalent;
      document this semantic difference explicitly rather than presenting it as
      equivalent behavior).
- [ ] `ParentLookupUtils` no longer uses `java.util.Collections.unmodifiableList`;
      replaced with a common Kotlin API with identical immutability guarantees.
- [ ] Tests are copied and adapted to `commonTest` (or `jvmTest`/`iosTest` where
      genuinely platform-specific) using `kotlin.test`, and pass on both the `jvm` and
      iOS (simulator) targets, within `scout-kmp/core`.
- [ ] `./gradlew build` succeeds for the standalone `scout-kmp/` project (its `core`
      module only, at this point — downstream modules are added in later phases).
      Separately, confirm `scout/` (the original project) still builds unmodified
      exactly as before this phase started (e.g. `git diff --stat scout/` shows zero
      changes) — this is the concrete proof the "never edit `scout/`" rule was honored.

## ⚠️ Anti-patterns (forbidden on this phase)

- Do **not** edit, rename, or `git mv` **any** file under `scout/` in this phase — it is
  the permanently frozen reference baseline. All work happens by creating new files
  under `scout-kmp/`. If you find yourself wanting to change something in `scout/`,
  stop — that almost certainly means you're confusing this phase with the pre-KMP-era
  in-place refactor plan, which no longer applies.
- Do **not** attempt to port/wire up `inspector`, `validator`, or `classgraph-collector`
  in this phase, whether against `scout/` or `scout-kmp/` — that is Phase 4's
  responsibility, once `scout-kmp/core`'s API is stable and benchmark-compared.
- Do **not** touch the `compiled-keys` source set or `scout-gradle-plugin` in this
  phase — that is Phase 4's responsibility.
- Do **not** change the public API surface of `core` (method signatures, class names)
  beyond the `Class<*>` → `KClass<*>` key type change explicitly required for KMP
  portability, unless the user explicitly approves a broader API change.
- Do **not** silently drop `internal` visibility modifiers when copying files between
  source sets — multiplatform source sets support `internal` per source-set-hierarchy;
  verify visibility is preserved correctly.
- Do **not** introduce new third-party multiplatform dependencies (e.g.
  `kotlinx-atomicfu`) without confirming license compatibility (Apache 2.0 or
  equivalent permissive license) and recording it in `context_project.md`.
- Do **not** modify or delete existing Yandex copyright headers in files being moved —
  copy them as-is into the new source set location.
- Do **not** implement an `Int`-backed `DiKey` actual for JVM/Android in this phase,
  even as a "just in case" optimization — that swap is Phase 4's decision, made from
  measured benchmark data (see `phase4-compiled-keys-decision`), not something to
  pre-empt speculatively while building the seam.
- Do **not** reintroduce a generic "smart" `get<T>()` that inspects `typeOf<T>()` to
  auto-detect `Lazy`/`Provider`/collection/nullable wrapping, even if it looks like a
  nice ergonomic improvement for `commonMain`. This was tried by the original authors
  and caused a severe, hard-to-diagnose startup performance regression — see
  `.ai/design_philosophy.md`. Keep the explicit `get`/`opt`/`getLazy`/`getProvider`/
  `collect`/`associate` method family as-is.
- Do **not** proceed to Phase 3 while `inspector`/`validator` are broken or any test in
  `core` is failing.
