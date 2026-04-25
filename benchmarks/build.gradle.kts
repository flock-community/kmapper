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

benchmark {
    targets {
        register("main")
    }
    configurations {
        named("main") {
            warmups = 5
            iterations = 5
            iterationTime = 1
            iterationTimeUnit = "s"
            outputTimeUnit = "ns"
            mode = "avgt"
        }
    }
}

application {
    mainClass.set("community.flock.kmapper.benchmarks.ManualRunnerKt")
}

// The kmapper Gradle plugin and the compiler-runtime are resolved from
// mavenLocal at *configuration* time. They must be published BEFORE this
// module is configured — see settings.gradle.kts and benchmarks/README.md
// for the two-step bootstrap workflow.
