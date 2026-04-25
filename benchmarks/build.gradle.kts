plugins {
    kotlin("jvm") version "2.3.20"
    kotlin("plugin.allopen") version "2.3.20"
    id("org.jetbrains.kotlinx.benchmark") version "0.4.13"
    id("community.flock.kmapper") version "0.0.0-SNAPSHOT"
    application
}

group = rootProject.group
version = rootProject.version

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-benchmark-runtime:0.4.13")
}

kotlin {
    jvmToolchain(21)
}

// JMH requires @State classes to be open (non-final). The all-open plugin
// rewrites them at compile time so we don't have to write `open class`.
allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

// CI-friendly defaults: keep total wall time under ~1 min so benchmarks can
// run on every PR. Override locally for trustworthy numbers, e.g.:
//   ./gradlew -Pkmapper.benchmarks=true :benchmarks:benchmark \
//             -PbenchmarkWarmups=5 -PbenchmarkIterations=10
val benchmarkWarmups = (project.findProperty("benchmarkWarmups") as String? ?: "2").toInt()
val benchmarkIterations = (project.findProperty("benchmarkIterations") as String? ?: "3").toInt()

benchmark {
    targets {
        register("main")
    }
    configurations {
        named("main") {
            warmups = benchmarkWarmups
            iterations = benchmarkIterations
            iterationTime = 1
            iterationTimeUnit = "s"
            outputTimeUnit = "ns"
            mode = "avgt"
            reportFormat = "json"
        }
    }
}

application {
    mainClass.set("community.flock.kmapper.benchmarks.ManualRunnerKt")
}

// The kmapper Gradle plugin and the compiler-runtime are resolved from
// mavenLocal. They must be published BEFORE this module compiles, so wire
// the dependency explicitly (mirrors test-integration/build.gradle.kts).
val publishKmapperLocally = listOf(
    ":compiler-plugin:publishToMavenLocal",
    ":compiler-runtime:publishToMavenLocal",
    ":gradle-plugin:publishToMavenLocal",
)
tasks.named("compileKotlin") { publishKmapperLocally.forEach { dependsOn(it) } }
tasks.matching { it.name == "mainBenchmarkCompile" || it.name == "mainBenchmark" }
    .configureEach { publishKmapperLocally.forEach { dependsOn(it) } }

// Threshold gate: parse the JMH JSON report and fail if any kmapper benchmark
// is more than `benchmarkMaxRatio`x slower than its hand-written counterpart.
// The default ratio is deliberately loose (5x) because GitHub-hosted runners
// are noisy. Override locally for tighter checks:
//   ./gradlew -Pkmapper.benchmarks=true :benchmarks:verifyBenchmarkThresholds \
//             -PbenchmarkMaxRatio=2.0
val verifyBenchmarkThresholds by tasks.registering {
    group = "verification"
    description = "Fails if kmapper-generated mappers are >Nx slower than hand-written equivalents"
    dependsOn("benchmark")

    val maxRatio = (project.findProperty("benchmarkMaxRatio") as String? ?: "5.0").toDouble()
    val reportRoot = layout.buildDirectory.dir("reports/benchmarks/main")

    doLast {
        val root = reportRoot.get().asFile
        val jsonFile = root.walkTopDown()
            .filter { it.isFile && it.extension == "json" }
            .maxByOrNull { it.lastModified() }
            ?: error("No JMH JSON report found under $root — did `:benchmark` run with reportFormat=\"json\"?")

        // Minimal hand-rolled parse: each entry has
        //   "benchmark": "<fqcn>.<method>", "primaryMetric": { "score": <num>, ... }
        val text = jsonFile.readText()
        val entryRegex = Regex(
            """"benchmark"\s*:\s*"([^"]+)"[\s\S]*?"primaryMetric"\s*:\s*\{[\s\S]*?"score"\s*:\s*([0-9.eE+-]+)"""
        )
        val scores: Map<String, Double> = entryRegex.findAll(text).associate { m ->
            // benchmark id is "...SimpleMappingBenchmark.kmapper" — key by ClassName.method
            val id = m.groupValues[1].split('.').takeLast(2).joinToString(".")
            id to m.groupValues[2].toDouble()
        }
        require(scores.isNotEmpty()) {
            "Could not parse any benchmark scores from $jsonFile"
        }

        val pairs = listOf(
            "SimpleMappingBenchmark" to ("manual" to "kmapper"),
            "ComplexMappingBenchmark" to ("manual" to "kmapper"),
        )
        val report = StringBuilder().appendLine(
            "Benchmark threshold report (max ratio = ${maxRatio}x, source: ${jsonFile.name}):"
        )
        val failures = mutableListOf<String>()
        for ((cls, methods) in pairs) {
            val (manualMethod, kmapperMethod) = methods
            val manualKey = "$cls.$manualMethod"
            val kmapperKey = "$cls.$kmapperMethod"
            val manualScore = scores[manualKey]
            val kmapperScore = scores[kmapperKey]
            if (manualScore == null || kmapperScore == null) {
                failures += "missing scores for $cls (manual=$manualScore, kmapper=$kmapperScore)"
                continue
            }
            val ratio = kmapperScore / manualScore
            report.appendLine(
                "  %s: kmapper=%.2f ns, manual=%.2f ns, ratio=%.2fx".format(
                    cls, kmapperScore, manualScore, ratio,
                )
            )
            if (ratio > maxRatio) {
                failures += "$cls: kmapper is ${"%.2f".format(ratio)}x slower than manual (limit=${maxRatio}x)"
            }
        }
        logger.lifecycle(report.toString())
        if (failures.isNotEmpty()) {
            error("Benchmark threshold violations:\n  - " + failures.joinToString("\n  - "))
        }
    }
}
