package community.flock.kmapper.compiler.ir

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrPropertyReference
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.FqName

/**
 * IR visitor that generates method bodies for @KMapper annotated functions
 */
class MapperIrVisitor(
    private val context: IrPluginContext,
    private val collector: MessageCollector
) : IrElementTransformerVoid() {

    private fun info(message: String) = collector.report(CompilerMessageSeverity.INFO, "[MapperIrVisitor] $message")

    companion object {
        val KMAPPER_ANNOTATION_FQN = FqName("community.flock.kmapper.KMapper")
    }

    override fun visitCall(expression: IrCall): IrExpression {
        val transformedCall = super.visitCall(expression)
        val function = expression.symbol.owner
        if (function.name.asString() == "mapper" && function.hasAnnotation(KMAPPER_ANNOTATION_FQN)) {
            info("Found call to @KMapper mapper function - replacing with implementation")
            return createMapperImplementationFromCall(expression)
        }

        return transformedCall
    }

    @OptIn(ObsoleteDescriptorBasedAPI::class)
    private fun createMapperImplementationFromCall(expression: IrCall): IrExpression {
        info("Creating mapper implementation from call")

        val builder = DeclarationIrBuilder(context, expression.symbol)

        val callArgument = expression.arguments.getOrNull(1) as? IrFunctionExpression ?: error("No argument found")

        // Get the type arguments from the mapper call
        val typeArgument = expression.typeArguments[0] ?: error("Could not resolve target type for mapper")
        val typeArgumentClass = typeArgument.classOrNull?.owner ?: error("Could not resolve target class for mapper")
        val typeArgumentConstructor =
            typeArgumentClass.constructors.firstOrNull() ?: error("No primary constructor found for type argument")
        val fields = typeArgumentConstructor.parameters.map { it.name }

        // Get body from callArgument and filter mapping fields
        val functionBody = callArgument.function.body as? IrBlockBody ?: error("Function body is not a BLOCK_BODY")
        val callExpressions = functionBody.statements.filterIsInstance<IrCall>()
        val mapping = callExpressions.associate { call ->
            val field = call.arguments[1] as? IrPropertyReference ?: error("No name argument found")
            val name = field.symbol.descriptor.name
            val expression = call.arguments[2] ?: error("No expression argument found")
            name to expression
        }

        return builder.irCallConstructor(typeArgumentConstructor.symbol, listOf(typeArgument))
            .apply {
                fields.forEachIndexed { index, name ->
                    arguments[index] = mapping[name]
                }
            }
    }
}