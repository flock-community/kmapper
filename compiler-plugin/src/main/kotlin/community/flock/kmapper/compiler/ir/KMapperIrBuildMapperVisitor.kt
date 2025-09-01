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
import org.jetbrains.kotlin.ir.expressions.IrPropertyReference
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.isPrimitiveType
import org.jetbrains.kotlin.ir.types.isString
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.hasAnnotation
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
            ?.statements?.filterIsInstance<IrCall>().orEmpty()
            .associate { call ->
                val callName = call.symbol.owner.name
                val field = call.arguments.getOrNull(1) as? IrPropertyReference ?: error("No name argument found")
                val fieldName = field.symbol.owner.name
                when (callName.identifier) {
                    "map" -> fieldName to call.arguments.getOrNull(2)
                    "ignore" -> fieldName to null
                    else -> error("Unknown mapping type: $callName")
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
                            ?.let {
                                builder.irGetPropertyByName(
                                    receiver = receiverArgument,
                                    propertyName = field.name
                                )
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
                        builder.construct(
                            expression = receiver,
                            toShape = field.type.convertShape(),
                            fromShape = receiver.type.convertShape()
                        )
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
        val typeArgumentConstructor =
            typeArgumentClass.constructors.firstOrNull() ?: error("No primary constructor found for type argument")
        return Shape(
            this,
            typeArgumentConstructor,
            typeArgumentConstructor.parameters.map { Field(it.name, it.type) })
    }

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

    private fun IrBuilder.construct(
        expression: IrExpression,
        toShape: Shape,
        fromShape: Shape
    ): IrExpression =

        // Construct Enum Class
        if (toShape.isEnum() && fromShape.isEnum()) {
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
                            type.run { isPrimitiveType() || isString() } -> property
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