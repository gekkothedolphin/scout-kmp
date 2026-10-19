# 🧭 AI Agent Knowledge Base — Scout KMP Fork

This directory is the single source of truth for any AI agent (Copilot CLI, Claude,
GPT, etc.) working on this repository. Before doing **any** task, the agent must read
`.ai/README.md` and the relevant phase file from `migration_plan/`.

All project artifacts (code, comments, commit messages, docs, and files under `.ai/`)
must be written in **English**. Conversations with the human operator may happen in
any language, but everything committed to the repository stays in English.

## 🤖 Agent role

You act as a **Senior Kotlin Multiplatform architect** leading the migration of a fork
of the Yandex Scout library (a manual dependency injection framework for JVM/Android,
Apache 2.0 licensed) into a full Kotlin Multiplatform library. The fork is maintained by
**gekkothedolphin** (see root `README.md` License section and `AUTHORS`).

Read `design_philosophy.md` before changing any core API, key-encoding strategy, or
`get`/`collect`/`associate` method family — it documents design decisions the original
authors already tried, measured, and in some cases reverted (e.g. qualifiers, "smart
get" via `typeOf<T>()`, string keys). Repeating a reverted experiment wastes effort and
can reintroduce known production bugs.

Responsibilities:
- Never invent APIs, dependency versions, or file structure — always verify the actual
  repository state with tools (`grep`, `view`, `bash`) before making changes.
- Work in **atomic phases** (see `migration_plan/`) — do not skip ahead.
- After any meaningful change, update the status in this file and the checklists in the
  corresponding `phase_*.md`.
- Never remove or rewrite original copyright headers (`Copyright 2023 Yandex LLC`) —
  only append to them (`Copyright 2026 gekkothedolphin@gmail.com`), as already done in
  the root `README.md`.

## 📍 Current status

**Phase: `phase_1_analysis.md` — Analysis is now COMPLETE (all checklist items resolved,
including the benchmark baseline). Phase 2 (`phase_2_refactoring_shared.md`) is next,
starting with Step 2.0 (KSP collector feasibility spike); the plan was revised on
2026-10-19, see "Plan review 2026-10-19" below.
Key decisions below are CONFIRMED by the user — do not re-litigate them without
explicit new instruction.**

### ✅ Confirmed migration decisions

| Decision | Choice | Rationale |
|---|---|---|
| **First-iteration target platforms** | **JVM/Android + iOS** (JS/Wasm explicitly deferred to a later, separate phase) | iOS has a usable `WeakReference` equivalent for `ReusableInstanceFactory`; JS/Wasm has none and would also force premature decisions on the validator's classpath-reflection model. Starting with 3 target families at once was rejected as too high blast-radius for an unstable core. |
| **Physical code location** | All new KMP code lives in a **new, separate top-level `scout-kmp/` directory** — a standalone Gradle multiplatform project (own `settings.gradle.kts`, own Gradle wrapper), not a Kotlin package inside `scout/`. The original `scout/` directory is **never modified** for the rest of this migration — it is kept byte-for-byte as the JVM-only reference baseline. New modules (`core`, later `inspector`/`validator`/`androidApp`/`iosApp`/the KSP collector) are added to `scout-kmp/` incrementally, one per phase, by **copying and adapting** code from the corresponding `scout/` module — never by `git mv`/in-place edits. | Keeps `scout/` as a trustworthy, untouched baseline for benchmark comparisons (Phase 1 baseline vs Phase 4 comparison) and licensing/attribution clarity, makes rollback trivial (delete `scout-kmp/`), and allows incremental, side-by-side development without ever leaving the original library in a broken intermediate state. Confirmed 2026-08-02; supersedes any earlier phase wording that talked about "moving"/"renaming in place" `scout/scout/core` — that language now means "copy into `scout-kmp/core`, adapt, leave the `scout/` original untouched". |
| **Package namespace** | Rename Kotlin package `scout.*` → **`dev.gekko.scout.*`** now, before Phase 2 starts | Kotlin package namespace and Maven group are independent axes; the fork needed its own namespace before commonMain work begins to avoid a second disruptive rename later. |
| **Maven group for `scout-kmp/`** | **`io.github.gekkothedolphin`** (confirmed 2026-10-19; supersedes `dev.gekko.scout` from 2026-08-02). The Kotlin package stays **`dev.gekko.scout.*`** — the two axes intentionally differ | Maven Central only lets a group be published after namespace verification: `dev.gekko.scout` would require owning the `gekko.dev` domain, while `io.github.gekkothedolphin` is verified through the GitHub account. Maven Central does not check Kotlin package names, so the package keeps the shorter `dev.gekko.scout`. Artifact ids carry the `scout-` prefix (e.g. `io.github.gekkothedolphin:scout-core`). The new group also avoids any appearance of publishing under Yandex's original `com.yandex.scout` coordinates without their involvement. The original `scout/` project's Maven group (`com.yandex.scout`, in `scout/build.gradle`) is never changed — it is frozen along with the rest of `scout/`. |
| **Kotlin version** | Bump from `1.8.22` to the current stable Kotlin release before Phase 2 | 1.8.22 predates modern KMP tooling maturity (Xcode/Kotlin Native target support, commonizer improvements); upgrading first avoids doing the KMP conversion twice. |
| **`compiled-keys` fate on JVM/Android** | **Still deferred to Phase 4** — decision remains data-driven from the benchmark comparison, NOT changed by the iOS decision below. **Architecture pre-work now done in Phase 2**: the `DiKey`/`diKeyOf` `expect`/`actual` seam is built ahead of time so JVM/Android can later swap to an `Int`-backed actual without touching any `commonMain` call site; Phase 4 only decides *which* actual JVM/Android uses, not whether the abstraction exists | Porting `class-keys` to `commonMain` already requires `KClass<*>` instead of `Class<*>`, which independently breaks the `scout-gradle-plugin`'s bcel bytecode pattern matching (hardcoded signature `"(Ljava/lang/Class;)I"`, see `design_philosophy.md`). Capture a **benchmark baseline on the current, unmodified JVM-only code** (see below) before any core changes, then re-run the same benchmarks after Phase 2 to measure the actual regression and decide with real data. A 2026-07-26 standalone micro-benchmark (`.ai/experiments/kclass-benchmark/`) already shows ~9.2x `KClass`-vs-`Int` `HashMap` lookup overhead on JVM — real, but the full-graph benchmark comparison is still the basis for the final decision. |
| **Key strategy on iOS** | **`KClass<*>`-based `class-keys` only — no numeric-key equivalent on iOS at all** — now empirically confirmed, not just architecturally justified | `compiled-keys` is JVM bytecode/classfile specific (bcel operates on `.class` files); Kotlin/Native has no equivalent bytecode format, so a numeric-key variant is not portable there in any case. Note for documentation accuracy: the original JVM `compiled-keys` motivation (non-cached `KClass` instances + forced JVM class loading, see `design_philosophy.md`) is a **JVM ClassLoader-specific problem that does not exist on Kotlin/Native at all** — this is not "LLVM/static linking negating the JVM problem", it's that the problem's precondition (a JVM classloader) is absent on Native. A 2026-07-26 micro-benchmark (`.ai/experiments/kclass-benchmark/RESULTS.md`) confirms this empirically: `KClass` identity (`===`) is stable on iOS Simulator (unlike JVM, where it's unstable), and `KClass`-keyed lookup overhead vs `Int`-keyed is only ~1.11x in a release build — negligible. This decision is scoped to iOS only and does not change the JVM/Android `compiled-keys` deferral above. |
| **Graph validation strategy (replaces JVM-only ClassGraph/reflection for non-JVM targets)** | Build a **new KSP-based component collector**, generating direct (non-reflective) Kotlin invocation code, wired **only into test compilations** (`kspJvmTest`, `kspIosX64Test`, `kspIosArm64Test`, `kspIosSimulatorArm64Test` — see correction below), never into `commonMain`/production compilations | `ClassGraph` (classpath scanning) and `java.lang.reflect.Method` (used by `scout/validator`'s `MethodInvoker`) have no Kotlin/Native equivalent — they cannot run on iOS at all. KSP is the standard multiplatform-safe compile-time introspection mechanism. Keeping codegen scoped to test-only compilations matches Scout's founding principle (see `design_philosophy.md`: "validation happens in unit tests, not via annotation-processing in main code" — this is literally why Scout was created instead of using Dagger+Kapt). **Existing JVM `scout/validator` + `tools/classgraph-collector` are NOT being replaced** — they stay untouched as the proven JVM/Android validation path; the KSP-based collector is a new, additional module built specifically to unblock iOS/Native validation. |
| **Gradle KSP wiring correction** | There is **no single `kspCommonTest` configuration** in KSP's multiplatform support | KSP configures one Gradle configuration **per target's test compilation**: `kspJvmTest`, `kspIosX64Test`, `kspIosArm64Test`, `kspIosSimulatorArm64Test`, etc. (verified against official KSP multiplatform docs). The processor will run once per target — plan for this when estimating CI time, and never write `kspCommonTest` literally in build scripts, it does not exist. |
| **KSP processor implementation approach** | Build a narrow, purpose-specific KSP processor using **KotlinPoet** for code generation; do **not** take a runtime dependency on `kotlin-inject`/Metro | No existing library does "collect `Component` subclasses + generate direct invocation calls for validation" out of the box — this is bespoke to Scout's validation algorithm. `kotlin-inject` (github `evant/kotlin-inject`) and `Metro` (github `zacsweers/metro`) are proven, Apache-2.0-licensed, KSP-based multiplatform DI processors — use their **architecture as a reference** (multiplatform per-target KSP wiring, contribution/collection merging patterns) without depending on them at runtime, to avoid coupling Scout to a second DI framework. |

### ✅ Phase 1 complete — benchmark baseline captured (2026-08-02)

`scout/measures/jvm-benchmarks` was run (full suite, all 10 scenarios, 68 benchmark
methods) against the original, unmodified `scout/` codebase. Results archived at
`.ai/benchmarks/baseline/` (`control.csv` raw values, `compare.txt` formatted table,
`README.md` with environment metadata and headline numbers). This is the reference
point for Phase 4's benchmark comparison and `compiled-keys` fate decision — Phase 1 is
now fully checked off, Phase 2 can start.

### 🔍 Plan review 2026-10-19

The plan was checked against the actual `scout/` sources. Changes made:

- **Missed JVM API:** `kotlin.synchronized` / `@Volatile` in `SingleInstanceFactory`,
  `SynchronizedScopeBuilder`, `LateInitScope` and `Inject.kt` were not in the Phase 1
  inventory. Added to `context_project.md` and Phase 2 Step 2.2.
- **`ThreadConfinementCheck`:** atomicfu does not cover `ThreadLocal` or thread names;
  the iOS actual needs its own thread identity (Phase 2 Step 2.4).
- **`reusable` on iOS:** a weak reference clears at the next GC, so the iOS behaviour
  became an explicit user decision (open question 3).
- **Phase 2 split into Steps 2.0–2.5**, each ending with a green build, plus a minimal
  `scout-kmp` CI job in Step 2.1 instead of waiting for Phase 5.
- **KSP collector risk moved forward:** KSP on a test compilation cannot simply
  enumerate classes from `main` or other modules. A throwaway feasibility spike now runs
  first (Phase 2 Step 2.0); Phase 4 Part B depends on its result.
- **Ordering fix:** the iOS KSP validation test moved from Phase 3 to Phase 4 Part B
  (the collector only exists in Phase 4).
- **Swift interop:** `inline reified` APIs are not exported to Swift, so the iOS
  consumption model is "graph built in Kotlin, narrow non-generic facade for Swift"
  (Phase 3).
- **Android benchmarks:** the `compiled-keys` decision for Android needs at least one
  ART measurement, not only the HotSpot baseline (Phase 4 Part A).
- **Gradle plugin compatibility** (KMP task names, configuration cache, current AGP)
  added as an explicit check for option (a) in Phase 4.
- **Stale wording fixed:** composite-build wording in Phase 4, "still-open Maven group
  question" in Phase 5.

### ❓ Open questions (need a user decision)

1. ~~**Maven Central namespace.**~~ Resolved 2026-10-19: Maven group
   `io.github.gekkothedolphin`, Kotlin package stays `dev.gekko.scout` (see decisions
   table).
2. **License-header policy for `scout-kmp/`.** `scripts/check-license-headers.sh`
   requires the Yandex header in every `*.kt` in the repo; brand-new fork files fail
   it. Decide the header rule for brand-new files (Phase 2 Step 2.1).
3. **`reusable` on iOS:** weak reference (best-effort, usually recreated), strong cache
   cleared on memory warning, or strong reference (≈ `singleton`). Needed before
   Phase 2 Step 2.4.

## 📜 Interaction rules

1. **One phase file = one atomic stage.** Do not mix tasks from different phases in a
   single PR/commit.
2. **Checklists are the source of truth.** Only mark `[x]` after real verification
   (build/tests passing), never based on assumption.
3. **Always read the "📍 Starting state" section of a phase before starting work** — it
   lists the files you must open first.
4. **The "⚠️ Anti-patterns" section in every phase is mandatory** — these are not
   decorative; violating them has previously broken the build or created licensing risk.
5. **Do not delete existing JVM-only modules** (`inspector`, `validator`,
   `tools/classgraph-collector`, `tools/scout-gradle-plugin`) — they remain JVM-specific
   by architectural decision (see `context_project.md`).
6. **Never edit files under `scout/` (the original repo root subdirectory).** It is the
   frozen JVM-only reference baseline for the rest of this migration. All new/ported
   code goes into the separate `scout-kmp/` directory (own standalone Gradle project),
   built up module-by-module as each phase reaches it — see "Physical code location" in
   the decisions table above.
7. **All repository content must be in English** — code, comments, KDoc, commit
   messages, and everything under `.ai/`.
8. When anything is ambiguous — **stop and ask the user**, don't guess.

## 📖 Project glossary

| Term | Meaning |
|---|---|
| **Scope** | Container holding dependency `Definition`s and created instances. |
| **Component** | The only entry point to read from a `Scope` from outside (`get()`, `getLazy()`, `getProvider()`). |
| **Definition / Registry** | Internal description of a dependency factory and the registry of all definitions in a scope. |
| **Accessor / Interceptor** | Controlled access mechanism to a `Scope` (`DirectAccessor`, `TrickyAccessor`, `InterceptorRegistry`). |
| **InstanceFactory** | Instance creation strategy: `Single` (singleton), `Reusable` (SoftReference-backed cache), `Factory` (new instance every time). |
| **class-keys** | Dependency key variant based on `Class<*>` (JVM reflection). Simpler, a candidate for porting to `commonMain` via `KClass<*>`. |
| **compiled-keys** | Dependency key variant based on `Int`, substituted by the `scout-gradle-plugin` bytecode plugin (bcel). Entirely JVM bytecode specific, **not portable** to KMP as-is. |
| **inspector** | Module for inspecting `Scope` content (used by the validator). JVM-only, uses reflection. |
| **validator** | Module for validating the dependency graph (consistency/overrides/naming checks). JVM-only. |
| **shared module** | Target KMP module (`commonMain`/`jvmMain`/...), physically located at **`scout-kmp/core`** (a new, standalone module — not a modification of `scout/scout/core`). Populated by copying+adapting code from `scout/scout/core` in Phase 2. |
| **androidApp / iosApp / composeApp** | Platform consumer modules of the shared library, to be created under **`scout-kmp/`**. **Do not exist yet**, created in Phase 3 (`composeApp` deferred). |
| **`scout/` vs `scout-kmp/`** | `scout/` is the original, JVM-only codebase — **frozen, never edited** for the rest of this migration, used only as a read reference and as the source of the Phase 1/Phase 4 benchmark baseline. `scout-kmp/` is the new, standalone multiplatform Gradle project where all migration work actually happens, built up incrementally (one module copied+adapted per phase, starting with `core`). |
| **Qualifiers (Koin/Dagger concept)** | Deliberately **not supported** in Scout. Instances of the same type are disambiguated via dedicated wrapper classes (e.g. `IoScheduler(val instance: Scheduler)`), not string/annotation qualifiers. Do not reintroduce qualifiers during the KMP port. |
| **`element` / `mapping`** | Explicit multibinding factory kinds — `element<T>` contributes to a `List<T>` (read via `collect`), `mapping<K,V>` contributes to a `Map<K,V>` (read via `associate`). Intentionally explicit instead of Koin's implicit `getAll`. |
| **`allowOverride`** | Required flag to intentionally redefine an existing factory; silent overrides are treated as bugs by design (this caused real production incidents at Yandex Market before the flag existed). |
| **"Smart get" anti-pattern** | A historical, reverted design where a single `get<T>()` used `typeOf<T>()` to auto-detect `Lazy`/`Provider`/collection/nullable wrapping. Caused a severe startup performance regression at Yandex Market and was replaced by today's explicit `get`/`opt`/`getLazy`/`getProvider`/`collect`/`associate` method family. **Never reintroduce this pattern.** See `design_philosophy.md`. |
| **`KClass` non-caching problem** | Kotlin does not cache `KClass` instances (`X::class === X::class` is `false`), and touching a `KClass`/`Class<*>` forces JVM class loading. This was the original motivation for `compiled-keys`. Porting `class-keys` to `commonMain` via `KClass<*>` reintroduces this exact cost on JVM — see `design_philosophy.md` before assuming performance parity. **This problem is JVM-ClassLoader-specific and does not exist on Kotlin/Native at all** — the iOS target uses `KClass<*>`-based `class-keys` unconditionally, this is not a trade-off on iOS, just the only option. |
| **KSP-based validator (planned)** | A new, additional Kotlin Symbol Processing–based component collector, wired **only into test compilations** (`kspJvmTest`, `kspIosX64Test`, etc. — there is no single `kspCommonTest` configuration, KSP is per-target), generating direct non-reflective invocation code instead of using `ClassGraph`+`java.lang.reflect`. Built to unblock graph validation on iOS/Native, where classpath scanning and reflection don't exist. **Does not replace** the existing JVM `scout/validator`/`tools/classgraph-collector`, which remain the proven JVM/Android validation path. |

## 🗂️ Repository layout (top level)

```
scout/          # ORIGINAL codebase — frozen, read-only reference baseline. Never edit.
scout-kmp/      # NEW multiplatform project (own settings.gradle.kts/gradlew), built up
                # incrementally: core (Phase 2) → androidApp/iosApp (Phase 3) →
                # inspector/validator/ksp-collector (Phase 4) → ...
.ai/            # this knowledge base
```

## 🗂️ `.ai/` structure

```
.ai/
├── README.md                          # this file — knowledge map
├── context_project.md                 # tech stack, architecture, code style
├── design_philosophy.md               # origin story & design rationale (Habr article digest)
├── SESSION_HANDOFF.md                 # pointer to the original Copilot CLI session
├── benchmarks/
│   └── baseline/                      # Phase 1 JVM benchmark baseline (unmodified scout/)
├── experiments/
│   └── kclass-benchmark/              # standalone KClass-vs-Int key lookup micro-benchmark
└── migration_plan/
    ├── phase_1_analysis.md            # package/dependency/license analysis
    ├── phase_2_refactoring_shared.md  # move core into shared module (commonMain)
    ├── phase_3_platforms.md           # androidApp/iosApp/composeApp
    ├── phase_4_tooling_jvm.md         # adapt inspector/validator/gradle-plugin
    └── phase_5_ci_release.md          # CI, multiplatform artifact publishing
```
