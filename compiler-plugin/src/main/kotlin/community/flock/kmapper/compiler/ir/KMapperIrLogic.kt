package community.flock.kmapper.compiler.ir

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.name.Name

data class Shape(
    val type: IrType,
    val constructor: IrConstructor,
    val fields: List<Field>
)

data class Field(val name: Name, val type: IrType) {
    override fun equals(other: Any?): Boolean {
        return other is Field && name == other.name && type.makeNotNull() == other.type.makeNotNull()
    }
}

fun Shape.isEnum() = type.classOrNull?.owner?.kind == ClassKind.ENUM_CLASS
