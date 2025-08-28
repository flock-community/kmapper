@file:Suppress("DEPRECATION", "DEPRECATION_ERROR")

package community.flock.kmapper.compiler

import community.flock.kmapper.compiler.ir.MapperIrVisitor
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

/**
 * IR extension that generates to() method for classes annotated with @Flock
 */
class MapperExtension(val collector: MessageCollector) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        moduleFragment.transformChildrenVoid(MapperIrVisitor(pluginContext, collector))
    }
}
