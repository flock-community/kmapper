package community.flock.kmapper.compiler.fir

import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.FirEnumEntry
import org.jetbrains.kotlin.fir.declarations.utils.isClass
import org.jetbrains.kotlin.fir.declarations.utils.isEnumClass
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.isMarkedNullable
import org.jetbrains.kotlin.fir.types.isPrimitive
import org.jetbrains.kotlin.name.Name

data class Mapping(
    val to: Field,
    val from: Field
)

data class Field(
    val name: Name,
    val type: ConeKotlinType,
    val hasDefaultValue: Boolean,
    val fields: List<Field>
)

context(session: FirSession, collector: MessageCollector)
fun Mapping.deepEqual(): Boolean =
    from.name == to.name &&
        nullableEqual() &&
        (primaryEqual() || enumsEqual() || fieldsEqual())

context(session: FirSession, collector: MessageCollector)
private fun Mapping.nullableEqual(): Boolean {
    if (to.type.isMarkedNullable && !from.type.isMarkedNullable) return true
    return to.type.isMarkedNullable == from.type.isMarkedNullable
}

private fun Mapping.primaryEqual(): Boolean {
    if (!to.type.isPrimitive || !from.type.isPrimitive) return false
    if (to.type.isMarkedNullable != from.type.isMarkedNullable) return false
    if (to.type.isMarkedNullable != from.type.isMarkedNullable) return false
    return to.type == from.type
}

context(session: FirSession, collector: MessageCollector)
private fun Mapping.enumsEqual(): Boolean {
    val toEntries =
        to.type.toRegularClassSymbol(session)?.takeIf { it.isEnumClass }?.enumEntryNames()?.toSet() ?: return false
    val fromEntries = from.type.toRegularClassSymbol(session)?.takeIf { it.isEnumClass }?.enumEntryNames()?.toSet()
        ?: return false
    return toEntries == fromEntries
}

context(session: FirSession, collector: MessageCollector)
private fun Mapping.fieldsEqual(): Boolean {
    if (to.type.isPrimitive || from.type.isPrimitive) return false
    if (to.type.toRegularClassSymbol(session)?.isClass == false) return false
    if (from.type.toRegularClassSymbol(session)?.isClass == false) return false
    return from.fields.zip(to.fields).all { _ -> deepEqual() }
}

@OptIn(DirectDeclarationsAccess::class, SymbolInternals::class)
private fun FirRegularClassSymbol.enumEntryNames(): List<Name> =
    this.fir.declarations
        .filterIsInstance<FirEnumEntry>()
        .map { it.name }
