package community.flock.kmapper

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors

class GradlePluginTest {

    @Test
    fun compileProjectWithPlugin_emitsMarker(@TempDir tempDir: Path) {
        // Write settings.gradle.kts
        Files.writeString(
            tempDir.resolve("settings.gradle.kts"),
            """
            |pluginManagement {
            |  repositories {
            |    gradlePluginPortal()
            |    mavenCentral()
            |    mavenLocal()
            |  }
            |}
            |rootProject.name = "sample"
            |""".trimMargin()
        )

        // Write build.gradle.kts
        Files.writeString(
            tempDir.resolve("build.gradle.kts"),
            """
            |plugins {
            |    id("community.flock.kmapper") version "0.0.0-SNAPSHOT"
            |    kotlin("jvm") version "2.2.20-RC"
            |    application
            |}
            |repositories { 
            |  mavenCentral()
            |  mavenLocal()
            |}
            |dependencies {
            |    implementation(kotlin("stdlib"))
            |}
            |kotlin {
            |  jvmToolchain(21)
            |}
            |application {
            |  mainClass.set("sample.AppKt")
            |}
            |""".trimMargin()
        )

        // Write sample Kotlin sources
        val srcDir = tempDir.resolve("src/main/kotlin"); Files.createDirectories(srcDir)
        // Provide the annotation in the sample project classpath
        val annDir = srcDir.resolve("community/flock/kmapper"); Files.createDirectories(annDir)
        Files.writeString(
            annDir.resolve("Flock.kt"),
            """
            |package community.flock.kmapper
            |
            |@Target(AnnotationTarget.CLASS)
            |annotation class Flock
            |""".trimMargin()
        )
        // App code: just println to test prefixing; no flock() call required
        Files.writeString(
            srcDir.resolve("App.kt"),
            """
            |package sample
            |
            |import community.flock.kmapper.Flock
            |
            |@Flock
            |data class User(val firstName: String, val lastName: String)
            |
            |fun main() {
            |  val u = User("Jane", "Doe")
            |  val res = u.to<String>(Pair("testName", 1))
            |  println(res)
            |}
            |""".trimMargin()
        )

        val result: BuildResult = GradleRunner.create()
            .withProjectDir(tempDir.toFile())
            .withArguments("run", "--info", "--stacktrace")
            .forwardOutput()
            .build()

        val output = result.output

        assertTrue(
            output.contains("[KMapperPlugin] Compiler plugin registrar loaded"),
            "Expected compiler plugin marker not found in output"
        )
    }

}
