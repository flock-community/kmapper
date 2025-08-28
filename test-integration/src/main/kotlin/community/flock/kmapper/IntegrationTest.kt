package community.flock.kmapper

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import java.nio.file.Files

class IntegrationTest(options: Options) {
    data class Options(
        val kotlinVersion: String
    )

    data class File(
        val path: String,
        val name: String,
        val content: String
    )

    val files = mutableListOf<File>(
        settingsGradle,
        buildGradle,
    )

    fun file(file: String, content: () -> String): IntegrationTest {
        files.add(File("src/main/kotlin", file, content()))
        return this
    }

    fun compile(assert: (String) -> Unit): IntegrationTest {
        val tempDir = Files.createTempDirectory("")
        files.forEach {file ->
            val srcDir = tempDir
                .resolve(file.path)
                .apply (Files::createDirectories)
            print("write file: $srcDir ${file.path}")
            Files.writeString(srcDir.resolve(file.name), file.content)
        }

        val result: BuildResult = GradleRunner.create()
            .withProjectDir(tempDir.toFile())
            .withArguments("run", "--info")
            .forwardOutput()
            .run()

        val output = result.output

        assert(output)

        return this
    }

    companion object {
        val settingsGradle = File(
            "",
            "settings.gradle.kts",
            """
            |pluginManagement {
            |  repositories {
            |    gradlePluginPortal()
            |    mavenCentral()
            |    mavenLocal()
            |  }
            |}
            |rootProject.name = "sample"
            |
            """.trimMargin()
        )

        val buildGradle = File(
            "",
            "build.gradle.kts",
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
            """.trimMargin()
        )
    }
}