package community.flock.kmapper

@Target(AnnotationTarget.FUNCTION)
annotation class KMapper

@Target(AnnotationTarget.FUNCTION)
annotation class KMapperInternal

fun generated(): Nothing = error("Mapper was not generated")

@KMapper
fun <TO, FROM> FROM.mapper(block: (TO.(it: FROM) -> Unit)? = null): TO = generated()

fun <T> T.ignore() {}

@KMapperInternal
fun __mapField(name: String, value: Any?) {}
