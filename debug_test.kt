package community.flock.kmapper

@Target(AnnotationTarget.CLASS)
annotation class Flock

@Flock
class User {
  override fun toString(): String = "User"
}

fun main() {
  val u = User()
  println(u.flock())
}