package community.flock.kmapper.compiler.ir

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.dump
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

    companion object {
        val KMAPPER_ANNOTATION_FQN = FqName("community.flock.kmapper.KMapper")
    }

    override fun visitElement(element: IrElement): IrElement {
        return super.visitElement(element)
    }

    override fun visitCall(expression: IrCall): IrExpression {
        val transformedCall = super.visitCall(expression)

        val function = expression.symbol.owner

        if (function.name.asString() == "mapper" && function.hasAnnotation(KMAPPER_ANNOTATION_FQN)) {
            collector.report(
                CompilerMessageSeverity.INFO,
                "[MapperIrVisitor] Found call to @KMapper mapper function - replacing with implementation"
            )

            // Replace the mapper call with actual object construction
            return createMapperImplementationFromCall(expression)
        }

        return transformedCall
    }

    private fun createMapperImplementationFromCall(expression: IrCall): IrExpression {
        collector.report(
            CompilerMessageSeverity.INFO,
            "[MapperIrVisitor] Creating mapper implementation from call"
        )

        val builder = DeclarationIrBuilder(context, expression.symbol)

        // Get the type arguments from the mapper call
        val typeArguments = expression.typeArguments
        if (typeArguments.isEmpty() || typeArguments.size != 1) {
            collector.report(
                CompilerMessageSeverity.WARNING,
                "[MapperIrVisitor] Could not find type argument"
            )
        }
        val targetType = typeArguments[0]
        val targetClass = targetType?.classOrNull?.owner

        collector.report(
            CompilerMessageSeverity.INFO,
            "[MapperIrVisitor] Creating instance of: ${targetClass?.name}"
        )

        if (targetClass == null) {
            collector.report(
                CompilerMessageSeverity.WARNING,
                "[MapperIrVisitor] Could not resolve target class for mapper"
            )
        }

        // Find the primary constructor
        val constructor = targetClass?.constructors?.firstOrNull()
        if (constructor == null) {
            collector.report(
                CompilerMessageSeverity.WARNING,
                "[MapperIrVisitor] No constructor found for mapper target type"
            )
        }

        val constructorArgument = expression.arguments.getOrNull(1) as? IrFunctionExpression ?: error("No argument found")

        collector.report(
            CompilerMessageSeverity.INFO,
            constructorArgument.function.dump()
        )

        return builder.irCallConstructor(constructor!!.symbol, listOf(targetType)).apply {
            arguments[0] = builder.irString("Hello")
            arguments[1] = builder.irInt(0)
        }

    }
}