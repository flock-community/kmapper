import community.flock.kmapper.mapper

data class Person(val id: Int, val firstName: String, val lastName: String)
data class PersonDto(val id: Int, val name: String)

fun box() {
    val person = Person(1, "John", "Doe")
    val dto:PersonDto = person.mapper {
        to::name map "${it.firstName} ${it.lastName}"
    }
}
