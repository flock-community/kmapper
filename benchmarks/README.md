# benchmarks

Performance comparison between **kmapper-generated** mappings and
**hand-written** constructor calls between two data classes.

Because kmapper is a compile-time codegen plugin (no reflection at runtime),
the expected runtime overhead is essentially zero. This module exists to
prove that empirically, to make the claim replicable, and to catch
regressions if a future change to the IR generator accidentally introduces
runtime cost.

## What is measured

Two scenarios, each with a `kmapper` variant and a `manual` variant:

| Scenario  | Source shape                                                                                                                  | Target shape                                          |
| --------- | ----------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------- |
| `simple`  | flat 3-field data class                                                                                                       | flat 2-field data class with one derived field        |
| `complex` | nested data classes, two `@JvmInline value class`es, a list of 10 nested objects, an enum, an `Int → Long` widening, derived field | flat-equivalent DTO graph with the same value/enum/list structure |

The two variants do the same work: same constructor calls, same string
concatenation for the derived name, same `List.map` for the line items.
Only the *source* of the mapper code differs (kmapper IR vs hand-written
Kotlin).

## How to run

There are two runners. **Use both** — they answer different questions.

### Bootstrap (one-time, plus after every change to the compiler plugin)

This module applies the `community.flock.kmapper` Gradle plugin to its own
sources, so the plugin must be in `mavenLocal` *before* the module can even
be configured. To avoid breaking the parent build for everyone else,
`:benchmarks` is only included when the opt-in property is set.

```bash
./gradlew :compiler-plugin:publishToMavenLocal \
          :compiler-runtime:publishToMavenLocal \
          :gradle-plugin:publishToMavenLocal
```

Subsequent commands all need `-Pkmapper.benchmarks=true` to include the module.

### 1. JMH via kotlinx-benchmark (authoritative)

```bash
./gradlew -Pkmapper.benchmarks=true :benchmarks:benchmark
```

This forks a fresh JVM, runs a configurable warmup, measures average time
per op in nanoseconds, and writes a JSON report to `build/reports/benchmarks/main/`.
Defaults are tuned for CI (warmups=2, iterations=3, 1s each) so the whole
run completes in under a minute. For trustworthy numbers on a quiet
workstation, override:

```bash
./gradlew -Pkmapper.benchmarks=true :benchmarks:benchmark \
          -PbenchmarkWarmups=5 -PbenchmarkIterations=10
```

JMH guards against dead-code elimination, constant folding, and on-stack
replacement skew. This is the number to quote in PRs and release notes.

### 2. Standalone nanoTime runner (replicable smoke test)

```bash
./gradlew -Pkmapper.benchmarks=true :benchmarks:run
```

Prints `ns/op` and a `% overhead` figure for each scenario in a few
seconds. Implementation lives in [`ManualRunner.kt`](src/main/kotlin/community/flock/kmapper/benchmarks/ManualRunner.kt).
Numbers are *indicative only*: a hand-rolled loop can't fully defeat JIT
optimizations the way JMH can. Useful for:

- a 10-second sanity check while iterating on the compiler plugin
- a CI step that fails only on order-of-magnitude regressions
- demoing the comparison without asking reviewers to learn JMH

If JMH and the manual runner disagree by more than a few percent, **trust
JMH**.

### 3. Threshold gate (CI / regression guard)

```bash
./gradlew -Pkmapper.benchmarks=true :benchmarks:verifyBenchmarkThresholds
```

Runs `:benchmark`, then parses the JSON report and **fails the build** if any
kmapper benchmark is more than `benchmarkMaxRatio`x slower than its manual
counterpart. The default ratio is `5.0` — deliberately loose, because
GitHub-hosted runners are noisy and small-percent differences are not
distinguishable from variance. Override locally for a stricter check:

```bash
./gradlew -Pkmapper.benchmarks=true :benchmarks:verifyBenchmarkThresholds \
          -PbenchmarkMaxRatio=2.0
```

The CI job in [`.github/workflows/build.yml`](../.github/workflows/build.yml)
runs this task on every PR and uploads the JMH report as a build artifact.

## Other approaches considered

The current setup picks **kotlinx-benchmark + a simple `main()` runner**
because they are the most idiomatic Kotlin choices and cover both
"authoritative" and "replicable" needs. Alternatives, with the tradeoffs
that pushed them down the list:

- **Raw JMH with the `me.champeau.jmh` Gradle plugin.** Equally accurate;
  more Java-flavored configuration. Pick this if you want to drop the
  kotlinx wrapper and depend only on JMH itself.
- **JUnit `@Test` with `measureTimeMillis`.** Reuses the existing test
  infra but produces unreliable numbers (no JIT control, no fork isolation,
  no DCE protection). Adequate only for catching catastrophic regressions.
- **JFR / async-profiler over a long-running workload.** Excellent for
  flame-graphing where time is spent inside a generated mapping; not a
  substitute for `ns/op` numbers. Worth pairing with JMH once a regression
  is suspected:
  ```bash
  ./gradlew :benchmarks:benchmark -Pjvmargs="-XX:+FlightRecorder -XX:StartFlightRecording=duration=60s,filename=kmapper.jfr"
  ```
- **Compile-time overhead benchmark.** A separate question: how much does
  *adding* the kmapper plugin slow down `kotlinc`? Out of scope for this
  module (it would need clean-build timing of synthetic projects, not
  micro-benchmarks). Track it separately if it becomes a concern.

## How to add a new scenario

1. Add domain + dto data classes and two mapper functions
   (`toDtoKMapper()` using `mapper { ... }`, `toDtoManual()` using a plain
   constructor) to a new file under `src/main/kotlin/community/flock/kmapper/benchmarks/`.
2. Add a fixture instance to [`Fixtures.kt`](src/main/kotlin/community/flock/kmapper/benchmarks/Fixtures.kt).
3. Add a `@State` class with two `@Benchmark` methods (`kmapper` and
   `manual`) following the pattern of [`SimpleMappingBenchmark.kt`](src/main/kotlin/community/flock/kmapper/benchmarks/SimpleMappingBenchmark.kt).
4. Optionally add a `runScenario(...)` call in
   [`ManualRunner.kt`](src/main/kotlin/community/flock/kmapper/benchmarks/ManualRunner.kt).

Both runners pick up new benchmarks automatically — no extra wiring.

## Reproducibility notes

- Pin a Java toolchain (`jvmToolchain(21)`) so JIT differences between
  Java versions don't pollute results.
- Run on a quiet machine. Close browsers, IDE indexers, Spotlight, etc.
- Disable CPU frequency scaling if you have access to it
  (`sudo cpupower frequency-set -g performance` on Linux).
- Quote three numbers per scenario: `manual ns/op`, `kmapper ns/op`,
  `% overhead`. A single number hides whether the absolute cost is
  meaningful for your workload.
- Re-run on the same hardware before claiming a regression is real.
  Single-run noise on a laptop is often ±5–10%.
