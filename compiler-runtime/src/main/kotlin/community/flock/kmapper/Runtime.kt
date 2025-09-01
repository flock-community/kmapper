package community.flock.kmapper

import kotlin.reflect.KProperty0

@Target(AnnotationTarget.FUNCTION)
annotation class KMapper

fun generated(): Nothing = error("Mapper was not generated")

@KMapper
fun <TO, FROM> FROM.mapper(block: (Mapper<TO, FROM>.(it: FROM) -> Unit)? = null): TO = generated()

class Mapper<TO, FROM>() {
    val to: TO get() = error("For context")
    inline infix fun <reified T> KProperty0<T>.map(value: T) {}
    inline fun <reified T> KProperty0<T>.ignore() {}
}
