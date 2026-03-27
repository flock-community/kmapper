package community.flock.kmapper

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BasicMappingTest {

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
    fun shouldCompile_jdkTypes() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |import java.util.UUID
                |import java.time.Instant
                |
                |data class EntityWithJdkTypes(val id: UUID, val name: String, val createdAt: Instant)
                |data class DomainWithJdkTypes(val id: UUID, val name: String, val createdAt: Instant)
                |
                |fun main() {
                |  val entity = EntityWithJdkTypes(id = UUID.randomUUID(), name = "test", createdAt = Instant.now())
                |  val domain: DomainWithJdkTypes = entity.mapper()
                |  println(domain)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("DomainWithJdkTypes("),
                    "Expected DomainWithJdkTypes in output"
                )
            }
    }

    @Test
    fun shouldCompile_sourceWithPrivateConstructor() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |data class AnnotationProgress private constructor(
                |    val evaluationId: String,
                |    val totalInputs: Int,
                |    val annotatedInputs: Int,
                |) {
                |    companion object {
                |        operator fun invoke(evaluationId: String, totalInputs: Int, annotatedInputs: Int): AnnotationProgress =
                |            AnnotationProgress(evaluationId, totalInputs, annotatedInputs)
                |    }
                |}
                |
                |data class AnnotationProgressDto(val evaluationId: String, val totalInputs: Int, val annotatedInputs: Int)
                |
                |fun AnnotationProgress.toDto(): AnnotationProgressDto = mapper()
                |
                |fun main() {
                |    val progress = AnnotationProgress("eval-1", 10, 5)
                |    val dto = progress.toDto()
                |    println(dto)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("AnnotationProgressDto(evaluationId=eval-1, totalInputs=10, annotatedInputs=5)"),
                    "Expected AnnotationProgressDto(evaluationId=eval-1, totalInputs=10, annotatedInputs=5) in output"
                )
            }
    }

    @Test
    fun shouldCompile_sourceWithPrivateConstructorAndExplicitMapping() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |data class AnnotationProgress private constructor(
                |    val evaluationId: String,
                |    val totalInputs: Int,
                |    val annotatedInputs: Int,
                |) {
                |    companion object {
                |        operator fun invoke(evaluationId: String, totalInputs: Int, annotatedInputs: Int): AnnotationProgress =
                |            AnnotationProgress(evaluationId, totalInputs, annotatedInputs)
                |    }
                |}
                |
                |data class AnnotationProgressDto(val evalId: String, val total: Long, val annotated: Long)
                |
                |fun AnnotationProgress.toDto(): AnnotationProgressDto = mapper {
                |    evalId = it.evaluationId
                |    total = it.totalInputs.toLong()
                |    annotated = it.annotatedInputs.toLong()
                |}
                |
                |fun main() {
                |    val progress = AnnotationProgress("eval-1", 10, 5)
                |    val dto = progress.toDto()
                |    println(dto)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("AnnotationProgressDto(evalId=eval-1, total=10, annotated=5)"),
                    "Expected AnnotationProgressDto(evalId=eval-1, total=10, annotated=5) in output"
                )
            }
    }

    @Test
    fun shouldCompile_sourceWithNoConstructor() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |class Progress private constructor(
                |    val evaluationId: String,
                |    val totalInputs: Int,
                |    val annotatedInputs: Int,
                |) {
                |    companion object {
                |        fun create(evaluationId: String, totalInputs: Int, annotatedInputs: Int): Progress =
                |            Progress(evaluationId, totalInputs, annotatedInputs)
                |    }
                |}
                |
                |data class ProgressDto(val evaluationId: String, val totalInputs: Int, val annotatedInputs: Int)
                |
                |fun Progress.toDto(): ProgressDto = mapper()
                |
                |fun main() {
                |    val progress = Progress.create("eval-1", 10, 5)
                |    val dto = progress.toDto()
                |    println(dto)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("ProgressDto(evaluationId=eval-1, totalInputs=10, annotatedInputs=5)"),
                    "Expected ProgressDto(evaluationId=eval-1, totalInputs=10, annotatedInputs=5) in output"
                )
            }
    }

    @Test
    fun shouldCompile_sourceIsInterface() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |interface HasProgress {
                |    val evaluationId: String
                |    val totalInputs: Int
                |    val annotatedInputs: Int
                |}
                |
                |data class ProgressImpl(
                |    override val evaluationId: String,
                |    override val totalInputs: Int,
                |    override val annotatedInputs: Int,
                |) : HasProgress
                |
                |data class ProgressDto(val evaluationId: String, val totalInputs: Int, val annotatedInputs: Int)
                |
                |fun HasProgress.toDto(): ProgressDto = mapper()
                |
                |fun main() {
                |    val progress: HasProgress = ProgressImpl("eval-1", 10, 5)
                |    val dto = progress.toDto()
                |    println(dto)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("ProgressDto(evaluationId=eval-1, totalInputs=10, annotatedInputs=5)"),
                    "Expected ProgressDto(evaluationId=eval-1, totalInputs=10, annotatedInputs=5) in output"
                )
            }
    }

    @Test
    fun shouldCompile_autoMapSameNamePropertyInDslBlock() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
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
                |data class TextMessageDto(
                |    val sender: String,
                |    val createdAt: String,
                |    val text: String,
                |)
                |
                |fun Message.Text.toDto(): TextMessageDto = mapper {
                |    sender = it.sender.uppercase()
                |    createdAt = it.createdAt
                |    // text should be auto-mapped since it has the same name and type
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
    fun shouldCompile_autoMapWithSealedInterfaceTarget() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
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
                |sealed interface MessageDto
                |
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
    fun shouldCompile_autoMapWithSerializableTarget() {
        IntegrationTest(options)
            .apply {
                files.removeIf { it.name == "build.gradle.kts" }
                files.add(
                    IntegrationTest.File(
                        "", "build.gradle.kts",
                        """
                        |plugins {
                        |    id("community.flock.kmapper") version "0.0.0-SNAPSHOT"
                        |    kotlin("jvm") version "2.3.10"
                        |    kotlin("plugin.serialization") version "2.3.10"
                        |    application
                        |}
                        |repositories {
                        |  mavenCentral()
                        |  mavenLocal()
                        |}
                        |dependencies {
                        |    implementation(kotlin("stdlib"))
                        |    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
                        |}
                        |kotlin {
                        |  jvmToolchain(21)
                        |}
                        |application {
                        |  mainClass.set("sample.AppKt")
                        |}
                        """.trimMargin()
                    )
                )
            }
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
    fun shouldCompile_valueClassAutoMapInDslBlock() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |@JvmInline
                |value class ModelId(val value: String)
                |@JvmInline
                |value class TaskDesc(val value: String)
                |
                |data class Run(
                |    val id: String,
                |    val modelIdentifier: ModelId,
                |    val taskDescription: TaskDesc,
                |    val isViewed: Boolean,
                |)
                |
                |data class RunDto(
                |    val id: String,
                |    val modelIdentifier: String,
                |    val taskDescription: String,
                |    val isViewed: Boolean,
                |)
                |
                |fun Run.toDto(): RunDto = mapper {
                |    isViewed = it.isViewed
                |}
                |
                |fun main() {
                |    val run = Run(id = "1", modelIdentifier = ModelId("gpt-4"), taskDescription = TaskDesc("do stuff"), isViewed = true)
                |    val dto = run.toDto()
                |    println(dto)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("RunDto(id=1, modelIdentifier=gpt-4, taskDescription=do stuff, isViewed=true)"),
                    "Expected RunDto(id=1, modelIdentifier=gpt-4, taskDescription=do stuff, isViewed=true) in output but got: $output"
                )
            }
    }

    @Test
    fun shouldCompile_valueClassAutoMapInDslBlockWithSerializable() {
        IntegrationTest(
            options.copy(
                additionalPlugins = listOf("""kotlin("plugin.serialization") version "${options.kotlinVersion}""""),
                additionalDependencies = listOf("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1"),
            )
        )
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |import kotlinx.serialization.Serializable
                |import kotlinx.serialization.SerialName
                |
                |@JvmInline
                |value class ModelId(val value: String)
                |@JvmInline
                |value class TaskDesc(val value: String)
                |
                |data class Run(
                |    val id: String,
                |    val modelIdentifier: ModelId,
                |    val taskDescription: TaskDesc,
                |    val isViewed: Boolean,
                |)
                |
                |@Serializable
                |@SerialName("RunDto")
                |data class RunDto(
                |    val id: String,
                |    val modelIdentifier: String,
                |    val taskDescription: String,
                |    val isViewed: Boolean,
                |)
                |
                |fun Run.toDto(): RunDto = mapper {
                |    isViewed = it.isViewed
                |}
                |
                |fun main() {
                |    val run = Run(id = "1", modelIdentifier = ModelId("gpt-4"), taskDescription = TaskDesc("do stuff"), isViewed = true)
                |    val dto = run.toDto()
                |    println(dto)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("RunDto(id=1, modelIdentifier=gpt-4, taskDescription=do stuff, isViewed=true)"),
                    "Expected RunDto(id=1, modelIdentifier=gpt-4, taskDescription=do stuff, isViewed=true) in output but got: $output"
                )
            }
    }

    @Test
    fun shouldCompile_selfReferencingType() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |data class Category(val name: String, val parent: Category?)
                |data class CategoryDto(val name: String, val parent: Category?)
                |
                |fun main() {
                |    val category = Category("child", Category("root", null))
                |    val dto: CategoryDto = category.mapper()
                |    println(dto)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("CategoryDto(name=child, parent=Category(name=root, parent=null))"),
                    "Expected CategoryDto(name=child, parent=Category(name=root, parent=null)) in output"
                )
            }
    }

    @Test
    fun shouldCompile_nestedLambdaInMapperBlock() {
        IntegrationTest(options)
            .file("App.kt") {
                $$"""
                |package sample
                |
                |import community.flock.kmapper.mapper
                |
                |data class User(val firstName: String, val tags: List<String>)
                |data class UserDto(val name: String, val tags: List<String>)
                |
                |fun User.toDto(): UserDto = mapper {
                |    name = it.firstName
                |    tags = it.tags.map { tag -> tag.uppercase() }
                |}
                |
                |fun main() {
                |    val user = User("Alice", listOf("kotlin", "java"))
                |    val dto = user.toDto()
                |    println(dto)
                |}
                |
                """.trimMargin()
            }
            .compileSuccess { output ->
                assertTrue(
                    output.contains("UserDto(name=Alice, tags=[KOTLIN, JAVA])"),
                    "Expected UserDto(name=Alice, tags=[KOTLIN, JAVA]) in output"
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
}
