package community.flock.kmapper

@Target(AnnotationTarget.FUNCTION)
annotation class KMapper

fun generated(): Nothing = error("Mapper was not generated")

@KMapper
fun <TO, FROM> FROM.mapper(block: (TO.(it: FROM) -> Unit)? = null): TO = generated()

fun <T> T.ignore() {}
