# Phase 2 — Shared Module Refactoring

## 🎯 Goal

Turn `scout/scout/core` into a genuine Kotlin Multiplatform module with a
`commonMain` source set containing all platform-agnostic DI logic, backed by
`jvmMain` and `iosMain`/`iosArm64Main`/`iosSimulatorArm64Main`/`iosX64Main` actuals
(confirmed target set: **JVM/Android + iOS** for this iteration; JS/Wasm explicitly
out of scope) for the handful of platform-specific APIs identified during analysis.
This phase also executes the confirmed package rename (`scout.*` →
`dev.gekko.scout.*`) and the Kotlin version bump, since both must happen before or
alongside the multiplatform conversion to avoid a second disruptive rename later. All
downstream JVM-only modules (`inspector`, `validator`, `classgraph-collector`) must
keep compiling against the new `jvm` target of `core` without any public API break
beyond the confirmed package/key-type changes.

## 📍 Starting state (read before starting)

- `.ai/migration_plan/phase_1_analysis.md` — **must be fully checked off** before
  starting this phase; re-read its findings table (JVM API → replacement strategy).
- `.ai/context_project.md` — package/namespace and Kotlin version decisions recorded
  during Phase 1.
- `scout/scout/core/build.gradle` — current JVM-only plugin block to be replaced with
  `org.jetbrains.kotlin.multiplatform`.
- `scout/scout/core/src/main/kotlin/scout/**` — files to move into `commonMain`.
- `scout/scout/core/src/class-keys/kotlin/scout/**` — key definitions to adapt
  (`Class<*>` → `KClass<*>`) and move into `commonMain`.
- `scout/scout/core/src/compiled-keys/kotlin/scout/**` — must remain JVM-only; do not
  attempt to port this variant in this phase (see Phase 4 for its fate).
- `scout/scout/core/src/test/**` — existing JUnit5 tests to be migrated to
  `kotlin.test` under `commonTest`/`jvmTest`.
- `scout/scout/inspector/build.gradle`, `scout/scout/validator/build.gradle` — confirm
  how they reference `scout_core_module`; this dependency wiring must be updated to
  target the `jvm` variant of the new multiplatform `core`.

## 📋 Checklist (Definition of Done)

- [ ] Kotlin version is bumped from `1.8.22` to the current stable release (verify the
      actual latest stable version at execution time — do not hardcode a version from
      this planning document, which may already be outdated) across `versions.gradle`
      and all module `build.gradle` files, and the project still builds on the `jvm`
      target before any multiplatform conversion work starts (isolate the version bump
      as its own verifiable commit/step).
- [ ] Kotlin package namespace is renamed from `scout.*` to **`dev.gekko.scout.*`**
      across all files in `scout/scout/core` (and transitively in `inspector`,
      `validator`, `classgraph-collector`, `scout-gradle-plugin` once they're updated to
      depend on the renamed `core`) — verify via grep that zero references to the bare
      `package scout` / `import scout.` remain unrenamed. Update `AUTHORS`/license
      header package references if any exist. This must be a dedicated, isolated
      rename step (ideally its own commit) completed and verified building *before*
      starting the `commonMain` extraction below, to keep the two changes independently
      revertible/reviewable.
- [ ] `scout/scout/core/build.gradle` applies `org.jetbrains.kotlin.multiplatform`
      instead of `org.jetbrains.kotlin.jvm`, with `jvm()` and the iOS target set
      (`iosArm64()`, `iosSimulatorArm64()`, `iosX64()` or the current recommended
      Kotlin/Native default target list) declared.
- [ ] `commonMain` contains: `Component`, `Scope`, `Injector`, `Provider`,
      `Definition`/`Registry`, `InstanceFactory` and its `Single`/`Factory` strategies,
      `Accessor`/`Interceptor` family, all `ScopeBuilder` variants, and all
      `error`/`exception` types — verified by `grep` that none of these files import
      `java.*`/`javax.*` after the move.
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
- [ ] All package/namespace renames decided in Phase 1 (if any) are applied
      consistently across `commonMain` and `jvmMain`, with no leftover references to the
      old namespace.
- [ ] Tests are migrated to `commonTest` (or `jvmTest`/`iosTest` where genuinely
      platform-specific) using `kotlin.test`, and pass on both the `jvm` and iOS
      (simulator) targets.
- [ ] `scout/scout/inspector` and `scout/scout/validator` build.gradle dependency
      declarations are updated to consume the multiplatform `core`'s `jvm` artifact, and
      both modules compile and their existing test suites pass unmodified (no source
      changes in `inspector`/`validator` beyond dependency wiring, unless an API break is
      unavoidable and explicitly approved by the user).
- [ ] `./gradlew build` succeeds for the whole `scout` root project.

## ⚠️ Anti-patterns (forbidden on this phase)

- Do **not** touch the `compiled-keys` source set or `scout-gradle-plugin` in this
  phase — that is Phase 4's responsibility.
- Do **not** change the public API surface of `core` (method signatures, class names)
  beyond the `Class<*>` → `KClass<*>` key type change explicitly required for KMP
  portability, unless the user explicitly approves a broader API change.
- Do **not** silently drop `internal` visibility modifiers when moving files between
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
