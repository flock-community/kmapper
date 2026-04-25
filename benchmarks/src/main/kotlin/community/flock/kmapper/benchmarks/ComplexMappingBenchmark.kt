package community.flock.kmapper.benchmarks

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.BenchmarkTimeUnit
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Scope
import kotlinx.benchmark.State

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.NANOSECONDS)
class ComplexMappingBenchmark {

    private val source = Fixtures.complexOrder

    @Benchmark
    fun kmapper(): OrderDto = source.toDtoKMapper()

    @Benchmark
    fun manual(): OrderDto = source.toDtoManual()
}
