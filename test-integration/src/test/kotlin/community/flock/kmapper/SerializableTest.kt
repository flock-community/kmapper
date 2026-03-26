package community.flock.kmapper

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SerializableTest {

    val options = IntegrationTest.Options(
        kotlinVersion = "2.3.10",
        additionalPlugins = listOf("""kotlin("plugin.serialization") version "2.3.10""""),
        additionalDependencies = listOf("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1"),
    )

    @Test
    fun shouldCompile_autoMapWithSerializableTarget() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |import kotlinx.serialization.Serializable
                |
                |data class Pagination(
                |    val pageNumber: Int,
                |    val pageSize: Int,
                |    val totalElements: Long,
                |)
                |
                |@Serializable
                |data class PaginationDto(
                |    val pageNumber: Long,
                |    val pageSize: Long,
                |    val totalElements: Long,
                |)
                |
                |fun Pagination.toDto(): PaginationDto = mapper {
                |    pageNumber = it.pageNumber.toLong()
                |    pageSize = it.pageSize.toLong()
                |}
                |
                |fun main() {
                |    val pagination = Pagination(pageNumber = 1, pageSize = 10, totalElements = 100L)
                |    val dto = pagination.toDto()
                |    println(dto)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("PaginationDto(pageNumber=1, pageSize=10, totalElements=100)"),
                    "Expected PaginationDto(pageNumber=1, pageSize=10, totalElements=100) in output"
                )
            }
    }

    @Test
    fun shouldCompile_autoMapWithSerializableAndSealedInterface() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |import kotlinx.serialization.Serializable
                |import kotlinx.serialization.SerialName
                |
                |sealed interface Message {
                |    val sender: String
                |    val createdAt: String
                |    val category: String
                |
                |    data class Text(
                |        override val sender: String,
                |        override val createdAt: String,
                |        val text: String,
                |        override val category: String = "EXECUTION",
                |    ) : Message
                |}
                |
                |@Serializable
                |sealed interface MessageDto
                |
                |@Serializable
                |@SerialName("TextMessageDto")
                |data class TextMessageDto(
                |    val sender: String,
                |    val createdAt: String,
                |    val text: String,
                |) : MessageDto
                |
                |fun Message.Text.toDto(): TextMessageDto = mapper {
                |    sender = it.sender.uppercase()
                |    createdAt = it.createdAt
                |}
                |
                |fun main() {
                |    val message = Message.Text(sender = "agent", createdAt = "2025-01-01", text = "hello")
                |    val dto = message.toDto()
                |    println(dto)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("TextMessageDto(sender=AGENT, createdAt=2025-01-01, text=hello)"),
                    "Expected TextMessageDto(sender=AGENT, createdAt=2025-01-01, text=hello) in output"
                )
            }
    }

    @Test
    fun shouldCompile_allFieldsAutoMappedWithSerializable() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |import kotlinx.serialization.Serializable
                |
                |data class Result(val description: String, val response: String?)
                |
                |@Serializable
                |data class ResultDto(val description: String, val response: String?)
                |
                |fun Result.toDto(): ResultDto = mapper()
                |
                |fun main() {
                |    val result = Result(description = "done", response = "ok")
                |    val dto = result.toDto()
                |    println(dto)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("ResultDto(description=done, response=ok)"),
                    "Expected ResultDto(description=done, response=ok) in output"
                )
            }
    }
}
