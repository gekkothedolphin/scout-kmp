# 📰 Origin Story & Design Philosophy (Habr Article Digest)

> Source: ["Мы опенсорснули Scout — DI-фреймворк, который написали для приложения Яндекс
> Маркет"](https://habr.com/ru/companies/yandex/articles/764920/), by Александр
> Миронычев (Yandex Market Android infra team), published on Habr. This is a condensed,
> agent-oriented digest of the original design rationale — **read this before changing
> any core API or key-encoding strategy**, because several design choices look
> "obviously improvable" but were already tried and reverted by the original authors
> for concrete, measured reasons.

## Why Scout exists (origin)

- Yandex Market's Android app (~9 years old codebase, ~350 Gradle modules, ~50
  concurrent developers at time of writing) used **Dagger + Kapt**, then Kotlin adoption
  made annotation-processing-based codegen (`kaptGenerateStubsKotlin`) the dominant cost
  in build time (~70% of generated code came from Dagger).
- Two prior DI ecosystems were explicitly evaluated and rejected:
  - **Dagger**: excellent, but its compile-time graph validation requires expensive
    codegen/kapt, and its "everything must be statically derivable" model becomes a
    maintenance burden once a codebase splits into hundreds of modules (subcomponent
    wiring cost scales badly).
  - **Koin**: cheap to maintain (no codegen), but no compile-time or systematic runtime
    graph validation, and its string-key + qualifier-based architecture is inherently
    slower and less safe at scale.
- Scout's stated design goal: **lightweight + safe + fast**, with graph validation
  happening in **unit tests** instead of compile time (no annotation processing), a
  concise explicit API, and module-splitting that doesn't complicate the graph.
- Scale it battle-tested at: **~6000 dependency graph nodes** across ~350 Gradle
  modules in the Yandex Market app, with the public API essentially frozen for ~1.5
  years before open-sourcing.

## Key API design decisions (and their rationale)

