package test

// Define the annotation
@Target(AnnotationTarget.CLASS)
annotation class Flock

@Flock
class User {
    override fun toString(): String = "User"
}

fun main() {
    val user = User()
    println(user.flock()) // This should cause "Unresolved reference 'flock'" error
}