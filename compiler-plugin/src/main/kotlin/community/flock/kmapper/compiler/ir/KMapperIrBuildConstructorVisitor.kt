package community.flock.kmapper.compiler.ir

import community.flock.kmapper.compiler.util.MessageCollectorUtil.info
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.builders.IrBuilder
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrProperty
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
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class KMapperIrBuildConstructorVisitor(
    private val context: IrPluginContext,
    private val collector: MessageCollector
) : IrElementTransformerVoid() {

    data class Shape(val constructor: IrConstructor, val fields: List<Field>)
    data class Field(val name: Name, val type: IrType)

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

        val definedMapping = callArgument?.function
            ?.body.let { it as? IrBlockBody }
            ?.statements?.filterIsInstance<IrCall>().orEmpty()
            .associate { call ->
                val field = call.arguments[1] as? IrPropertyReference ?: error("No name argument found")
                val name = field.symbol.owner.name
                val expression = call.arguments[2] ?: error("No expression argument found")
                name to expression
            }

        val toTypeArgument = expression.typeArguments[0] ?: error("Could not resolve target type for mapper")
        val fromTypeArgument = expression.typeArguments[1] ?: error("Could not resolve source type for mapper")


        val remapper = object : IrElementTransformerVoid() {
            override fun visitGetValue(expression: IrGetValue): IrExpression {
                val e = super.visitGetValue(expression)
                val itParamSymbol = callArgument?.function?.parameters
                    ?.firstOrNull { it.kind == IrParameterKind.Regular || it.kind == IrParameterKind.Context }
                    ?.symbol
                if (expression.symbol == itParamSymbol) {
                    return receiverArgument
                }
                return e
            }
        }

        val toShape = toTypeArgument.convertShape()

        val constructorCall = builder.irCallConstructor(toShape.constructor.symbol, listOf(toTypeArgument)).apply {
            toShape.fields.onEachIndexed { index, field ->
                val mappedValue = when {
                    field in fromTypeArgument.convertShape().fields -> builder.irGetPropertyByName(
                        receiver = receiverArgument,
                        propertyName = field.name
                    )

                    else -> definedMapping[field.name]
                }
                if (mappedValue == null) {
                    val receiver = builder.irGetPropertyByName(receiver = receiverArgument, propertyName = field.name)
                    arguments[index] =
                        builder.recursiveConstructor(
                            receiver,
                            field.type.convertShape(),
                            receiver.type.convertShape()
                        )
                } else {
                    arguments[index] = mappedValue.transform(remapper, null)
                }
            }
        }

        return constructorCall
    }

    private fun IrType.convertShape(): Shape {
        val typeArgumentClass = classOrNull?.owner ?: error("Could not resolve target class for mapper")
        val typeArgumentConstructor =
            typeArgumentClass.constructors.firstOrNull() ?: error("No primary constructor found for type argument")
        return Shape(
            typeArgumentConstructor,
            typeArgumentConstructor.parameters.map { Field(it.name, it.type) })
    }

    private fun IrBuilder.irGetPropertyByName(receiver: IrExpression, propertyName: Name): IrExpression {
        val receiverClass = receiver.type.classOrNull?.owner
            ?: error("Receiver type has no class owner for property $propertyName")

        val property: IrProperty = receiverClass.declarations
            .filterIsInstance<IrProperty>()
            .firstOrNull { it.name == propertyName }
            ?: error("Property '$propertyName' not found on ${receiverClass.name}")

        val getter = property.getter
            ?: error("Property '$propertyName' has no getter")

        return irCall(getter.symbol).apply {
            dispatchReceiver = receiver
        }
    }

    private fun IrBuilder.recursiveConstructor(
        expression: IrExpression,
        toShape: Shape,
        fromShape: Shape
    ): IrExpression = irCallConstructor(toShape.constructor.symbol, listOf(expression.type)).apply {
        toShape.fields.onEachIndexed { index, (name, type) ->
            val property = irGetPropertyByName(expression, name)
            if (type.makeNotNull().run { isPrimitiveType() || isString() }) {
                arguments[index] = property
            } else {
                arguments[index] = recursiveConstructor(
                    property,
                    toShape = toShape.fields[index].type.convertShape(),
                    fromShape = fromShape.fields[index].type.convertShape()
                )
            }
        }
    }
}