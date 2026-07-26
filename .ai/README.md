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

**Phase: `phase_1_analysis.md` — Analysis (in progress, KMP infrastructure not yet created).**

Facts about the current repository state as of writing this knowledge base:
- Build system is pure **Groovy** Gradle (`build.gradle`, `settings.gradle`); there are
  no `.kts` files in the project.
- Kotlin `1.8.22`, JVM-only (`org.jetbrains.kotlin.jvm`), Java toolchain `8`.
- The KMP plugin (`kotlin("multiplatform")`) is **not applied anywhere**.
- `androidApp/`, `iosApp/`, `composeApp/` directories **do not exist yet** — they will
  be created in Phase 3, based on the existing `scout/samples/coffee-maker`.
- Maven group for all modules is `com.yandex.scout`, but Kotlin source packages are
  `scout.*` (NOT `com.yandex.scout.*`). This distinction matters for Phase 1 — do not
  conflate the Kotlin package namespace with Maven coordinates.
- The base module to move into `commonMain` is **`scout/scout/core`** (see dependency
  analysis in `context_project.md`).

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
6. **All repository content must be in English** — code, comments, KDoc, commit
   messages, and everything under `.ai/`.
7. When anything is ambiguous — **stop and ask the user**, don't guess.

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
| **shared module** | Target KMP module (`commonMain`/`jvmMain`/...) that `scout/core` will be moved into. Created in Phase 2. |
| **androidApp / iosApp / composeApp** | Platform consumer modules of the shared library. **Do not exist yet**, created in Phase 3. |

## 🗂️ `.ai/` structure

```
.ai/
├── README.md                          # this file — knowledge map
├── context_project.md                 # tech stack, architecture, code style
└── migration_plan/
    ├── phase_1_analysis.md            # package/dependency/license analysis
    ├── phase_2_refactoring_shared.md  # move core into shared module (commonMain)
    ├── phase_3_platforms.md           # androidApp/iosApp/composeApp
    ├── phase_4_tooling_jvm.md         # adapt inspector/validator/gradle-plugin
    └── phase_5_ci_release.md          # CI, multiplatform artifact publishing
```
