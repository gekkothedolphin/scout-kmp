# 🧱 Project Technical Context

> Data source: live repository inspection (`build.gradle`, `settings.gradle`,
> `versions.gradle`, `modules.gradle`, sources under `scout/scout/*`). Keep this file
> updated whenever the stack changes — do not let it go stale.

## Build system

- **Build tool:** Gradle, scripts written in **Groovy** (`build.gradle`,
  `settings.gradle`). There are no `.gradle.kts` files in the project — migrating to
  Kotlin DSL alongside KMP adoption is a reasonable idea but is a separate concern, not
  a blocker for this migration.
- **Root `settings.gradle`** (`scout/settings.gradle`) includes the modules:
  - `:scout:core`, `:scout:inspector`, `:scout:validator`
  - `:tools:classgraph-collector`
  - `:measures:jvm-benchmarks`
  - `:samples:coffee-maker`
  - composite builds: `tools/scout-gradle-plugin`, `measures/graph-generator-plugin`
- **Versions** (`versions.gradle`):
  - `kotlin_version = 1.8.22`
  - `java_version = 8` (JVM toolchain)
  - `gradle_version = 8.2.1`
  - `dokka_version = 1.8.10`
  - `scout_version = 0.9.3`
- **Plugins:** `java-library`, `org.jetbrains.kotlin.jvm` everywhere — all modules are
  JVM-targeted, **the KMP plugin (`org.jetbrains.kotlin.multiplatform`) is not used
  anywhere**.
- **Group/coordinates:** `com.yandex.scout` (Maven), Kotlin package namespace —
  `scout.*` (do not confuse changing the Maven group with changing the package
  namespace — they are independent decisions to be addressed separately in Phase 1).

## Module architecture

```
scout/
├── scout/core        ← BASE module. Pure DI logic. Primary candidate for commonMain.
├── scout/inspector    ← depends on core. JVM reflection. Stays JVM-only.
├── scout/validator    ← depends on core+inspector. JVM reflection. Stays JVM-only.
├── tools/classgraph-collector   ← depends on core+validator, ClassGraph lib. JVM-only.
├── tools/scout-gradle-plugin    ← bytecode plugin (bcel), generates compiled-keys. JVM-only build tool.
├── measures/*         ← benchmarks and graph generator, JVM-only.
└── samples/coffee-maker ← library usage sample (future prototype for composeApp/androidApp).
```

### `scout/core` — detailed breakdown (base for commonMain)

Key types: `Component`, `Scope`, `Injector`, `Provider`, `Definition`/`Registry`,
`InstanceFactory` (+ `Single`/`Reusable`/`Factory` strategies), `Accessor`/`Interceptor`
(`DirectAccessor`, `TrickyAccessor`, `InterceptorRegistry`), `ScopeBuilder` (+
`ConfinedScopeBuilder`, `SynchronizedScopeBuilder`, `UnsafeScopeBuilder`), error handling
(`ScoutError*`, `exception/*`).

Two alternative dependency-key source sets:
- **`src/class-keys`** — `ObjectKey/CollectionKey/AssociationKey = Class<*>` (plain JVM
  reflection via `T::class.java`). **Portable variant** — porting only requires
  replacing `Class<*>` with `KClass<*>`.
- **`src/compiled-keys`** — `ObjectKey = Int`, values substituted by the
  `scout-gradle-plugin` bytecode plugin at compile time (bcel bytecode rewriting)
  **Not portable** to KMP as-is — it is JVM classfile specific.

Known JVM dependencies in `core` requiring `expect`/`actual` when porting:
| File | JVM API | Replacement plan |
|---|---|---|
| `utils/ThreadConfinementCheck.kt` | `java.util.concurrent.atomic.AtomicReference`, `ThreadLocal` | `kotlinx-atomicfu` + per-platform expect/actual |
| `factory/strategy/ReusableInstanceFactory.kt` | `java.lang.ref.SoftReference` | `expect class SoftRef` (JVM actual = `SoftReference`, native/JS actual = strong reference or platform equivalent) |
| `scope/utils/ParentLookupUtils.kt` | `java.util.Collections.unmodifiableList` | trivially replaceable with standard Kotlin common API |

## Architectural patterns

- **DI:** a custom manual DI framework (not Koin/Dagger/Hilt) — Scope + Component with a
  declarative DSL (`scope("name") { singleton<T> {...} }`).
- **Navigation / UI framework:** none present — the project is a **library**, not an
  application. `androidApp`/`iosApp`/`composeApp` do not exist yet and will be created
  in Phase 3 as thin consumers of the shared module (Compose Multiplatform is the
  candidate UI layer, to be confirmed with the user before starting Phase 3).
- **Testing:** JUnit5 (`junit_api_library`/`junit_engine_library`) + AssertJ, run via
  `useJUnitPlatform()`. When ported to `commonTest`, migrate to `kotlin.test`.
- **Documentation:** Dokka (`dokka-gradle-plugin`) generates the javadoc jar for
  publishing.

## Code style and licensing

- Every Kotlin file starts with an Apache 2.0 license header, generated/checked by
  `scripts/license_header` — **do not remove or alter existing `Copyright 2023 Yandex
  LLC` headers**, only append the new line `Copyright 2026 gekkothedolphin@gmail.com`
  where this has already been done at the repository level (see root `README.md`).
- `internal` visibility is used consistently for implementation details
  (`ThreadConfinementCheck`, `BuiltinKeys` in the class-keys variant, etc.) — preserve
  this encapsulation level when porting to multiplatform source sets.
- Public API is documented with KDoc comments (see `ThreadConfinementCheck.kt`,
  `DefinitionKey.kt`) — preserve during refactoring.
- **Language:** all code, comments, KDoc, identifiers, commit messages, and `.ai/`
  content must be written in English, regardless of the language used to discuss the
  task with the human operator.
