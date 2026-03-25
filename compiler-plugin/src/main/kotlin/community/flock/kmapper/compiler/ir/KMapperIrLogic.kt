package community.flock.kmapper.compiler.ir

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds

data class Shape(
    val type: IrType,
    val constructor: IrConstructor?,
    val fields: List<Field>
)

val IR_WIDENING_TABLE: Map<ClassId, Set<ClassId>> = mapOf(
    StandardClassIds.Byte to setOf(StandardClassIds.Short, StandardClassIds.Int, StandardClassIds.Long, StandardClassIds.Float, StandardClassIds.Double),
    StandardClassIds.Short to setOf(StandardClassIds.Int, StandardClassIds.Long, StandardClassIds.Float, StandardClassIds.Double),
    StandardClassIds.Int to setOf(StandardClassIds.Long, StandardClassIds.Float, StandardClassIds.Double),
    StandardClassIds.Long to setOf(StandardClassIds.Float, StandardClassIds.Double),
    StandardClassIds.Float to setOf(StandardClassIds.Double),
)

fun isWideningAllowed(fromType: IrType, toType: IrType): Boolean {
    val fromClassId = fromType.makeNotNull().classOrNull?.owner?.classId ?: return false
    val toClassId = toType.makeNotNull().classOrNull?.owner?.classId ?: return false
    return IR_WIDENING_TABLE[fromClassId]?.contains(toClassId) == true
}

fun IrType.isValueClass(): Boolean =
    classOrNull?.owner?.isValue == true

fun IrType.valueClassInnerType(): IrType? {
    val irClass = classOrNull?.owner ?: return null
    if (!irClass.isValue) return null
    val constructor = irClass.constructors.firstOrNull() ?: return null
    val params = constructor.parameters.filter { it.kind == IrParameterKind.Regular }
    if (params.size != 1) return null
    return params.first().type
}

// Note: `this` is the source field (from readableProperties), `other` is the target field (constructor param).
// Widening direction: this.type -> other.type. This asymmetry is intentional.
data class Field(val name: Name, val type: IrType) {
    override fun equals(other: Any?): Boolean {
        if (other !is Field) return false
        if (name != other.name) return false
        val thisType = type.makeNotNull()
        val otherType = other.type.makeNotNull()
        if (thisType == otherType) return true
        if (isWideningAllowed(fromType = type, toType = other.type)) return true
        // Unwrap: source is value class, target is inner type
        if (type.isValueClass() && type.valueClassInnerType()?.makeNotNull() == otherType) return true
        // Wrap: target is value class, source is inner type
        if (other.type.isValueClass() && other.type.valueClassInnerType()?.makeNotNull() == thisType) return true
        // Value-to-value: both value classes with same inner type
        if (type.isValueClass() && other.type.isValueClass()) {
            val thisInner = type.valueClassInnerType()?.makeNotNull()
            val otherInner = other.type.valueClassInnerType()?.makeNotNull()
            if (thisInner != null && thisInner == otherInner) return true
        }
        return false
    }

    override fun hashCode(): Int = name.hashCode()
}

fun Shape.isEnum() = type.classOrNull?.owner?.kind == ClassKind.ENUM_CLASS
