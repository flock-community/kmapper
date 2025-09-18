package community.flock.kmapper.compiler.fir

import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.FirEnumEntry
import org.jetbrains.kotlin.fir.declarations.constructors
import org.jetbrains.kotlin.fir.declarations.declaredProperties
import org.jetbrains.kotlin.fir.declarations.utils.isClass
import org.jetbrains.kotlin.fir.declarations.utils.isEnumClass
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeKotlinTypeProjection
import org.jetbrains.kotlin.fir.types.isMarkedNullable
import org.jetbrains.kotlin.fir.types.isPrimitive
import org.jetbrains.kotlin.name.Name

data class Field(
    val name: Name,
    val type: ConeKotlinType,
    val hasDefaultValue: Boolean,
    val fields: List<Field>
)

context(session: FirSession, collector: MessageCollector)
infix fun Field.deepEqual(other: Field): Boolean =
    name == other.name &&
        nullableEqual(this, other) &&
        (primaryEqual(this, other) || enumsEqual(this, other) || collectionsEqual(this, other) || fieldsEqual(this, other))

context(session: FirSession, collector: MessageCollector)
private fun nullableEqual(to: Field, from: Field): Boolean {
    if (to.type.isMarkedNullable && !from.type.isMarkedNullable) return true
    return to.type.isMarkedNullable == from.type.isMarkedNullable
}

private fun primaryEqual(to: Field, from: Field): Boolean {
    if (!to.type.isPrimitive || !from.type.isPrimitive) return false
    if (to.type.isMarkedNullable != from.type.isMarkedNullable) return false
    if (to.type.isMarkedNullable != from.type.isMarkedNullable) return false
    return to.type == from.type
}

context(session: FirSession, collector: MessageCollector)
private fun enumsEqual(to: Field, from: Field): Boolean {
    val toEntries =
        to.type.toRegularClassSymbol(session)?.takeIf { it.isEnumClass }?.enumEntryNames()?.toSet() ?: return false
    val fromEntries = from.type.toRegularClassSymbol(session)?.takeIf { it.isEnumClass }?.enumEntryNames()?.toSet()
        ?: return false
    return toEntries == fromEntries
}

context(session: FirSession, collector: MessageCollector)
private fun fieldsEqual(to: Field, from: Field): Boolean {
    if (to.type.isPrimitive || from.type.isPrimitive) return false
    if (to.type.toRegularClassSymbol(session)?.isClass == false) return false
    if (from.type.toRegularClassSymbol(session)?.isClass == false) return false
    return from.fields.zip(to.fields).all { (a, b) -> a deepEqual b }
}

context(session: FirSession, collector: MessageCollector)
private fun collectionsEqual(to: Field, from: Field): Boolean {
    val toClass = to.type.toRegularClassSymbol(session)?.classId
    val fromClass = from.type.toRegularClassSymbol(session)?.classId
    if (toClass != fromClass) return false

    val toElem = to.type.listElementType() ?: return true // if no type arg, be permissive
    val fromElem = from.type.listElementType() ?: return true

    // Build temporary fields for element comparison:
    val toElemField = Field(
        name = Name.identifier("") ,
        type = toElem,
        hasDefaultValue = false,
        fields = toElem.resolveConstructorFields()
    )
    val fromElemField = Field(
        name = Name.identifier("") ,
        type = fromElem,
        hasDefaultValue = false,
        fields = fromElem.resolvePropertyFields()
    )

    return toElemField deepEqual fromElemField
}

private fun ConeKotlinType.listElementType(): ConeKotlinType? {
    val classLike = this as? ConeClassLikeType ?: return null
    val first = classLike.typeArguments.getOrNull(0) as? ConeKotlinTypeProjection ?: return null
    return first.type
}

context(session: FirSession)
private fun ConeKotlinType.resolveConstructorFields(): List<Field> {
    val classSymbol = toRegularClassSymbol(session)
    val primaryConstructor = classSymbol?.constructors(session)?.firstOrNull()
    return primaryConstructor?.valueParameterSymbols?.map { parameter ->
        Field(
            name = parameter.name,
            type = parameter.resolvedReturnType,
            hasDefaultValue = parameter.hasDefaultValue,
            fields = parameter.resolvedReturnType.resolveConstructorFields()
        )
    }.orEmpty()
}

context(session: FirSession)
private fun ConeKotlinType.resolvePropertyFields(): List<Field> {
    val classSymbol = toRegularClassSymbol(session)
    return classSymbol?.declaredProperties(session)
        .orEmpty()
        .map { property ->
            Field(
                name = property.name,
                type = property.resolvedReturnType,
                hasDefaultValue = property.resolvedDefaultValue != null,
                fields = property.resolvedReturnType.resolvePropertyFields()
            )
        }
}

@OptIn(DirectDeclarationsAccess::class, SymbolInternals::class)
private fun FirRegularClassSymbol.enumEntryNames(): List<Name> =
    this.fir.declarations
        .filterIsInstance<FirEnumEntry>()
        .map { it.name }