| Decision | Rationale | Relevance to this fork |
|---|---|---|
| `singleton` / `reusable` / `factory` naming | `single` in Koin was confusing (reads like RxJava's `Single`); `@Reusable` borrowed from Dagger to allow soft-reference caching without forcing full singleton lifetime. | Matches `InstanceFactory` strategies already found in `core` — do not rename these concepts casually, they are a deliberate API decision, not an accident. |
| **Explicit generic type on every factory** (`factory<MyController> { ... }`) | Full-text search (`<MyController>`) must reliably find every factory for a type, because IDE "Find Usages" was too slow at Yandex Market's scale. Implicit type inference was intentionally blocked at the API level. | If any KMP refactor introduces syntax sugar with inferred types, it silently breaks this searchability guarantee — avoid it. |
| **No qualifiers** | Qualifiers become a de-facto public interface of a module and complicate the graph in a multi-module codebase. Replaced by wrapper classes (`class IoScheduler(val instance: Scheduler)`) that give each variant its own concrete type. | Do not reintroduce Dagger/Koin-style qualifiers when refactoring `class-keys`/`compiled-keys` — the wrapper-class pattern is the intended idiom. |
| **Explicit multibinding factories** (`element<T>`, `mapping<K,V>`) instead of Koin's implicit `getAll` | Implicit collection/map assembly from same-typed factories breaks the "explicit type per factory line" searchability guarantee above. | `collect`/`associate` APIs in `Component`/`Accessor` map directly to this decision — preserve the distinction between `element`/`mapping` and plain `factory`. |
| **`allowOverride` flag required to redefine a factory** | Without it, silent factory overrides caused real production bugs (duplicate `singleton`/conflicting strategies merged silently from parallel branches). | Any KMP-era Registry/ScopeBuilder refactor must keep override-safety as a hard invariant, not an opt-in nicety. |
| **Lazy dependencies are pervasive by default** | App startup time regression was traced to eager object graph construction; solved by defaulting to `Lazy<T>` wrapped dependencies. | — |
| **No "smart" `get<T>()` overload resolution** | An early version used `typeOf<T>()` to make a single `get()` auto-detect `Lazy<T>`/`Provider<T>`/`List<T>`/`Map<K,V>`/nullable — `typeOf<T>()` turned out to be **prohibitively slow** and caused a major startup performance regression. This forced a full graph refactor and a long stagnation period. | **Directly explains why `Component`/`Accessor` today expose separate explicit methods** — `get`, `opt`, `getLazy`, `optLazy`, `getProvider`, `optProvider`, `collect`, `collectLazy`, `collectProvider`, `associate`, `associateLazy`, `associateProvider`. **Never reintroduce a generic "smart get" using `typeOf<T>()`/reflection-based overload resolution** — this is a proven anti-pattern for this codebase, in KMP `commonMain` or otherwise. |
| **Immutable graph, built via `scope("name") { ... }` builder** | Immutability is required to make graph validation meaningful at all (mirrors why Dagger can validate at compile time). `Registry` exists specifically because factories are registered before the `Scope` exists. | `ScopeBuilder`/`Registry`/`Scope` split in `core` is intentional layering — preserve it. |
| **`Component` as the only read path into a `Scope`** | Enables the graph validator to work by reflecting over `Component` subclass *method declarations* (not call sites, which are effectively uncollectable). Direct access to scope internals is deliberately forbidden. | This is why `Accessor`/`Interceptor`/`DirectAccessor`/`TrickyAccessor` exist as a controlled-access layer — do not add a public "escape hatch" bypassing `Component`. |
| **Validation via reflection over `Component` subclasses**, not compile-time | No annotation processing = no compile-time graph proof. Validator instead: (1) finds all `Component` subclasses via **ClassGraph** (classpath scanning, tuned with package/jar filters for speed), (2) reflectively instantiates them, (3) calls every method and forces evaluation of any `Lazy`/`Provider` results. | This is exactly what `scout/validator` + `tools/classgraph-collector` implement — confirms these modules are inherently JVM/classpath-reflection-based and **cannot be ported to commonMain** (see `phase_4_tooling_jvm.md`). |
| **`FactoryEnhancer`-style composition instead of runtime flags/branches** | To avoid paying "if enabled" branch costs on every dependency resolution at Yandex Market's scale (~6000 nodes), optional behavior (e.g. logging) is added via composition/wrapping at factory-creation time, not conditional checks in the hot path. | Relevant if optimizing `InstanceFactory`/`Interceptor` chains during the KMP port — prefer composition-based opt-in features over hot-path branching. |

## The `KClass` → `Int` key evolution — critical context for the KMP portThis is the single most important historical fact for `phase_2_refactoring_shared.md`
and `phase_4_tooling_jvm.md`:

1. Scout's **first** key implementation used **`Class<*>`/`KClass<*>`** as the
   dependency key (this is today's `class-keys` source set).
2. At Yandex Market's scale, this caused **measured performance problems**:
   - `KClass` instances are **not cached** by Kotlin (`String::class.java == String::class.java`
     is `true`, but `String::class === String::class` is `false`) — every key
     construction/comparison paid extra cost.
   - Using `KClass` as a map key requires computing its hash code repeatedly.
   - **Critically: touching a `KClass`/`Class<*>` forces JVM class loading** of that
     type — an expensive, easy-to-miss cost when done for every dependency resolution
     across thousands of graph nodes.
3. The fix was **`compiled-keys`**: the `scout-gradle-plugin` bytecode-rewrites
   (`bcel`) compiled classes to replace key construction with plain `Int` constants
   computed at build time, eliminating class loading, hashing, and instance-identity
   overhead entirely. This is documented as the harder-won, more mature variant, not an
   experimental afterthought.

**Implication for this KMP migration:** Phase 2 (`phase_2_refactoring_shared.md`)
currently plans to port `class-keys` to `commonMain` using `KClass<*>`, because
`compiled-keys` is JVM-bytecode-specific and cannot be ported as-is. **This is the
right call for portability**, but it deliberately reintroduces the exact performance
characteristics (`KClass` instance non-caching, class-loading cost) that the original
authors spent significant effort eliminating for JVM. Any performance-sensitive
production use of the multiplatform `core` on JVM/Android should be aware it is
reverting to the *pre-optimization* Scout behavior unless/until Phase 4 decides to keep
`compiled-keys` alive as a JVM-only opt-in layer on top of the multiplatform `jvm`
target. **Do not silently claim performance parity with the original `compiled-keys`
variant** — call this trade-off out explicitly in any user-facing documentation.

## KMP validation strategy — why the KSP-based approach fits, not fights, the original philosophy

The migration replaces ClassGraph classpath scanning + `java.lang.reflect.Method`
invocation (JVM-only, cannot run on Kotlin/Native) with a **new KSP-based component
collector**, wired only into test compilations. This is worth calling out explicitly:
it is **not a compromise forced by KMP**, it is arguably a *more faithful*
implementation of Scout's founding principle than the current JVM tooling.

Recall from the "Algorithm validation" section above: the original authors explicitly
rejected **any** annotation-processing/codegen in production code specifically to avoid
repeating Dagger+Kapt's build-time cost, and instead pushed all graph verification into
**unit tests**. The JVM implementation happens to use runtime reflection to achieve
this (because that was the pragmatic JVM-native tool available), but the actual
invariant the authors cared about was **"no codegen in `main`, verification lives in
`test`"** — not "verification must use reflection". A KSP processor wired exclusively
into `kspJvmTest`/`kspIosX64Test`/etc. (never into `kspJvm`/`kspIosX64` main
configurations) satisfies the exact same invariant, just with compile-time-generated
direct calls instead of runtime-reflected ones — which is actually a *strictly better*
mechanism on platforms where reflection doesn't fully exist (Kotlin/Native) or is
discouraged for performance (this port already avoids `typeOf<T>()`-style reflection
for the reasons above).

**Guardrails to preserve this invariant during implementation:**
- The KSP-generated invocation code must be tied to test source sets (`iosTest`,
  `jvmTest`, `commonTest`-consuming test compilations) only, never to
  `main`/`commonMain` compilations — verify Gradle configuration names literally start
  with `ksp` and end with `Test` (e.g. `kspJvmTest`) for every wiring added.
- Do not let "it would be more convenient to also generate this in main" creep in
  later — that is precisely the kapt-style regression the original team escaped from.
- KSP tooling itself (KotlinPoet, the processor module) is a **build-time-only**
  dependency, never shipped in the published `core` artifact — same packaging
  discipline as the existing `scout-gradle-plugin`, which is also never a runtime
  dependency of consumers.


- The original team benchmarked Scout against Koin, Kodein, Katana, and Dagger,
  acknowledging benchmarks measure isolated code paths, not real-world conditions, and
  results are hardware/JVM/OS dependent (`measures/jvm-benchmarks` in this repo is the
  living version of that suite).
- General expectation set by the authors: **Dagger's compile-time codegen approach is
  fundamentally faster** than any manual/runtime DI framework's dependency resolution;
  Scout's goal was never to beat Dagger's raw speed, only to get close while being much
  cheaper to build and maintain, and demonstrably faster/safer than Koin.
- Any KMP-era benchmarking work (Phase 4/5) should preserve this framing rather than
  imply the ported library is faster than the original JVM-optimized one.
