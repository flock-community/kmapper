package community.flock.kmapper

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KMapperTest {

    val options = IntegrationTest.Options(
        kotlinVersion = "2.2.20-RC",
    )

    @Test
    fun shouldCompile_happyFlow() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |data class User(val firstName: String, val lastName: String, val age: Int)
                |data class Id(val id: Int)
                |data class UserDto(val name: String, val age: Int)
                |
                |fun main() {
                |  val user = User("John", "Doe", 99)
                |  val userDto:UserDto = user.mapper {
                |    to::name map "${it.firstName} ${user.lastName}"
                |  }
                |  println(userDto)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("UserDto(name=John Doe, age=99)"),
                    "Expected UserDto(name=John Doe, age=99) in output"
                )
            }
    }

    @Test
    fun shouldCompile_identicalClasses() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |data class User(val id: Int, val name: String, val age: Int)
                |data class UserDto(val id: Int, val name: String, val age: Int)
                |
                |fun main() {
                |  val user = User(id=1, name="John Doe", age=99)
                |  val res:UserDto = user.mapper()
                |  println(res)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("UserDto(id=1, name=John Doe, age=99)"),
                    "Expected UserDto(name=John Doe, age=99) in output"
                )
            }
    }

    @Test
    fun shouldCompile_nestedEqualClasses() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |data class Person(val firstName: String, val lastName: String, val age: Int, val address:  Address)
                |data class Address(val street: String, val city: String, val zipCode: String)
                |
                |data class PersonDto(val name: String, val age: Int, val address: AddressDto)
                |data class AddressDto(val street: String, val city: String, val zipCode: String)
                |
                |fun main() {
                |    val user = Person(
                |        firstName = "John",
                |        lastName = "Doe",
                |        age = 99,
                |        address = Address("Main Street", "Hamburg", "22049")
                |    )
                |    val res: PersonDto = user.mapper {
                |        to::name map "${it.firstName} ${it.lastName}"
                |    }
                |    println(res)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("[KMapperPlugin] Compiler plugin registrar loaded"),
                    "Expected compiler plugin marker not found in output"
                )
                assertTrue(
                    output.contains("PersonDto(name=John Doe, age=99, address=AddressDto(street=Main Street, city=Hamburg, zipCode=22049))"),
                    "Expected PersonDto(name=John Doe, age=99, address=AddressDto(street=Main Street, city=Hamburg, zipCode=22049)) in output"
                )
            }
    }

    @Test
    fun shouldCompile_deepNestedEqualClasses() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |data class Person(val firstName: String, val lastName: String, val age: Int, val address:  Address)
                |data class Address(val streetCity: StreetCity, val zipCode: String)
                |data class StreetCity(val street: String, val city: String)
                |
                |data class PersonDto(val name: String, val age: Int, val address: AddressDto)
                |data class AddressDto(val streetCity: StreetCityDto, val zipCode: String)
                |data class StreetCityDto(val street: String, val city: String)
                |
                |fun main() {
                |    val user = Person(
                |        firstName = "John",
                |        lastName = "Doe",
                |        age = 99,
                |        address = Address(StreetCity("Main Street", "Hamburg"), "22049")
                |    )
                |    val res: PersonDto = user.mapper {
                |        to::name map "${it.firstName} ${it.lastName}"
                |    }
                |    println(res)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("PersonDto(name=John Doe, age=99, address=AddressDto(streetCity=StreetCityDto(street=Main Street, city=Hamburg), zipCode=22049))"),
                    "PersonDto(name=John Doe, age=99, address=AddressDto(streetCity=StreetCityDto(street=Main Street, city=Hamburg), zipCode=22049))"
                )
            }
    }


    @Test
    fun shouldFail_missingParameterAge() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |data class User(val firstName: String, val lastName: String)
                |data class Id(val id: Int)
                |data class UserDto(val name: String, val age: Int)
                |
                |fun main() {
                |  val user = User("John", "Doe")
                |  val userDto:UserDto = user.mapper {
                |    to::name map "${user.firstName} ${user.lastName}"
                |  }
                |}
                |
                """.trimMargin()
            }
            .compileFail { output ->
                assertTrue(
                    output.contains("Missing mapping for: age."),
                    "Missing mapping for: age."
                )
            }
    }

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
    fun shouldCompile_getField() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |data class User(val id: Int, val name: String) {
                |   val age get() = 1
                |}
                |
                |data class UserDto(val id: Int, val name: String, val age: Int)
                |
                |fun main() {
                |  val user = User(id=1, name="John Doe")
                |  val res:UserDto = user.mapper()
                |  println(res)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("UserDto(id=1, name=John Doe, age=1)"),
                    "Expected UserDto(id=1, name=John Doe, age=1) in output"
                )
            }
    }

    @Test
    fun shouldCompile_defaultField() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |data class User(val id: Int, val name: String, val age: Int = 99)
                |
                |data class UserDto(val id: Int, val name: String, val age: Int)
                |
                |fun main() {
                |  val user = User(id=1, name="John Doe")
                |  val res:UserDto = user.mapper()
                |  println(res)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("UserDto(id=1, name=John Doe, age=99)"),
                    "Expected UserDto(id=1, name=John Doe, age=99) in output"
                )
            }
    }

    @Test
    fun shouldFail_valueClassap() {
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
                |data class UserDto(val id: Int, val name: String)
                |
                |fun main() {
                |  val user = User(id=Id(1), name="John Doe")
                |  val res:UserDto = user.mapper()
                |  println(res)
                |}
                |
                """.trimMargin()
            }
            .compileFail { output ->
                assertTrue(
                    output.contains("Missing mapping for: id"),
                    "Missing mapping for: id"
                )
            }
    }

    @Test
    fun shouldFail_valueClassDeepMap() {
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
                    "Expected UserDto(id=IdDto(id=1), name=John Doe, age=99) in output"
                )
            }
    }
}
