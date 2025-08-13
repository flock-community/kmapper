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

class CompilerPluginFunctionalTest {

    @Test
    fun compileProjectWithPlugin_emitsMarker(@TempDir tempDir: Path) {
        val pluginJar = locatePluginJar()
        require(pluginJar.isFile) { "Plugin jar not found at: $pluginJar" }

        // Write settings.gradle.kts
        Files.writeString(
            tempDir.resolve("settings.gradle.kts"),
            """
            |pluginManagement {
            |  repositories {
            |    gradlePluginPortal()
            |    mavenCentral()
            |  }
            |}
            |plugins {
            |  id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
            |}
            |rootProject.name = "sample"
            |""".trimMargin()
        )

        // Write build.gradle.kts
        Files.writeString(
            tempDir.resolve("build.gradle.kts"),
            """
            |plugins {
            |    kotlin("jvm") version "2.2.0"
            |    application
            |}
            |repositories { mavenCentral() }
            |dependencies {
            |    implementation(kotlin("stdlib"))
            |}
            |kotlin {
            |  jvmToolchain(17)
            |  compilerOptions {
            |    freeCompilerArgs.add("-Xplugin=${'$'}{pluginJarPath}")
            |  }
            |}
            |application {
            |  mainClass.set("sample.AppKt")
            |}
            |""".trimMargin().replace("${'$'}{pluginJarPath}", pluginJar.absolutePath.replace("\\", "\\\\"))
        )

        // Write a simple Kotlin source file
        val srcDir = tempDir.resolve("src/main/kotlin"); Files.createDirectories(srcDir)
        Files.writeString(
            srcDir.resolve("App.kt"),
            """
            |package sample
            |
            |fun main() {
            |  println("Hello from sample")
            |}
            |""".trimMargin()
        )

        val result: BuildResult = GradleRunner.create()
            .withProjectDir(tempDir.toFile())
            .withArguments("run", "--info")
            .forwardOutput()
            .build()

        val output = result.output
        assertTrue(
            output.contains("[KMapperPlugin] Compiler plugin registrar loaded"),
            "Expected compiler plugin marker not found in output"
        )
        assertTrue(
            output.contains("FLOCK Hello from sample"),
            "Expected println output prefixed with 'FLOCK' not found"
        )
    }

    private fun locatePluginJar(): File {
        val libsDir = File("../plugin/build/libs").absoluteFile.normalize()
        if (!libsDir.isDirectory) return File("nonexistent")
        val jar = Files.list(libsDir.toPath())
            .filter { it.toString().endsWith(".jar") }
            .collect(Collectors.toList())
            .firstOrNull()
        return jar?.toFile() ?: File("nonexistent")
    }
}
