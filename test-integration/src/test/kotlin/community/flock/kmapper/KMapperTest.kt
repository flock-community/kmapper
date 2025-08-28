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
                |data class User(val firstName: String, val lastName: String)
                |data class Id(val id: Int)
                |data class UserDto(val name: String, val age: Int)
                |
                |fun main() {
                |  val user = User("John", "Doe")
                |  val userDto = user.mapper<UserDto> {
                |    to::name map "${user.firstName} ${user.lastName}"
                |    to::age map 3
                |  }
                |}
                |
                """.trimMargin()
            }
            .compile { output ->
                assertTrue(
                    output.contains("[KMapperPlugin] Compiler plugin registrar loaded"),
                    "Expected compiler plugin marker not found in output"
                )
            }
    }

    @Test
    fun shouldCompileError_missingParameterAge() {
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
                |  val userDto = user.mapper<UserDto> {
                |    to::name map "${user.firstName} ${user.lastName}"
                |  }
                |}
                |
                """.trimMargin()
            }
            .compile { output ->
                assertTrue(
                    output.contains("Missing constructor parameters in mapping: age."),
                    "Expected compiler Missing constructor parameters"
                )
            }
    }
}
