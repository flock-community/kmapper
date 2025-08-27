package debug

import community.flock.kmapper.*

data class TestUser(val name: String)

fun main() {
    println("Testing generated() call directly:")
    try {
        val result = generated()
        println("Generated returned: $result")
    } catch (e: Exception) {
        println("Generated threw: ${e.message}")
    }
    
    println("Testing mapper call:")
    try {
        val user = "test"
        val mapped = user.mapper<TestUser> {
            to::name map "Test Name"
        }
        println("Mapper returned: $mapped")
    } catch (e: Exception) {
        println("Mapper threw: ${e.message}")
    }
}