package community.flock.kmapper

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KMapperTest {

    val options = IntegrationTest.Options(
        kotlinVersion = "2.3.10",
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
                |    name = "${it.firstName} ${user.lastName}"
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
                |        name = "${it.firstName} ${it.lastName}"
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
                |        name = "${it.firstName} ${it.lastName}"
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
                |    name = "${user.firstName} ${user.lastName}"
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
    fun shouldSuccess_stringLists() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |data class Person(val id: String, val skills: List<String>)
                |data class PersonDto(val id: String, val skills: List<String>)
                |
                |fun main() {
                |  val person = Person("1", listOf("Kotlin", "Spring"))
                |  val personDto:PersonDto = person.mapper()
                |  println(personDto)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("PersonDto(id=1, skills=[Kotlin, Spring])"),
                    "PersonDto(id=1, skills=[Kotlin, Spring])"
                )
            }
    }

    @Test
    fun shouldFail_differentLists() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |data class Person(val id: String, val skills: List<String>)
                |data class PersonDto(val id: String, val skills: List<Int>)
                |
                |fun main() {
                |  val person = Person("1", listOf("Kotlin", "Spring"))
                |  val personDto:PersonDto = person.mapper()
                |  println(personDto)
                |}
                |
                """.trimMargin()
            }
            .compileFail { output ->
                assertTrue(
                    output.contains("Missing mapping for: skills."),
                    "Missing mapping for: skills."
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
    fun shouldCompile_unknownInFrom() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |data class Account(val name: String)
                |data class User(val id: Int, val account: Account)
                |
                |data class UserDto(val id: Int)
                |
                |fun main() {
                |  val user = User(id=1, account=Account(name="John Doe"))
                |  val res:UserDto = user.mapper()
                |  println(res)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("UserDto(id=1)"),
                    "Expected UserDto(id=1) in output"
                )
            }
    }

    @Test
    fun shouldCompile_complexList() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |data class Account(val name: String)
                |data class User(val id: Int, val accounts: List<Account>)
                
                |data class AccountDto(val name: String)
                |data class UserDto(val id: Int, val accounts: List<AccountDto>)
                |
                |fun main() {
                |  val user = User(id=1, accounts=listOf(Account(name="John Doe")))
                |  val res:UserDto = user.mapper()
                |  println(res)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("UserDto(id=1, accounts=[Account(name=John Doe)])"),
                    "Expected UserDto(id=1, accounts=[Account(name=John Doe)]) in output"
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

    @Test
    fun shouldSuccess_nullableFieldsFromNullable() {
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
    fun shouldCompile_extensionFunction() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |data class User(val firstName: String, val lastName: String, val age: Int)
                |data class UserDto(val name: String, val age: Int)
                |
                |fun User.toDto(): UserDto = mapper {
                |    name = "${it.firstName} ${it.lastName}"
                |}
                |
                |fun main() {
                |  val user = User("John", "Doe", 99)
                |  val userDto = user.toDto()
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
                |      require(value.contains("@")) { "Invalid email: ${'$'}value" }
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
                |      require(value.contains("@")) { "Invalid email: ${'$'}value" }
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
