package community.flock.kmapper.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.name.FqName

/**
 * IR visitor that generates method bodies for FIR-generated to() methods
 */
class FlockIrVisitor(
    private val context: IrPluginContext
) : IrVisitorVoid() {
    
    private val FLOCK_ANNOTATION_FQN = FqName("community.flock.kmapper.Flock")
    
    override fun visitElement(element: IrElement) {
        element.acceptChildrenVoid(this)
    }
    
    override fun visitSimpleFunction(declaration: IrSimpleFunction) {
        super.visitSimpleFunction(declaration)
        
        // Check if this is a to() method that needs a body
        if (declaration.name.asString() == "to" &&
            declaration.body == null && 
            declaration.parent is IrClass) {
            
            val parentClass = declaration.parent as IrClass
            if (parentClass.hasAnnotation(FLOCK_ANNOTATION_FQN)) {
                println("[DEBUG] Generating body for to() method in ${parentClass.name}")
                generateFlockMethodBody(declaration, parentClass)
            }
        }
    }
    
    private fun generateFlockMethodBody(function: IrSimpleFunction, parentClass: IrClass) {
        val builder = DeclarationIrBuilder(context, function.symbol)
        
        function.body = builder.irBlockBody {
            +irReturn(irString("FLOCK ${parentClass.name}"))
        }
        
        println("[DEBUG] Successfully generated body for to() method in ${parentClass.name}")
    }
}