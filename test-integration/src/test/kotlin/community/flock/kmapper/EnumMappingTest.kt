package community.flock.kmapper

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EnumMappingTest {

    val options = IntegrationTest.Options(
        kotlinVersion = "2.3.10",
    )

    @Test
    fun shouldCompile_equalEnums() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |enum class Gender { MALE, FEMALE }
                |data class Address(val street: String, val city: String)
                |data class Person(val name: String, val gender: Gender, val address: Address)
                |
                |enum class GenderDto { FEMALE, MALE }
                |data class AddressDto(val street: String, val city: String)
                |data class PersonDto(val name: String, val gender: GenderDto, val address: AddressDto)
                |
                |fun main() {
                |  val person = Person(name="John Doe", gender=Gender.MALE, address = Address("Main Street", "Hamburg"))
                |  val dto:PersonDto = person.mapper()
                |  println(dto)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("PersonDto(name=John Doe, gender=MALE, address=AddressDto(street=Main Street, city=Hamburg))"),
                    "Expected PersonDto(name=John Doe, gender=MALE, address=AddressDto(street=Main Street, city=Hamburg)) in output"
                )
            }
    }

    @Test
    fun shouldFail_notEqualEnums() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |enum class Gender { MALE, FEMALE }
                |data class Address(val street: String, val city: String)
                |data class Person(val name: String, val gender: Gender, val address: Address)
                |
                |enum class GenderDto { FEMALE, MALE, X }
                |data class AddressDto(val street: String, val city: String)
                |data class PersonDto(val name: String, val gender: GenderDto, val address: AddressDto)
                |
                |fun main() {
                |  val person = Person(name="John Doe", gender=Gender.MALE, address = Address("Main Street", "Hamburg"))
                |  val dto:PersonDto = person.mapper()
                |  println(dto)
                |}
                |
                """.trimMargin()
            }
            .compileFail { output ->
                assertTrue(
                    output.contains("Missing mapping for: gender"),
                    "Expected Missing mapping for: gender in output"
                )
            }
    }

    @Test
    fun shouldSuccess_nestedEnumMapping() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |enum class Status { OLD, NEW }
                |data class Address(val street: String, val city: String, val status: Status)
                |data class Person(val name: String, val address: Address)
                |
                |enum class StatusDto { OLD, NEW }
                |data class AddressDto(val street: String, val city: String, val status: StatusDto)
                |data class PersonDto(val name: String, val address: AddressDto)
                |
                |fun main() {
                |  val person = Person(name="John Doe", address = Address("Main Street", "Hamburg", Status.NEW))
                |  val dto:PersonDto = person.mapper()
                |  println(dto)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess{ output ->
                assertTrue(
                    output.contains("PersonDto(name=John Doe, address=AddressDto(street=Main Street, city=Hamburg, status=NEW))"),
                    "PersonDto(name=John Doe, address=AddressDto(street=Main Street, city=Hamburg, status=NEW))"
                )
            }
    }
}
