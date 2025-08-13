package community.flock.kmapper.compiler

import org.jetbrains.kotlin.DeprecatedForRemovalCompilerApi
import org.jetbrains.kotlin.backend.common.extensions.FirIncompatiblePluginAPI
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irConcat
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.addArgument
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.FqName

/**
 * IR extension that prefixes every kotlin.io.println call with "HELLO".
 */
class PrintlnPrefixIrExtension : IrGenerationExtension {
    @OptIn(FirIncompatiblePluginAPI::class, DeprecatedForRemovalCompilerApi::class)
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val printlnFqName = FqName("kotlin.io.println")
        val printlnSymbols = pluginContext.referenceFunctions(printlnFqName)
        val printlnOneArg = printlnSymbols.firstOrNull { it.owner.valueParameters.size == 1 }

        moduleFragment.transformChildrenVoid(object : IrElementTransformerVoid() {
            override fun visitCall(expression: IrCall): IrExpression {
                val call = super.visitCall(expression) as IrCall
                val owner = call.symbol.owner
                val fq = owner.fqNameWhenAvailable?.asString()
                if (fq != printlnFqName.asString()) return call

                val builder = DeclarationIrBuilder(
                    pluginContext,
                    call.symbol,
                    call.startOffset,
                    call.endOffset
                )
                val hello = builder.irString("HELLO")

                return when (call.valueArgumentsCount) {
                    0 -> {
                        // println() -> println("HELLO") using the 1-arg println overload
                        val target = printlnOneArg ?: call.symbol
                        builder.irCall(target).apply { putValueArgument(0, hello) }
                    }
                    else -> {
                        val arg0 = call.getValueArgument(0) ?: return call
                        val concat = builder.irConcat().apply {
                            addArgument(hello)
                            addArgument(arg0)
                        }
                        call.putValueArgument(0, concat)
                        call
                    }
                }
            }
        })
    }
}
