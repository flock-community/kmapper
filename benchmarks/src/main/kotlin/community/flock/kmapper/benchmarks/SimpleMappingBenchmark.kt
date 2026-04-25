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
class SimpleMappingBenchmark {

    private val source = Fixtures.simpleUser

    @Benchmark
    fun kmapper(): SimpleUserDto = source.toDtoKMapper()

    @Benchmark
    fun manual(): SimpleUserDto = source.toDtoManual()
}
