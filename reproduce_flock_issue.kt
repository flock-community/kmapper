import community.flock.kmapper.Flock

@Flock
class TestUser {
    override fun toString(): String = "TestUser"
}

fun main() {
    val user = TestUser()
    println(user.flock()) // This should cause "Unresolved reference 'flock'" error
}