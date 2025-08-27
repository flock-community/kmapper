@file:Suppress("DEPRECATION", "DEPRECATION_ERROR")

package community.flock.kmapper.compiler

import community.flock.kmapper.compiler.ir.FlockIrVisitor
import community.flock.kmapper.compiler.ir.SecondIrVisitor
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * IR extension that generates to() method for classes annotated with @Flock
 */
class FlockExtension(val collector: MessageCollector) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        println("[DEBUG] FlockExtension IR generate called")
        
        val visitor = FlockIrVisitor(pluginContext, collector)
        moduleFragment.acceptChildrenVoid(visitor)

        val second = SecondIrVisitor(pluginContext, collector)
        moduleFragment.acceptChildrenVoid(second)
    }
}
