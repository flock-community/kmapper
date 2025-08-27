package community.flock.kmapper

import kotlin.reflect.KProperty0
import kotlin.reflect.KProperty1

@Target(AnnotationTarget.FUNCTION)
annotation class KMapper

fun generated(): Nothing = error("Mapper was not generated")

@KMapper
inline fun <reified TO> Any.mapper(mapper: (Mapper<TO>.() -> Unit)) = {}

class Mapper<TO>() {
    val to: TO get() = error("For context")
    inline infix fun <reified T> KProperty0<T>.map(value: T) {}
    inline infix fun <reified T> KProperty1<*, T>.map(value: T) {}
}