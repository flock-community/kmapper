pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven(url="https://central.sonatype.com/repository/maven-snapshots/")
        maven("https://storage.googleapis.com/gradleup/m2")
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap")
    }
}

dependencyResolutionManagement {
    repositories {
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
