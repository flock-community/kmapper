package community.flock.kmapper

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NumericWideningTest {

    val options = IntegrationTest.Options(
        kotlinVersion = "2.3.10",
    )

    @Test
    fun shouldCompile_byteToShortWidening() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |data class Source(val value: Byte, val name: String)
                |data class Target(val value: Short, val name: String)
                |
                |fun main() {
                |  val source = Source(value=7, name="test")
                |  val target:Target = source.mapper()
                |  println(target)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("Target(value=7, name=test)"),
                    "Expected Target(value=7, name=test) in output"
                )
            }
    }

    @Test
    fun shouldCompile_byteToIntWidening() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |data class Source(val value: Byte, val name: String)
                |data class Target(val value: Int, val name: String)
                |
                |fun main() {
                |  val source = Source(value=7, name="test")
                |  val target:Target = source.mapper()
                |  println(target)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("Target(value=7, name=test)"),
                    "Expected Target(value=7, name=test) in output"
                )
            }
    }

    @Test
    fun shouldCompile_byteToLongWidening() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |data class Source(val value: Byte, val name: String)
                |data class Target(val value: Long, val name: String)
                |
                |fun main() {
                |  val source = Source(value=7, name="test")
                |  val target:Target = source.mapper()
                |  println(target)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("Target(value=7, name=test)"),
                    "Expected Target(value=7, name=test) in output"
                )
            }
    }

    @Test
    fun shouldCompile_byteToFloatWidening() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |data class Source(val value: Byte, val name: String)
                |data class Target(val value: Float, val name: String)
                |
                |fun main() {
                |  val source = Source(value=7, name="test")
                |  val target:Target = source.mapper()
                |  println(target)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("Target(value=7.0, name=test)"),
                    "Expected Target(value=7.0, name=test) in output"
                )
            }
    }

    @Test
    fun shouldCompile_byteToDoubleWidening() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |data class Source(val value: Byte, val name: String)
                |data class Target(val value: Double, val name: String)
                |
                |fun main() {
                |  val source = Source(value=7, name="test")
                |  val target:Target = source.mapper()
                |  println(target)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("Target(value=7.0, name=test)"),
                    "Expected Target(value=7.0, name=test) in output"
                )
            }
    }

    @Test
    fun shouldCompile_shortToIntWidening() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |data class Source(val value: Short, val name: String)
                |data class Target(val value: Int, val name: String)
                |
                |fun main() {
                |  val source = Source(value=7, name="test")
                |  val target:Target = source.mapper()
                |  println(target)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("Target(value=7, name=test)"),
                    "Expected Target(value=7, name=test) in output"
                )
            }
    }

    @Test
    fun shouldCompile_shortToLongWidening() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |data class Source(val value: Short, val name: String)
                |data class Target(val value: Long, val name: String)
                |
                |fun main() {
                |  val source = Source(value=7, name="test")
                |  val target:Target = source.mapper()
                |  println(target)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("Target(value=7, name=test)"),
                    "Expected Target(value=7, name=test) in output"
                )
            }
    }

    @Test
    fun shouldCompile_shortToFloatWidening() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |data class Source(val value: Short, val name: String)
                |data class Target(val value: Float, val name: String)
                |
                |fun main() {
                |  val source = Source(value=7, name="test")
                |  val target:Target = source.mapper()
                |  println(target)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("Target(value=7.0, name=test)"),
                    "Expected Target(value=7.0, name=test) in output"
                )
            }
    }

    @Test
    fun shouldCompile_shortToDoubleWidening() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |data class Source(val value: Short, val name: String)
                |data class Target(val value: Double, val name: String)
                |
                |fun main() {
                |  val source = Source(value=7, name="test")
                |  val target:Target = source.mapper()
                |  println(target)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("Target(value=7.0, name=test)"),
                    "Expected Target(value=7.0, name=test) in output"
                )
            }
    }

    @Test
    fun shouldCompile_intToLongWidening() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |data class Source(val id: Int, val name: String)
                |data class Target(val id: Long, val name: String)
                |
                |fun main() {
                |  val source = Source(id=42, name="test")
                |  val target:Target = source.mapper()
                |  println(target)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("Target(id=42, name=test)"),
                    "Expected Target(id=42, name=test) in output"
                )
            }
    }

    @Test
    fun shouldCompile_intToFloatWidening() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |data class Source(val value: Int, val name: String)
                |data class Target(val value: Float, val name: String)
                |
                |fun main() {
                |  val source = Source(value=42, name="test")
                |  val target:Target = source.mapper()
                |  println(target)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("Target(value=42.0, name=test)"),
                    "Expected Target(value=42.0, name=test) in output"
                )
            }
    }

    @Test
    fun shouldCompile_intToDoubleWidening() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |data class Source(val value: Int, val name: String)
                |data class Target(val value: Double, val name: String)
                |
                |fun main() {
                |  val source = Source(value=42, name="test")
                |  val target:Target = source.mapper()
                |  println(target)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("Target(value=42.0, name=test)"),
                    "Expected Target(value=42.0, name=test) in output"
                )
            }
    }

    @Test
    fun shouldCompile_intToNullableLongWidening() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |data class Source(val value: Int, val name: String)
                |data class Target(val value: Long?, val name: String)
                |
                |fun main() {
                |  val source = Source(value=42, name="test")
                |  val target:Target = source.mapper()
                |  println(target)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("Target(value=42, name=test)"),
                    "Expected Target(value=42, name=test) in output"
                )
            }
    }

    @Test
    fun shouldCompile_longToFloatWidening() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |data class Source(val value: Long, val name: String)
                |data class Target(val value: Float, val name: String)
                |
                |fun main() {
                |  val source = Source(value=42L, name="test")
                |  val target:Target = source.mapper()
                |  println(target)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("Target(value=42.0, name=test)"),
                    "Expected Target(value=42.0, name=test) in output"
                )
            }
    }

    @Test
    fun shouldCompile_longToDoubleWidening() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |data class Source(val value: Long, val name: String)
                |data class Target(val value: Double, val name: String)
                |
                |fun main() {
                |  val source = Source(value=42L, name="test")
                |  val target:Target = source.mapper()
                |  println(target)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("Target(value=42.0, name=test)"),
                    "Expected Target(value=42.0, name=test) in output"
                )
            }
    }

    @Test
    fun shouldCompile_floatToDoubleWidening() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |data class Source(val value: Float, val name: String)
                |data class Target(val value: Double, val name: String)
                |
                |fun main() {
                |  val source = Source(value=3.14f, name="test")
                |  val target:Target = source.mapper()
                |  println(target)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("Target(value=3.14"),
                    "Expected Target(value=3.14...) in output"
                )
            }
    }

    @Test
    fun shouldFail_longToIntNarrowing() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |data class Source(val value: Long, val name: String)
                |data class Target(val value: Int, val name: String)
                |
                |fun main() {
                |  val source = Source(value=42L, name="test")
                |  val target:Target = source.mapper()
                |  println(target)
                |}
                |
                """.trimMargin()
            }
            .compileFail { output ->
                assertTrue(
                    output.contains("Missing mapping for: value"),
                    "Expected Missing mapping for: value"
                )
            }
    }

    @Test
    fun shouldFail_doubleToFloatNarrowing() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |data class Source(val value: Double, val name: String)
                |data class Target(val value: Float, val name: String)
                |
                |fun main() {
                |  val source = Source(value=3.14, name="test")
                |  val target:Target = source.mapper()
                |  println(target)
                |}
                |
                """.trimMargin()
            }
            .compileFail { output ->
                assertTrue(
                    output.contains("Missing mapping for: value"),
                    "Expected Missing mapping for: value"
                )
            }
    }
}
