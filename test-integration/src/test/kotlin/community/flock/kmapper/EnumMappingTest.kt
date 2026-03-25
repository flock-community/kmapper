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
    fun shouldCompile_enumWithLabelProperty() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |enum class FieldType { TEXT, NUMBER }
                |data class FormField(val name: String, val type: FieldType)
                |
                |enum class EvaluationFieldType(val label: String) { TEXT("Text"), NUMBER("Number") }
                |data class EvaluationFormField(val name: String, val type: EvaluationFieldType)
                |
                |fun main() {
                |  val field = FormField(name="age", type=FieldType.NUMBER)
                |  val dto: EvaluationFormField = field.mapper()
                |  println(dto)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("EvaluationFormField(name=age, type=NUMBER)"),
                    "Expected EvaluationFormField(name=age, type=NUMBER) in output"
                )
            }
    }

    @Test
    fun shouldCompile_enumWithLabelPropertyReverse() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |enum class EvaluationFieldType(val label: String) { TEXT("Text"), NUMBER("Number") }
                |data class EvaluationFormField(val name: String, val type: EvaluationFieldType)
                |
                |enum class FieldType { TEXT, NUMBER }
                |data class FormField(val name: String, val type: FieldType)
                |
                |fun main() {
                |  val field = EvaluationFormField(name="age", type=EvaluationFieldType.NUMBER)
                |  val dto: FormField = field.mapper()
                |  println(dto)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("FormField(name=age, type=NUMBER)"),
                    "Expected FormField(name=age, type=NUMBER) in output"
                )
            }
    }

    @Test
    fun shouldCompile_directEnumWithLabelProperty() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |enum class MimeType { IMAGE_JPEG, IMAGE_PNG }
                |enum class MimeTypeDto(val label: String) { IMAGE_JPEG("IMAGE_JPEG"), IMAGE_PNG("IMAGE_PNG") }
                |
                |fun MimeType.toDto(): MimeTypeDto = mapper {}
                |
                |fun main() {
                |  val dto = MimeType.IMAGE_JPEG.toDto()
                |  println(dto)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("IMAGE_JPEG"),
                    "Expected IMAGE_JPEG in output"
                )
            }
    }

    @Test
    fun shouldCompile_directEnumWithLabelPropertyReverse() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |enum class MimeTypeDto(val label: String) { IMAGE_JPEG("IMAGE_JPEG"), IMAGE_PNG("IMAGE_PNG") }
                |enum class MimeType { IMAGE_JPEG, IMAGE_PNG }
                |
                |fun MimeTypeDto.toMimeType(): MimeType = mapper {}
                |
                |fun main() {
                |  val result = MimeTypeDto.IMAGE_JPEG.toMimeType()
                |  println(result)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("IMAGE_JPEG"),
                    "Expected IMAGE_JPEG in output"
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
