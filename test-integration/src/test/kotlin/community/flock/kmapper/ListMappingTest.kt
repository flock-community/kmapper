package community.flock.kmapper

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ListMappingTest {

    val options = IntegrationTest.Options(
        kotlinVersion = "2.3.10",
    )

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
                    output.contains("UserDto(id=1, accounts=[AccountDto(name=John Doe)])"),
                    "Expected UserDto(id=1, accounts=[AccountDto(name=John Doe)]) in output"
                )
            }
    }

    @Test
    fun shouldCompile_valueClassUnwrapInList() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |@JvmInline
                |value class Id(val id: Int)
                |data class SourceItem(val id: Id, val label: String)
                |data class Source(val items: List<SourceItem>, val name: String)
                |
                |data class TargetItem(val id: Int, val label: String)
                |data class Target(val items: List<TargetItem>, val name: String)
                |
                |fun main() {
                |  val source = Source(items=listOf(SourceItem(Id(1), "a"), SourceItem(Id(2), "b")), name="test")
                |  val target:Target = source.mapper()
                |  println(target)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("Target(items=[TargetItem(id=1, label=a), TargetItem(id=2, label=b)], name=test)"),
                    "Expected Target with unwrapped value class items in list"
                )
            }
    }
}
