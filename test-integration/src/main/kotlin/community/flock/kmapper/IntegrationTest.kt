package community.flock.kmapper

import org.gradle.testkit.runner.GradleRunner
import java.nio.file.Files

class IntegrationTest(options: Options) {
    data class Options(
        val kotlinVersion: String,
        val additionalPlugins: List<String> = emptyList(),
        val additionalDependencies: List<String> = emptyList(),
    )

    data class File(
        val path: String,
        val name: String,
        val content: String
    )

    val files = mutableListOf<File>(
        settingsGradle,
        buildGradle(options),
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

        fun buildGradle(options: Options): File {
            val additionalPlugins = options.additionalPlugins.joinToString("\n") { "|    $it" }
            val additionalDeps = options.additionalDependencies.joinToString("\n") { "|    implementation(\"$it\")" }
            return File(
                "",
                "build.gradle.kts",
                """
                |plugins {
                |    id("community.flock.kmapper") version "0.0.0-SNAPSHOT"
                |    kotlin("jvm") version "${options.kotlinVersion}"
                |    application
                ${additionalPlugins}
                |}
                |repositories {
                |  mavenCentral()
                |  mavenLocal()
                |}
                |dependencies {
                |    implementation(kotlin("stdlib"))
                ${additionalDeps}
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
}