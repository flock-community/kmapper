@file:Suppress("DEPRECATION", "DEPRECATION_ERROR")

package community.flock.kmapper.compiler

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
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
 * IR extension that generates flock() method for classes annotated with @Flock
 */
class FlockExtension : IrGenerationExtension {
    
    private val FLOCK_ANNOTATION_FQN = FqName("community.flock.kmapper.Flock")

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        println("[DEBUG] FlockExtension IR generate called")
        
        val visitor = FlockIrVisitor(pluginContext)
        moduleFragment.acceptChildrenVoid(visitor)
    }

    private fun addFlockMethod(irClass: IrClass, pluginContext: IrPluginContext) {
        val stringType = pluginContext.irBuiltIns.stringType
        
        // Add function with proper body
        val function = irClass.addFunction {
            name = Name.identifier("flock")
            returnType = stringType
            visibility = DescriptorVisibilities.PUBLIC
            modality = Modality.FINAL
        }
        
        // Create function body using IR builders
        function.body = IrBlockBodyBuilder(
            pluginContext, 
            Scope(function.symbol), 
            function.startOffset, 
            function.endOffset
        ).irBlockBody {
            +irReturn(irString("FLOCK ${irClass.name}"))
        }
        
        println("[DEBUG] Successfully added flock() method with body to ${irClass.name}")
    }
}
