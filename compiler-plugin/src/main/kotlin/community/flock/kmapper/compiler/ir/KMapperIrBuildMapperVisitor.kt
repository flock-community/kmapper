@file:OptIn(ObsoleteDescriptorBasedAPI::class)

package community.flock.kmapper.compiler.ir

import community.flock.kmapper.compiler.util.MessageCollectorUtil.info
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.builders.IrBuilder
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrTypeOperatorCall
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.isPrimitiveType
import org.jetbrains.kotlin.ir.types.isString
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class KMapperIrBuildMapperVisitor(
    private val context: IrPluginContext,
    private val collector: MessageCollector
) : IrElementTransformerVoid() {

    companion object {
        val KMAPPER_ANNOTATION_FQN = FqName("community.flock.kmapper.KMapper")
    }

    override fun visitCall(expression: IrCall): IrExpression {
        val transformedCall = super.visitCall(expression)
        val function = expression.symbol.owner
        if (function.name.asString() == "mapper" && function.hasAnnotation(KMAPPER_ANNOTATION_FQN)) {
            return createMapperImplementationFromCall(expression)
        }

        return transformedCall
    }

    private fun createMapperImplementationFromCall(expression: IrCall): IrExpression {
        collector.info("Creating mapper implementation from call")
        val builder = DeclarationIrBuilder(context, expression.symbol)

        val receiverArgument = expression.arguments.getOrNull(0) ?: error("No extension receiver found for mapper call")
        val callArgument = expression.arguments.getOrNull(1) as? IrFunctionExpression

        val mapping = callArgument?.function
            ?.body.let { it as? IrBlockBody }
            ?.statements.orEmpty()
            // Unwrap IrTypeOperatorCall wrappers (e.g., coercion to Unit) to find the actual IrCall
            .map { stmt ->
                when (stmt) {
                    is IrTypeOperatorCall -> stmt.argument as? IrCall ?: stmt
                    else -> stmt
                }
            }
            .filterIsInstance<IrCall>()
            .associate { call ->
                val functionName = call.symbol.owner.name.identifier
                when (functionName) {
                    "to" -> {
                        // `to` is used as assignment marker: age.to(value)
                        // Extension receiver is the property getter call
                        // First regular parameter is the value
                        val extReceiverIndex = call.symbol.owner.parameters
                            .indexOfFirst { it.kind == IrParameterKind.ExtensionReceiver }
                            .takeIf { it >= 0 } ?: error("to must have an extension receiver parameter")
                        val getterCall = call.arguments[extReceiverIndex] as? IrCall
                            ?: error("to receiver must be a property getter call")
                        val fieldName = getterCall.symbol.owner.correspondingPropertySymbol?.owner?.name
                            ?: error("Cannot extract property name from to receiver")
                        val valueIndex = call.symbol.owner.parameters
                            .indexOfFirst { it.kind == IrParameterKind.Regular }
                            .takeIf { it >= 0 } ?: error("to must have a value parameter")
                        val valueExpr = call.arguments[valueIndex]
                        fieldName to valueExpr
                    }
                    "ignore" -> {
                        // Find the extension receiver index in the function's parameters
                        val extReceiverIndex = call.symbol.owner.parameters
                            .indexOfFirst { it.kind == IrParameterKind.ExtensionReceiver }
                            .takeIf { it >= 0 } ?: error("ignore must have an extension receiver parameter")
                        val getterCall = call.arguments[extReceiverIndex] as? IrCall
                            ?: error("ignore receiver must be a property getter call")
                        val fieldName = getterCall.symbol.owner.correspondingPropertySymbol?.owner?.name
                            ?: error("Cannot extract property name from ignore receiver")
                        fieldName to null
                    }
                    else -> error("Unknown mapping type in mapper lambda: $functionName")
                }
            }

        val toTypeArgument = expression.typeArguments[0] ?: error("Could not resolve target type for mapper")
        val fromTypeArgument = expression.typeArguments[1] ?: error("Could not resolve source type for mapper")

        val remapper = object : IrElementTransformerVoid() {
            override fun visitGetValue(expression: IrGetValue): IrExpression {
                val transformedGetValue = super.visitGetValue(expression)
                val itParamSymbol = callArgument?.function?.parameters
                    ?.firstOrNull { it.kind == IrParameterKind.Regular || it.kind == IrParameterKind.Context }
                    ?.symbol
                if (expression.symbol == itParamSymbol) {
                    return receiverArgument.deepCopyWithSymbols()
                }
                return transformedGetValue
            }
        }

        val toShape = toTypeArgument.convertShape()

        val constructorCall = builder.irCallConstructor(toShape.constructor.symbol, emptyList()).apply {
            toShape.fields.onEachIndexed { index, field ->

                val mappedValue =
                    if (mapping.containsKey(field.name)) {
                        mapping[field.name] ?: toShape.constructor.defaultValue(index)
                    } else {
                        fromTypeArgument.readableProperties()
                            .find { it == field }
                            ?.let { sourceField ->
                                val property = builder.irGetPropertyByName(
                                    receiver = receiverArgument,
                                    propertyName = field.name
                                )
                                when {
                                    // Widening: Int -> Long etc.
                                    property != null && isWideningAllowed(sourceField.type, field.type) ->
                                        builder.irWideningCall(property, field.type)
                                    // Unwrap: value class -> inner type
                                    property != null && sourceField.type.isValueClass() && !field.type.isValueClass() ->
                                        builder.irValueClassUnwrap(property, sourceField.type)
                                    // Wrap: inner type -> value class
                                    property != null && !sourceField.type.isValueClass() && field.type.isValueClass() ->
                                        builder.irValueClassWrap(property, field.type)
                                    // Value-to-value: different value classes with same inner type
                                    property != null && sourceField.type.isValueClass() && field.type.isValueClass()
                                        && sourceField.type.makeNotNull() != field.type.makeNotNull() ->
                                        builder.irValueClassWrap(
                                            builder.irValueClassUnwrap(property, sourceField.type),
                                            field.type
                                        )
                                    else -> property
                                }
                            }
                            ?: toShape.constructor.defaultValue(index)
                    }

                collector.info("mappedValue: $mappedValue")
                arguments[index] =
                    if (mappedValue == null) {
                        val receiver = builder.irGetPropertyByName(
                            receiver = receiverArgument,
                            propertyName = field.name
                        ) ?: error("Could not resolve property ${field.name}")
                        if (field.type.isKotlinList() && receiver.type.isKotlinList()) {
                            // For lists, pass through as-is when no explicit mapping is provided
                            receiver
                        } else {
                            builder.construct(
                                expression = receiver,
                                toShape = field.type.convertShape(),
                                fromShape = receiver.type.convertShape()
                            )
                        }
                    } else {
                        mappedValue.transform(remapper, null)
                    }
            }
        }

        return constructorCall
    }

    private fun IrConstructor.defaultValue(index: Int) = parameters[index].defaultValue?.expression

    private fun IrType.convertShape(): Shape {
        val typeArgumentClass = classOrNull?.owner ?: error("Could not resolve target class for mapper")
        // If this is a Kotlin List interface, it doesn't have a constructor and will be handled specially elsewhere
        if (typeArgumentClass.fqNameWhenAvailable?.asString() == "kotlin.collections.List") {
            error("List type should be handled without convertShape")
        }
        val typeArgumentConstructor =
            typeArgumentClass.constructors.firstOrNull() ?: error("No primary constructor found for type argument: ${typeArgumentClass.name}")
        return Shape(
            this,
            typeArgumentConstructor,
            typeArgumentConstructor.parameters.map { Field(it.name, it.type) })
    }

    private fun IrType.isKotlinList(): Boolean =
        classOrNull?.owner?.fqNameWhenAvailable?.asString() == "kotlin.collections.List"

    // Returns all readable properties (with a getter) as fields: used to detect available source fields.
    private fun IrType.readableProperties(): List<Field> {
        val typeArgumentClass = classOrNull?.owner ?: error("Could not resolve target class for mapper")
        return typeArgumentClass.declarations
            .filterIsInstance<IrProperty>()
            .filter { it.getter != null }
            .map { prop -> Field(prop.name, prop.getter!!.returnType) }
    }

    private fun IrBuilder.irGetPropertyByName(receiver: IrExpression, propertyName: Name): IrExpression? {
        val receiverClass = receiver.type.classOrNull?.owner
            ?: error("Receiver type has no class owner for property $propertyName")

        val property = receiverClass.declarations
            .filterIsInstance<IrProperty>()
            .firstOrNull { it.name == propertyName }
            ?.getter

        return property?.symbol
            ?.let {
                irCall(it).apply {
                    dispatchReceiver = receiver
                }
            }
    }

    private fun IrBuilder.irWideningCall(receiver: IrExpression, targetType: IrType): IrExpression {
        val targetClassId = targetType.makeNotNull().classOrNull?.owner?.classId
            ?: error("Cannot resolve target class for widening")
        val conversionName = "to${targetClassId.shortClassName.asString()}"
        val sourceClass = receiver.type.makeNotNull().classOrNull?.owner
            ?: error("Cannot resolve source class for widening")
        val conversionFunction = sourceClass.declarations
            .filterIsInstance<IrSimpleFunction>()
            .firstOrNull { it.name.asString() == conversionName }
            ?: error("No $conversionName function found on ${sourceClass.name}")
        return irCall(conversionFunction.symbol).apply {
            dispatchReceiver = receiver
        }
    }

    private fun IrBuilder.irValueClassUnwrap(receiver: IrExpression, valueClassType: IrType): IrExpression {
        val irClass = valueClassType.classOrNull?.owner ?: error("Cannot resolve value class")
        val property = irClass.declarations
            .filterIsInstance<IrProperty>()
            .firstOrNull { it.getter != null }
            ?: error("No property found in value class ${irClass.name}")
        return irCall(property.getter!!.symbol).apply {
            dispatchReceiver = receiver
        }
    }

    private fun IrBuilder.irValueClassWrap(receiver: IrExpression, targetType: IrType): IrExpression {
        val irClass = targetType.classOrNull?.owner ?: error("Cannot resolve value class for wrapping")
        val constructor = irClass.constructors.firstOrNull()
            ?: error("No constructor found for value class ${irClass.name}")
        return irCallConstructor(constructor.symbol, emptyList()).apply {
            arguments[0] = receiver
        }
    }

    private fun IrBuilder.construct(
        expression: IrExpression,
        toShape: Shape,
        fromShape: Shape
    ): IrExpression =

        // If both sides are kotlin.collections.List, pass through for now (primitive lists)
        if (toShape.type.isKotlinList() && fromShape.type.isKotlinList()) {
            // Note: Complex element mapping is handled elsewhere or falls back to direct assignment for identical element types
            expression

        // Construct Enum Class
        } else if (toShape.isEnum() && fromShape.isEnum()) {
            val valueOfFun = toShape.type.makeNotNull()
                .classOrNull?.owner?.declarations
                ?.filterIsInstance<IrSimpleFunction>()
                ?.firstOrNull { it.name.asString() == "valueOf" }
                ?: error("No valueOf function found for enum")
            val nameValue = irGetPropertyByName(receiver = expression, propertyName = Name.identifier("name"))
            irCall(valueOfFun.symbol).apply { arguments[0] = nameValue }

            // Construct Data Class
        } else {
            irCallConstructor(toShape.constructor.symbol, emptyList()).apply {
                collector.info("Mapping constructor: ${toShape}")
                toShape.fields.onEachIndexed { index, (name, type) ->
                    val property = irGetPropertyByName(expression, name) ?: error("Could not resolve property $name")
                    arguments[index] =
                        when {
                            type.run { isPrimitiveType() || isString() } -> {
                                when {
                                    // Unwrap value class to primitive
                                    property.type.isValueClass() && !type.isValueClass() ->
                                        irValueClassUnwrap(property, property.type)
                                    // Widening
                                    property.type.makeNotNull() != type.makeNotNull()
                                        && isWideningAllowed(property.type, type) ->
                                        irWideningCall(property, type)
                                    else -> property
                                }
                            }
                            // Wrap primitive into value class
                            type.isValueClass() && !property.type.isValueClass() ->
                                irValueClassWrap(property, type)
                            // Value-to-value: different value classes, same inner type
                            type.isValueClass() && property.type.isValueClass()
                                && type.makeNotNull() != property.type.makeNotNull() ->
                                irValueClassWrap(irValueClassUnwrap(property, property.type), type)
                            else -> construct(
                                property,
                                toShape = toShape.fields[index].type.convertShape(),
                                fromShape = fromShape.fields[index].type.convertShape()
                            )
                        }
                }
            }
        }
}