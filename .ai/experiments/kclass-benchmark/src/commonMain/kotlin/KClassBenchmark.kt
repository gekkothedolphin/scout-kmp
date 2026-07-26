import kotlin.time.Duration
import kotlin.time.measureTime

/**
 * Validates two claims from the "compiled-keys via expect/actual" proposal:
 *
 * 1. Identity/hashCode stability of `KClass` instances obtained via `::class`
 *    (the JVM-specific problem cited in the Habr article: `X::class` is not
 *    guaranteed to be `===`-stable across calls, forcing extra class-loading
 *    work every time a key is derived).
 * 2. Whether a `HashMap<KClass<*>, V>` lookup is "natively fast" on a given
 *    platform, relative to a raw `Int`-indexed baseline (`IntArray`/`HashMap<Int,V>`).
 *    This is the concrete, measurable stand-in for the vague claim "LLVM AOT
 *    compilation makes KClass-based lookups fast on iOS".
 *
 * Results must be interpreted per-platform (jvmMain vs iosSimulatorArm64Main
 * actuals both call into this same commonMain code) so the *relative*
 * overhead of KClass-keyed lookups vs Int-keyed lookups can be compared
 * directly between JVM and iOS Native, instead of relying on assumptions.
 */
object KClassBenchmark {

    private const val WARMUP_ITERATIONS = 50_000
    private const val MEASURED_ITERATIONS = 500_000

    data class Result(
        val platform: String,
        val identityStable: Boolean,
        val hashCodeStable: Boolean,
        val classAccessCost: Duration,
        val hashMapLookupCost: Duration,
        val intKeyedLookupCost: Duration,
    ) {
        val overheadRatio: Double
            get() = hashMapLookupCost.inWholeNanoseconds.toDouble() /
                intKeyedLookupCost.inWholeNanoseconds.coerceAtLeast(1)

        fun report(): String = buildString {
            appendLine("=== KClass benchmark on $platform ===")
            appendLine("KClass identity stable across calls (===): $identityStable")
            appendLine("KClass hashCode stable across calls:       $hashCodeStable")
            appendLine("Cost of $MEASURED_ITERATIONS `::class` accesses:      $classAccessCost")
            appendLine("Cost of $MEASURED_ITERATIONS HashMap<KClass,V> gets:  $hashMapLookupCost")
            appendLine("Cost of $MEASURED_ITERATIONS HashMap<Int,V> gets:     $intKeyedLookupCost")
            val rounded = kotlin.math.round(overheadRatio * 100) / 100
            appendLine("KClass-keyed overhead vs Int-keyed:        ${rounded}x")
        }
    }

    fun run(platform: String): Result {
        val identityStable = checkIdentityStability()
        val hashCodeStable = checkHashCodeStability()

        // Warmup (let the JIT/compiler settle before measuring; also matters
        // on Kotlin/Native to trigger any lazy metadata initialization once).
        repeat(WARMUP_ITERATIONS) { benchmarkClassAccess(1) }
        repeat(WARMUP_ITERATIONS) { benchmarkHashMapLookup(1) }
        repeat(WARMUP_ITERATIONS) { benchmarkIntKeyedLookup(1) }

        val classAccessCost = measureTime { benchmarkClassAccess(MEASURED_ITERATIONS) }
        val hashMapLookupCost = measureTime { benchmarkHashMapLookup(MEASURED_ITERATIONS) }
        val intKeyedLookupCost = measureTime { benchmarkIntKeyedLookup(MEASURED_ITERATIONS) }

        return Result(
            platform = platform,
            identityStable = identityStable,
            hashCodeStable = hashCodeStable,
            classAccessCost = classAccessCost,
            hashMapLookupCost = hashMapLookupCost,
            intKeyedLookupCost = intKeyedLookupCost,
        )
    }

    /** Mirrors the article's `String::class === String::class` check, generalized to 40 types. */
    private fun checkIdentityStability(): Boolean {
        val first = sampleClasses()
        val second = sampleClasses()
        return first.indices.all { i -> first[i] === second[i] }
    }

    private fun checkHashCodeStability(): Boolean {
        val first = sampleClasses().map { it.hashCode() }
        val second = sampleClasses().map { it.hashCode() }
        return first == second
    }

    private fun benchmarkClassAccess(iterations: Int) {
        var sink = 0
        var i = 0
        while (i < iterations) {
            sink += sampleClasses()[i % 40].hashCode()
            i++
        }
        // Prevent dead-code elimination of the loop by the optimizer.
        if (sink == Int.MIN_VALUE) println(sink)
    }

    private fun benchmarkHashMapLookup(iterations: Int) {
        val map = HashMap<kotlin.reflect.KClass<*>, Int>()
        sampleClasses().forEachIndexed { index, kClass -> map[kClass] = index }
        val keys = sampleClasses()

        var sink = 0
        var i = 0
        while (i < iterations) {
            sink += map[keys[i % 40]] ?: 0
            i++
        }
        if (sink == Int.MIN_VALUE) println(sink)
    }

    private fun benchmarkIntKeyedLookup(iterations: Int) {
        val map = HashMap<Int, Int>()
        repeat(40) { index -> map[index] = index }

        var sink = 0
        var i = 0
        while (i < iterations) {
            sink += map[i % 40] ?: 0
            i++
        }
        if (sink == Int.MIN_VALUE) println(sink)
    }
}
