package test

import community.flock.kmapper.*

data class User(val firstName: String, val lastName: String)
data class Id(val id: Int)
data class UserDto(val id: Id, val name: String, val age: Int)

fun main() {
    val user = User("John", "Doe")
    
    // This should work after implementing the mapper transformation
    val userDto = user.mapper<UserDto> {
        to::name map "${user.firstName} ${user.lastName}"
        to::age map 25
        to::id map Id(1)
    }
    
    println("UserDto: $userDto")
}