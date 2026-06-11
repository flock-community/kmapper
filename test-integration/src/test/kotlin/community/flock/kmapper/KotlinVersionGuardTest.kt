package community.flock.kmapper

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KotlinVersionGuardTest {

    val options = IntegrationTest.Options(
        kotlinVersion = "2.3.21",
    )

    @Test
    fun shouldFailFast_onKotlinOlderThanMinimum() {
        IntegrationTest(options)
            .file("App.kt") {
                """
                |package sample
                |
                |fun main() {
                |  println("should never compile")
                |}
                |
                """.trimMargin()
            }
            .compileFail { output ->
                assertTrue(
                    output.contains("kmapper requires Kotlin 2.4.0 or newer"),
                    "Expected a clear minimum-Kotlin-version error, got:\n$output"
                )
            }
    }
}
