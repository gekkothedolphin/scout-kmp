# KClass identity/lookup benchmark — results

Standalone KMP project (isolated from `scout/`, not part of the production
build) created to empirically test the claim "LLVM AOT compilation makes
`HashMap<KClass<*>, V>` lookups natively fast on iOS". See `build.gradle.kts`
and `src/` in this directory for the full benchmark source
(`KClassBenchmark.kt`). Run via:

```
./gradlew jvmRunBenchmark                              # JVM
./gradlew linkReleaseExecutableIosSimulatorArm64        # then run via simctl spawn on a booted simulator
```

## Method

- 40 distinct marker classes (`Key00`..`Key39`) used as synthetic DI keys.
- Measures (all warmed up, 500k measured iterations):
  1. `KClass` identity (`===`) and `hashCode()` stability across repeated `::class` access.
  2. Cost of repeatedly deriving `::class` from an instance.
  3. Cost of `HashMap<KClass<*>, Int>.get()` vs baseline `HashMap<Int, Int>.get()`.

## Results

| Metric | JVM (JDK 21) | iOS Simulator arm64, **debug** | iOS Simulator arm64, **release** |
|---|---|---|---|
| `KClass` identity stable (`===`) | **false** | true | true |
| `KClass.hashCode()` stable | true | true | true |
| 500k `::class` derivations | 68.9 ms | 65.1 ms | 12.7 ms |
| 500k `HashMap<KClass,V>` gets | 8.0 ms | 40.5 ms | 4.0 ms |
| 500k `HashMap<Int,V>` gets | 0.87 ms | 40.7 ms | 3.6 ms |
| **KClass overhead vs Int-keyed** | **9.2x slower** | 0.99x (misleading, see below) | **1.11x** |

## Interpretation

1. **The JVM problem is real and confirmed empirically, not just anecdotal**:
   `KClass` identity is *not* stable across calls on JVM (matches the
   article's `String::class === String::class` claim), and `HashMap<KClass,V>`
   is measurably ~9x slower than an equivalent `Int`-keyed map. This is the
   concrete justification for why `compiled-keys` exists on JVM/Android and
   remains a legitimate optimization to preserve there.

2. **On iOS/Native, `KClass` identity IS stable**, unlike JVM. This directly
   contradicts the flawed "LLVM defeats ClassLoader" reasoning from the
   original proposal (Kotlin/Native has no ClassLoader to defeat — the real
   reason is that Kotlin/Native's runtime evidently caches/interns `KClass`
   metadata references, unlike the JVM).

3. **Debug vs release native binaries differ by ~3-10x** — always benchmark
   Kotlin/Native with `release` binaries; debug numbers are not representative
   of production behavior (the `HashMap<Int,V>` baseline itself was ~11x
   slower in debug vs release, showing the noise is in the runtime/codegen,
   not specific to `KClass`).

4. **In the release build, `KClass`-keyed lookup overhead vs `Int`-keyed is
   only ~1.11x** — i.e. **negligible** on iOS/Native. This empirically
   supports the decision to use plain `KClass`-based keys on iOS without a
   compiled/numeric-key equivalent: unlike on JVM, there is no meaningful
   performance to buy back with such a mechanism on this platform, and
   building one would fight Kotlin/Native's lack of bytecode/ClassLoader
   concepts for no measurable benefit.

## Caveats / what this does NOT prove

- This measures raw map-lookup micro-costs only, not full Scout graph-init/
  resolution costs (see `measures/jvm-benchmarks` for the real workload shape) —
  treat this as directional evidence, not a substitute for the Phase 1/Phase 4
  benchmark baseline captured from the actual Scout benchmark suite.
- Single device/OS/Xcode version (Xcode 26.6, iPhone 15 Pro simulator, arm64
  host). Not tested on a physical device or on x86_64 simulator/Rosetta.
- Does not benchmark the *write* path (registration), only lookups.
