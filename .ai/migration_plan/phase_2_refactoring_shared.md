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

> Revised 2026-10-19 after a plan review against the actual `scout/` sources (see
> `.ai/README.md` "Plan review 2026-10-19"). Phase 2 is split into ordered steps. Each
> step ends with a green build inside `scout-kmp/` and is its own commit; the phase is
> complete only when every step is ticked. Do not start a step before the previous one
> is green.

### Step 2.0 — KSP collector feasibility spike (throwaway, before any core porting)

- [ ] In a standalone `.ai/experiments/ksp-collector-spike/` project (same style as
      `.ai/experiments/kclass-benchmark/`), prove that a KSP processor running on a
      **test** compilation (`kspJvmTest`, `kspIosSimulatorArm64Test`) can enumerate
      `Component`-like classes declared in the same module's **main** source set and in
      a second module. Background: KSP's `Resolver.getAllFiles()` /
      `getSymbolsWithAnnotation()` only cover the sources of the compilation being
      processed; classes from `main` and from dependencies are visible by name, not
      enumerable. Strategies to evaluate: (a) `@KspExperimental`
      `Resolver.getDeclarationsFromPackage(packageName)` with the packages passed as a
      KSP option, (b) an explicit list of components written in the test, (c) a marker
      annotation plus a step on the main compilation (this breaks the confirmed
      "test compilations only" decision, so only with explicit user sign-off).
- [ ] Results recorded in the spike's `RESULTS.md` and summarized in `.ai/README.md`.
      If no strategy works without breaking a confirmed decision, **stop and ask the
      user** before Step 2.1: the iOS validation strategy in the decisions table would
      have to change, and that affects Phases 3 and 4.

### Step 2.1 — Bootstrap and guard rails

The bootstrap files exist since commit `bc4016a`, but nobody has run a build yet. Tick
these only after `./gradlew build` actually passes.

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
- [ ] A minimal CI job for `scout-kmp/` is added next to the existing `scout/` jobs in
      `.github/workflows/ci.yaml` (existing jobs untouched): `./gradlew :core:jvmTest` on
      `ubuntu-latest` and `./gradlew :core:iosSimulatorArm64Test` on a `macos-*` runner.
      Publishing CI stays in Phase 5; this job exists so a regression is caught from the
      first ported file instead of three phases later.
- [ ] License-header policy agreed with the user and applied (open question, see
      `.ai/README.md`). `scripts/check-license-headers.sh` requires the Yandex header
      from `scripts/license_header` in the first 15 lines of **every** `*.kt` in the
      repository, including `scout-kmp/` and `.ai/experiments/`. Brand-new files such as
      `Placeholder.kt` carry only the fork header and fail the check as soon as CI runs.
      Copied files keep the Yandex header plus the appended fork line; brand-new files
      need a rule (fork-only header accepted by the script, or the combined header).
      The script lives in the repo-root `scripts/`, outside `scout/`, so changing it does
      not break the "never edit `scout/`" rule.

### Step 2.2 — Port core into `commonMain` (JVM target first)

iOS targets may be temporarily commented out in `scout-kmp/core/build.gradle.kts`
during this step (with a TODO pointing to Step 2.4) so that missing `actual`s don't
block the JVM port. They come back in Step 2.4.

- [ ] `scout-kmp/core/src/commonMain` contains (copied and adapted from
      `scout/scout/core/src/main`, package renamed to `dev.gekko.scout.*`): `Component`,
      `Scope`, `Injector`, `Provider`, `Definition`/`Registry`, `InstanceFactory` and its
      `Single`/`Factory` strategies, `Accessor`/`Interceptor` family, all `ScopeBuilder`
      variants, and all `error`/`exception` types — verified by `grep` that none of
      these files import `java.*`/`javax.*` after the copy, and that zero references to
      the bare `package scout` / `import scout.` remain (all renamed to
      `dev.gekko.scout.*`).
- [ ] Every `T::class.java` call site in the moved code is replaced with `T::class`,
      verified by grepping for `::class.java` inside `commonMain` (expected: zero
      matches).
