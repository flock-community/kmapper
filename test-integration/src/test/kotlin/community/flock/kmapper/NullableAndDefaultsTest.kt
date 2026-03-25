package community.flock.kmapper

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NullableAndDefaultsTest {

    val options = IntegrationTest.Options(
        kotlinVersion = "2.3.10",
    )

    @Test
    fun shouldFail_nullableFieldsFromNullable() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |data class Person(val firstName: String, val lastName: String?)
                |data class PersonDto(val firstName: String, val lastName: String)
                |
                |fun main() {
                |  val person = Person(firstName="John", lastName=null)
                |  val dto:PersonDto = person.mapper()
                |  println(dto)
                |}
                |
                """.trimMargin()
            }
            .compileFail{ output ->
                assertTrue(
                    output.contains("Missing mapping for: lastName"),
                    "Missing mapping for: lastName)"
                )
            }
    }

    @Test
    fun shouldSuccess_nullableFieldsToNullable() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |data class Person(val firstName: String, val lastName: String)
                |data class PersonDto(val firstName: String, val lastName: String?)
                |
                |fun main() {
                |  val person = Person(firstName="John", lastName="Doe")
                |  val dto:PersonDto = person.mapper()
                |  println(dto)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess{ output ->
                assertTrue(
                    output.contains("PersonDto(firstName=John, lastName=Doe)"),
                    "PersonDto(firstName=John, lastName=Doe)"
                )
            }
    }

    @Test
    fun shouldSuccess_nullableFieldsIgnoreNullableField() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |data class Person(val firstName: String)
                |data class PersonDto(val firstName: String, val lastName: String?)
                |
                |fun main() {
                |  val person = Person(firstName="John")
                |  val dto:PersonDto = person.mapper{
                |    lastName = null
                |  }
                |  println(dto)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess{ output ->
                assertTrue(
                    output.contains("PersonDto(firstName=John, lastName=null)"),
                    "PersonDto(firstName=John, lastName=null)"
                )
            }
    }

    @Test
    fun shouldSuccess_defaultValues() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |data class Person(val firstName: String)
                |data class PersonDto(val firstName: String, val lastName: String = "Doe")
                |
                |fun main() {
                |  val person = Person(firstName="John")
                |  val dto:PersonDto = person.mapper()
                |  println(dto)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess{ output ->
                assertTrue(
                    output.contains("PersonDto(firstName=John, lastName=Doe)"),
                    "PersonDto(firstName=John, lastName=Doe)"
                )
            }
    }

    @Test
    fun shouldSuccess_overwriteValue() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |data class Person(val firstName: String)
                |data class PersonDto(val firstName: String)
                |
                |fun main() {
                |  val person = Person(firstName="John")
                |  val dto:PersonDto = person.mapper {
                |    firstName = "HELLO"
                |  }
                |  println(dto)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess{ output ->
                assertTrue(
                    output.contains("PersonDto(firstName=HELLO)"),
                    "PersonDto(firstName=HELLO)"
                )
            }
    }

    @Test
    fun shouldSuccess_ignoreValue() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |import community.flock.kmapper.ignore
                |
                |data class Person(val firstName: String)
                |data class PersonDto(val firstName: String = "HELLO")
                |
                |fun main() {
                |  val person = Person(firstName="John")
                |  val dto:PersonDto = person.mapper {
                |    firstName.ignore()
                |  }
                |  println(dto)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess{ output ->
                assertTrue(
                    output.contains("PersonDto(firstName=HELLO)"),
                    "PersonDto(firstName=HELLO)"
                )
            }
    }
}
