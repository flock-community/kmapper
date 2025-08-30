package community.flock.kmapper

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

    private fun compile(): GradleRunner {
        val tempDir = Files.createTempDirectory("")
        files.forEach { file ->
            val srcDir = tempDir
                .resolve(file.path)
                .apply(Files::createDirectories)
            print("write file: $srcDir ${file.path}")
            Files.writeString(srcDir.resolve(file.name), file.content)
        }

        return GradleRunner.create()
            .withProjectDir(tempDir.toFile())
            .withArguments("run", "--info")
            .forwardOutput()

    }

    fun file(file: String, content: () -> String): IntegrationTest = files
        .add(File("src/main/kotlin", file, content()))
        .let { this }

    fun compileSuccess(assert: (String) -> Unit): IntegrationTest = this
        .compile()
        .build()
        .apply { assert(output) }
        .let { this }

    fun compileFail(assert: (String) -> Unit): IntegrationTest = this
        .compile()
        .buildAndFail()
        .apply { assert(output) }
        .let { this }

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
            |rootProject.name = "kmapper"
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