- [ ] **Synchronization primitives.** `kotlin.synchronized(lock) { }` is JVM-only, and
      it is used by `factory/strategy/SingleInstanceFactory.kt` (the double-checked init
      behind every `singleton`), `scope/builder/SynchronizedScopeBuilder.kt`,
      `scope/container/LateInitScope.kt` and `Inject.kt` (`synchronizedInject*`). This
      was missing from the Phase 1 JVM-API inventory. Replace it with a common lock:
      `kotlinx.atomicfu.locks.SynchronizedObject` + `synchronized` (license check
      required, see anti-patterns) or an internal `expect class` lock. `@Volatile`
      becomes `kotlin.concurrent.Volatile` (common). JVM behaviour must stay the same:
      same double-checked locking, and replacing `synchronized(this)` with a dedicated
      lock object is fine.
- [ ] `ParentLookupUtils` no longer uses `java.util.Collections.unmodifiableList`;
      replaced with a common Kotlin API with identical immutability guarantees.

### Step 2.3 — Key abstraction seam

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
- [ ] Read `.ai/design_philosophy.md`'s "`KClass` → `Int` key evolution" section and the
      2026-07-26 empirical benchmark section before finalizing this item. The measured
      data shows: on JVM, `KClass`-keyed `HashMap` lookups are ~9.2x slower than
      `Int`-keyed (confirms the seam is worth keeping ready for a future swap); on iOS,
      the overhead is only ~1.11x (confirms plain `KClass` is fine there permanently, and
      for a different reason than "LLVM AOT" — `KClass` identity is actually stable on
      Kotlin/Native, unlike on JVM). Document this trade-off explicitly in the module's
      public docs/KDoc rather than presenting `commonMain` `class-keys` as a drop-in
      performance equivalent on JVM.

### Step 2.4 — Platform `actual`s and iOS targets

- [ ] iOS targets (`iosX64`, `iosArm64`, `iosSimulatorArm64`) are enabled again and
      `commonMain` compiles for all of them.
- [ ] `ThreadConfinementCheck` is ported as an `expect`/`actual`. The original uses
      `ThreadLocal`, `Thread.currentThread().name` and `AtomicReference`. atomicfu only
      covers `AtomicReference`; it has no `ThreadLocal` and no thread name.
      `kotlin.native.concurrent.ThreadLocal` is an annotation for top-level/object
      properties and is not a per-instance drop-in. `jvmMain` keeps
      `java.lang.ThreadLocal`; `iosMain` needs its own thread identity (for example
      `NSThread.currentThread` or `pthread_self()` from `platform.posix`). The error
      message must still name both threads (or thread ids on iOS).
- [ ] `ReusableInstanceFactory`'s `SoftReference` usage is put behind an
      `expect`/`actual`, and the `jvmMain actual` keeps soft-reference semantics exactly.
      The iOS behaviour is a **user decision** made before implementing (open question,
      see `.ai/README.md`): Kotlin/Native has no soft reference, and a
      `kotlin.native.ref.WeakReference` is cleared at the next GC, so `reusable` would
      behave almost like `factory`. Options: (a) weak reference, documented as
      "best-effort, usually recreated"; (b) strong cache cleared on a memory warning;
      (c) strong reference, which behaves like `singleton`. Whatever is chosen is
      documented in KDoc. Also note that the original class is not thread-safe (plain
      `var`, no lock). Keep that as-is on JVM (no behaviour change) and document it.

### Step 2.5 — Tests and final verification

- [ ] Tests are copied and adapted. The 19 test files use JUnit 5 and AssertJ; AssertJ
      does not exist in `commonTest`, so assertions are rewritten to `kotlin.test`.
      Expect this to be the largest single chunk of Phase 2 work. Tests that use
      `java.util.concurrent` (`ScopeBuilderTest`, `ThreadConfinementCheckTest`,
      `utils/ThreadExtensions.kt`) go to `jvmTest`, with an iOS counterpart for
      `ThreadConfinementCheck` where feasible. All tests pass on `jvm` and the iOS
      simulator target.
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
- Do **not** proceed to Phase 3 while any test in `scout-kmp/core` is failing on any
  target, or while `scout/` no longer builds unmodified. (`inspector`/`validator` are not
  touched in this phase; their `scout-kmp/` copies are Phase 4 work.)
- Do **not** use `kotlin.synchronized`, `java.lang.ThreadLocal` or any other JVM-only
  API in `commonMain`. They may compile in the JVM-only Step 2.2 and then fail as soon
  as iOS targets come back in Step 2.4.
