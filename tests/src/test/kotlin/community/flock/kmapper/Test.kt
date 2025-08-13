package community.flock.kmapper

import java.util.UUID

interface Domain


data class TestDomain(
    val name: String,
    val code: UUID?
):Domain

data class TestDTO(
    val name: String,
    val code: String?
){
    data class Builder(
        var name: String,
        var code: String?
    )

}


inline fun <reified T> TestDomain.produce(name: String, code: String? = null): T {
    return T::class.constructors.first().call(name, code) as T
}

fun main() {
    val domain = TestDomain("name", UUID.randomUUID())
    val dto = domain.produce<TestDTO.Builder> (
        name = "name",
        code = domain.code?.toString()
    )
    print(dto)
}