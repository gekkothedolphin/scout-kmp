# Phase 2 — Shared Module Refactoring

## 🎯 Goal

Turn `scout/scout/core` into a genuine Kotlin Multiplatform module with a
`commonMain` source set containing all platform-agnostic DI logic, backed by
`jvmMain` (and, if approved in Phase 1, additional `nativeMain`/`jsMain`) actuals for
the handful of JVM-specific APIs identified during analysis. All downstream JVM-only
modules (`inspector`, `validator`, `classgraph-collector`) must keep compiling against
the new `jvm` target of `core` without any public API break.

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

- [ ] `scout/scout/core/build.gradle` applies `org.jetbrains.kotlin.multiplatform`
      instead of `org.jetbrains.kotlin.jvm`, with a `jvm()` target declared at minimum.
- [ ] `commonMain` contains: `Component`, `Scope`, `Injector`, `Provider`,
      `Definition`/`Registry`, `InstanceFactory` and its `Single`/`Factory` strategies,
      `Accessor`/`Interceptor` family, all `ScopeBuilder` variants, and all
      `error`/`exception` types — verified by `grep` that none of these files import
      `java.*`/`javax.*` after the move.
- [ ] `ObjectKey`/`CollectionKey`/`AssociationKey` typealiases use `KClass<*>` instead
      of `Class<*>`; every `T::class.java` call site in the moved code is replaced with
      `T::class`, and this is verified by grepping for `::class.java` inside
      `commonMain` (expected: zero matches).
- [ ] Read `.ai/design_philosophy.md`'s "`KClass` → `Int` key evolution" section before
      finalizing this item — porting to `KClass<*>` reintroduces the class-loading and
      instance-caching costs the original `compiled-keys` variant was built to
      eliminate. Document this trade-off explicitly in the module's public docs/KDoc
      rather than presenting `commonMain` `class-keys` as a drop-in performance
      equivalent.
- [ ] `ThreadConfinementCheck` is implemented as an `expect class` in `commonMain` with
      a `jvmMain actual` (using the strategy recorded in Phase 1 — `kotlinx-atomicfu` or
      equivalent).
- [ ] `ReusableInstanceFactory`'s `SoftReference` usage is abstracted behind an
      `expect`/`actual` type, with the `jvmMain actual` preserving current soft-reference
      semantics exactly (no behavior change for existing JVM consumers).
- [ ] `ParentLookupUtils` no longer uses `java.util.Collections.unmodifiableList`;
      replaced with a common Kotlin API with identical immutability guarantees.
- [ ] All package/namespace renames decided in Phase 1 (if any) are applied
      consistently across `commonMain` and `jvmMain`, with no leftover references to the
      old namespace.
- [ ] Tests are migrated to `commonTest` (or `jvmTest` where genuinely
      platform-specific) using `kotlin.test`, and pass on the `jvm` target.
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
- Do **not** reintroduce a generic "smart" `get<T>()` that inspects `typeOf<T>()` to
  auto-detect `Lazy`/`Provider`/collection/nullable wrapping, even if it looks like a
  nice ergonomic improvement for `commonMain`. This was tried by the original authors
  and caused a severe, hard-to-diagnose startup performance regression — see
  `.ai/design_philosophy.md`. Keep the explicit `get`/`opt`/`getLazy`/`getProvider`/
  `collect`/`associate` method family as-is.
- Do **not** proceed to Phase 3 while `inspector`/`validator` are broken or any test in
  `core` is failing.
