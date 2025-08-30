package community.flock.kmapper.compiler.ir

import community.flock.kmapper.compiler.util.MessageCollectorUtil.info
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.builders.IrBlockBuilder
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irTemporary
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
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class KMapperIrBuildConstructorVisitor(
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

        val callArgument = expression.arguments.getOrNull(1) as? IrFunctionExpression

        val typeArgument = expression.typeArguments[0] ?: error("Could not resolve target type for mapper")

        val fromFields = expression.typeArguments[1]?.extractFields() ?: emptyMap()

        val definedMapping = callArgument?.function
            ?.body.let { it as? IrBlockBody }
            ?.statements?.filterIsInstance<IrCall>().orEmpty()
            .associate { call ->
                val field = call.arguments[1] as? IrPropertyReference ?: error("No name argument found")
                val name = field.symbol.owner.name
                val expression = call.arguments[2] ?: error("No expression argument found")
                name to expression
            }

        val receiverExpr = expression.arguments[0] ?: error("No extension receiver found for mapper call")
        val itParamSymbol = callArgument?.function?.parameters
            ?.firstOrNull { it.kind == IrParameterKind.Regular || it.kind == IrParameterKind.Context }
            ?.symbol

        return builder.irBlock {

            val itTemp = irTemporary(receiverExpr, nameHint = "it")

            val remapper = object : IrElementTransformerVoid() {
                override fun visitGetValue(expression: IrGetValue): IrExpression {
                    val e = super.visitGetValue(expression)
                    if (expression.symbol == itParamSymbol) {
                        return builder.irGet(itTemp)
                    }
                    return e
                }
            }

            val typeArgumentClass = typeArgument.classOrNull?.owner
                ?: error("Could not resolve target class for mapper")

            val typeArgumentConstructor = typeArgumentClass.constructors.firstOrNull()
                ?: error("No primary constructor found for type argument")

            val toFields = typeArgumentConstructor.parameters.associate { it.name to it.type }

            val constructorCall = irCallConstructor(typeArgumentConstructor.symbol, listOf(typeArgument)).apply {
                toFields.onEachIndexed { index, entry ->
                    val mappedValue = when {
                        entry in fromFields.entries -> irGetPropertyByName(
                            receiver = builder.irGet(itTemp),
                            propertyName = entry.key
                        )

                        else -> definedMapping[entry.key]
                    }
                    if (mappedValue == null) {
                        val receiver = irGetPropertyByName(receiver = builder.irGet(itTemp), propertyName = entry.key)
                        arguments[index] = recursiveConstructor(entry.value, receiver)
                    } else {
                        arguments[index] = mappedValue.transform(remapper, null)
                    }
                }
            }
            +builder.irGet(itTemp)
            +constructorCall
        }
    }

    private fun IrType.extractFields(): Map<Name, IrType> {
        val typeArgumentClass = classOrNull?.owner ?: error("Could not resolve target class for mapper")
        val typeArgumentConstructor =
            typeArgumentClass.constructors.firstOrNull() ?: error("No primary constructor found for type argument")
        return typeArgumentConstructor.parameters.associate { it.name to it.type }
    }

    private fun IrBlockBuilder.irGetPropertyByName(receiver: IrExpression, propertyName: Name): IrExpression {
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

    private fun IrBlockBuilder.recursiveConstructor(toType: IrType, fromExpression: IrExpression): IrExpression {

        val typeArgumentClass = toType.classOrNull?.owner ?: error("Could not resolve target class for mapper")
        val typeArgumentConstructor = typeArgumentClass.constructors.firstOrNull() ?: error("No primary constructor")

        return irCallConstructor(typeArgumentConstructor.symbol, listOf(toType)).apply {
            fromExpression.type.extractFields().onEachIndexed { index, (key) ->
                arguments[index] = irGetPropertyByName(fromExpression, key)
            }
        }
    }
}