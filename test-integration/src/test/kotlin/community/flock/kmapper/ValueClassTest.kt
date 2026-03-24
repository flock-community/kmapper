package community.flock.kmapper

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ValueClassTest {

    val options = IntegrationTest.Options(
        kotlinVersion = "2.3.10",
    )

    @Test
    fun shouldCompile_valueClass() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |@JvmInline
                |value class Id(val id: Int)
                |data class User(val id: Id, val name: String, val age: Int)
                |
                |@JvmInline
                |value class IdDto(val id: Int)
                |data class UserDto(val id: IdDto, val name: String, val age: Int)
                |
                |fun main() {
                |  val user = User(id=Id(1), name="John Doe", age=99)
                |  val res:UserDto = user.mapper()
                |  println(res)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("UserDto(id=IdDto(id=1), name=John Doe, age=99)"),
                    "Expected UserDto(id=IdDto(id=1), name=John Doe, age=99) in output"
                )
            }
    }

    @Test
    fun shouldCompile_valueClassUnwrap() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |@JvmInline
                |value class Id(val id: Int)
                |data class Source(val id: Id, val name: String)
                |data class Target(val id: Int, val name: String)
                |
                |fun main() {
                |  val source = Source(id=Id(42), name="test")
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
    fun shouldCompile_valueClassWrap() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |@JvmInline
                |value class Id(val id: Int)
                |data class Source(val id: Int, val name: String)
                |data class Target(val id: Id, val name: String)
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
                    output.contains("Target(id=Id(id=42), name=test)"),
                    "Expected Target(id=Id(id=42), name=test) in output"
                )
            }
    }

    @Test
    fun shouldCompile_valueClassToValueClass() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |@JvmInline
                |value class SourceId(val id: Int)
                |data class Source(val id: SourceId, val name: String)
                |
                |@JvmInline
                |value class TargetId(val id: Int)
                |data class Target(val id: TargetId, val name: String)
                |
                |fun main() {
                |  val source = Source(id=SourceId(42), name="test")
                |  val target:Target = source.mapper()
                |  println(target)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("Target(id=TargetId(id=42), name=test)"),
                    "Expected Target(id=TargetId(id=42), name=test) in output"
                )
            }
    }

    @Test
    fun shouldCompile_valueClassDeepMap() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |@JvmInline
                |value class Id(val id: Int)
                |data class User(val id: Id, val name: String)
                |
                |@JvmInline
                |value class IdDto(val id: Int)
                |data class UserDto(val id: IdDto, val name: String)
                |
                |fun main() {
                |  val user = User(id=Id(1), name="John Doe")
                |  val res:UserDto = user.mapper()
                |  println(res)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("UserDto(id=IdDto(id=1), name=John Doe)"),
                    "Expected UserDto(id=IdDto(id=1), name=John Doe) in output"
                )
            }
    }

    @Test
    fun shouldCompile_valueClassUnwrapToNullable() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |@JvmInline
                |value class Id(val id: Int)
                |data class Source(val id: Id, val name: String)
                |data class Target(val id: Int?, val name: String)
                |
                |fun main() {
                |  val source = Source(id=Id(42), name="test")
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
    fun shouldCompile_valueClassUnwrapInNested() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |@JvmInline
                |value class Id(val id: Int)
                |data class Inner(val id: Id, val label: String)
                |data class Source(val inner: Inner, val name: String)
                |
                |data class InnerDto(val id: Int, val label: String)
                |data class Target(val inner: InnerDto, val name: String)
                |
                |fun main() {
                |  val source = Source(inner=Inner(Id(42), "hello"), name="test")
                |  val target:Target = source.mapper()
                |  println(target)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("Target(inner=InnerDto(id=42, label=hello), name=test)"),
                    "Expected Target(inner=InnerDto(id=42, label=hello), name=test) in output"
                )
            }
    }

    @Test
    fun shouldFail_nullableValueClassToNonNullable() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |@JvmInline
                |value class Id(val id: Int)
                |data class Source(val id: Id?, val name: String)
                |data class Target(val id: Int, val name: String)
                |
                |fun main() {
                |  val source = Source(id=Id(42), name="test")
                |  val target:Target = source.mapper()
                |  println(target)
                |}
                |
                """.trimMargin()
            }
            .compileFail { output ->
                assertTrue(
                    output.contains("Missing mapping for: id"),
                    "Expected Missing mapping for: id"
                )
            }
    }

    @Test
    fun shouldCompile_valueClassWrapWithCompanionInvoke() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |@JvmInline
                |value class Email(val value: String) {
                |  companion object {
                |    operator fun invoke(value: String): Email {
                |      require(value.contains("@")) { "${'$'}value" }
                |      return Email(value)
                |    }
                |  }
                |}
                |data class Source(val email: String, val name: String)
                |data class Target(val email: Email, val name: String)
                |
                |fun main() {
                |  val source = Source(email="test@example.com", name="test")
                |  val target:Target = source.mapper()
                |  println(target)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("Target(email=Email(value=test@example.com), name=test)"),
                    "Expected Target(email=Email(value=test@example.com), name=test) in output"
                )
            }
    }

    @Test
    fun shouldCompile_valueClassUnwrapWithCompanionInvoke() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |@JvmInline
                |value class Email(val value: String) {
                |  companion object {
                |    operator fun invoke(value: String): Email {
                |      require(value.contains("@")) { "${'$'}value" }
                |      return Email(value)
                |    }
                |  }
                |}
                |data class Source(val email: Email, val name: String)
                |data class Target(val email: String, val name: String)
                |
                |fun main() {
                |  val source = Source(email=Email("test@example.com"), name="test")
                |  val target:Target = source.mapper()
                |  println(target)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("Target(email=test@example.com, name=test)"),
                    "Expected Target(email=test@example.com, name=test) in output"
                )
            }
    }

    @Test
    fun shouldFail_valueClassWideningCompose() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |@JvmInline
                |value class Id(val id: Int)
                |data class Source(val id: Id, val name: String)
                |data class Target(val id: Long, val name: String)
                |
                |fun main() {
                |  val source = Source(id=Id(42), name="test")
                |  val target:Target = source.mapper()
                |  println(target)
                |}
                |
                """.trimMargin()
            }
            .compileFail { output ->
                assertTrue(
                    output.contains("Missing mapping for: id"),
                    "Expected Missing mapping for: id"
                )
            }
    }
}
