package community.flock.kmapper.benchmarks

/**
 * A zero-config benchmark runner for quick sanity checks.
 *
 * Run with: `./gradlew -Pkmapper.benchmarks=true :benchmarks:run`
 *
 * This is intentionally simpler than the JMH harness — it uses
 * `System.nanoTime()` and a manual warmup loop. The numbers it produces are
 * indicative, not authoritative: JIT effects, GC pauses, and dead-code
 * elimination can all skew results. For trustworthy numbers, use:
 *   `./gradlew -Pkmapper.benchmarks=true :benchmarks:benchmark`
 *
 * The runner exists so that anyone can replicate the comparison in seconds
 * without learning JMH, and so that CI smoke tests can detect order-of-
 * magnitude regressions cheaply.
 */
private const val WARMUP_ITERATIONS = 100_000
private const val MEASURE_ITERATIONS = 1_000_000

fun main() {
    println("kmapper micro-benchmark (System.nanoTime, indicative only)")
    println("Warmup: $WARMUP_ITERATIONS iters, Measure: $MEASURE_ITERATIONS iters")
    println("For accurate numbers, run: ./gradlew -Pkmapper.benchmarks=true :benchmarks:benchmark")
    println()

    runScenario(
        name = "Simple (3-field flat data class)",
        kmapper = { Fixtures.simpleUser.toDtoKMapper() },
        manual = { Fixtures.simpleUser.toDtoManual() },
    )

    runScenario(
        name = "Complex (nested + value classes + list of 10 + enum + widening)",
        kmapper = { Fixtures.complexOrder.toDtoKMapper() },
        manual = { Fixtures.complexOrder.toDtoManual() },
    )
}

private inline fun runScenario(
    name: String,
    crossinline kmapper: () -> Any,
    crossinline manual: () -> Any,
) {
    println("=== $name ===")

    // Warmup — interleave both to trigger JIT compilation on both call sites
    // before either is measured.
    var sink: Any = Unit
    repeat(WARMUP_ITERATIONS) {
        sink = kmapper()
        sink = manual()
    }

    val kmapperNs = measure(MEASURE_ITERATIONS) { kmapper() }
    val manualNs = measure(MEASURE_ITERATIONS) { manual() }

    val kmapperPerOp = kmapperNs.toDouble() / MEASURE_ITERATIONS
    val manualPerOp = manualNs.toDouble() / MEASURE_ITERATIONS
    val overheadPct = ((kmapperPerOp - manualPerOp) / manualPerOp) * 100.0

    println("  manual : %.2f ns/op".format(manualPerOp))
    println("  kmapper: %.2f ns/op".format(kmapperPerOp))
    println("  overhead: %+.2f%%".format(overheadPct))
    println("  (sink to defeat DCE: ${sink::class.simpleName})")
    println()
}

private inline fun measure(iterations: Int, crossinline op: () -> Any): Long {
    // Capture into a volatile-ish field so the JIT can't elide the call.
    var sink: Any = Unit
    val start = System.nanoTime()
    repeat(iterations) {
        sink = op()
    }
    val end = System.nanoTime()
    // Touch sink so the loop body has an observable side effect.
    if (sink.hashCode() == Int.MIN_VALUE) println("unreachable")
    return end - start
}
