package community.flock.kmapper.benchmarks

import community.flock.kmapper.mapper

/**
 * The simple workload: a flat data class mapped to another flat data class
 * with one derived field. This is the canonical kmapper README example and
 * the smallest mapping that exercises the DSL block.
 */
data class SimpleUser(
    val firstName: String,
    val lastName: String,
    val age: Int,
)

data class SimpleUserDto(
    val name: String,
    val age: Int,
)

fun SimpleUser.toDtoKMapper(): SimpleUserDto = mapper {
    name = "${it.firstName} ${it.lastName}"
}

fun SimpleUser.toDtoManual(): SimpleUserDto = SimpleUserDto(
    name = "$firstName $lastName",
    age = age,
)
