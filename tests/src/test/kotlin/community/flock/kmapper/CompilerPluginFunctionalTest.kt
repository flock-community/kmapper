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
            |    kotlin("jvm") version "2.2.20-RC"
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
        // App code: just println to test prefixing; no to() call required
        Files.writeString(
            srcDir.resolve("App.kt"),
            """
            |package sample
            |
            |import community.flock.kmapper.Flock
            |
            |@Flock
            |class User {
            |  override fun toString(): String = "User"
            |}
            |
            |fun main() {
            |  val u = User()
            |  println(u.to())
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
            output.contains("FLOCK User"),
            "Expected println output prefixed with 'FLOCK' not found"
        )
    }

    private fun locatePluginJar(): File {
        val libsDir = File("../compiler-plugin/build/libs").absoluteFile.normalize()
        if (!libsDir.isDirectory) return File("nonexistent")
        val jar = Files.list(libsDir.toPath())
            .filter { it.toString().endsWith(".jar") }
            .collect(Collectors.toList())
            .firstOrNull()
        return jar?.toFile() ?: File("nonexistent")
    }
}
