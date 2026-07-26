/**
 * 40 distinct marker types used as synthetic "DI keys" for the benchmark.
 * Distinct classes (not generics of one class) are required because the
 * hypothesis under test is about per-KClass identity/lookup cost, which a
 * single generic class with different type args would not exercise (KClass
 * of a generic type erases to the same runtime class on both platforms).
 */
class Key00; class Key01; class Key02; class Key03; class Key04
class Key05; class Key06; class Key07; class Key08; class Key09
class Key10; class Key11; class Key12; class Key13; class Key14
class Key15; class Key16; class Key17; class Key18; class Key19
class Key20; class Key21; class Key22; class Key23; class Key24
class Key25; class Key26; class Key27; class Key28; class Key29
class Key30; class Key31; class Key32; class Key33; class Key34
class Key35; class Key36; class Key37; class Key38; class Key39

/** Returns the KClass of each of the 40 marker types, in stable order. */
fun sampleClasses(): List<kotlin.reflect.KClass<*>> = listOf(
    Key00::class, Key01::class, Key02::class, Key03::class, Key04::class,
    Key05::class, Key06::class, Key07::class, Key08::class, Key09::class,
    Key10::class, Key11::class, Key12::class, Key13::class, Key14::class,
    Key15::class, Key16::class, Key17::class, Key18::class, Key19::class,
    Key20::class, Key21::class, Key22::class, Key23::class, Key24::class,
    Key25::class, Key26::class, Key27::class, Key28::class, Key29::class,
    Key30::class, Key31::class, Key32::class, Key33::class, Key34::class,
    Key35::class, Key36::class, Key37::class, Key38::class, Key39::class,
)
