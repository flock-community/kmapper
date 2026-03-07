// CHECK_BYTECODE_LISTING
import community.flock.kmapper.*

data class Person(val id: Int, val firstName: String, val lastName: String)
data class PersonDto(val id: Int, val name: String)

fun box(): String {
    val person = Person(1, "John", "Doe")
    val dto: PersonDto = person.mapper {
        name = "${it.firstName} ${it.lastName}"
    }
    return "OK"
}
