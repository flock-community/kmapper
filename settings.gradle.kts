pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
        maven(url="https://central.sonatype.com/repository/maven-snapshots/")
        maven("https://storage.googleapis.com/gradleup/m2")
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap")
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        maven(url="https://central.sonatype.com/repository/maven-snapshots/")
        maven("https://storage.googleapis.com/gradleup/m2")
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "kmapper"

include(
    ":compiler-plugin",
    "compiler-runtime",
    ":gradle-plugin",
    ":test-framework",
    ":test-integration"
)

// :benchmarks applies the kmapper Gradle plugin to its own sources and so
// requires the plugin to already be in mavenLocal at *configuration* time.
// We gate inclusion behind an opt-in property to avoid breaking the parent
// build before the bootstrap publish has happened.
//
//   1. Publish:  ./gradlew :compiler-plugin:publishToMavenLocal \
//                          :compiler-runtime:publishToMavenLocal \
//                          :gradle-plugin:publishToMavenLocal
//   2. Run:      ./gradlew -Pkmapper.benchmarks=true :benchmarks:benchmark
if (providers.gradleProperty("kmapper.benchmarks").orNull == "true") {
    include(":benchmarks")
}
