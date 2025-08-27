package community.flock.kmapper.compiler.ir

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.name.FqName

/**
 * IR visitor that generates method bodies for FIR-generated to() methods
 */
class SecondIrVisitor(
    private val context: IrPluginContext,
    private val collector: MessageCollector
) : IrVisitorVoid() {

    private val FLOCK_ANNOTATION_FQN = FqName("community.flock.kmapper.Flock")

    override fun visitElement(element: IrElement) {
        element.acceptChildrenVoid(this)
    }

    override fun visitSimpleFunction(declaration: IrSimpleFunction) {
        super.visitSimpleFunction(declaration)

        // Check if this is a to() method that needs a body
        if (declaration.name.asString() == "second" &&
            declaration.body == null &&
            declaration.parent is IrClass

        ) {
            declaration.parameters.forEach {
                collector.report(CompilerMessageSeverity.INFO,"[FlockIrVisitor] Parameter: ${it.name}")
            }
            val parentClass = declaration.parent as IrClass
            if (parentClass.hasAnnotation(FLOCK_ANNOTATION_FQN)) {
                generateFlockMethodBody(declaration, parentClass)
            }
        }
    }

    private fun generateFlockMethodBody(
        function: IrSimpleFunction,
        parentClass: IrClass
    ) {
        val builder = DeclarationIrBuilder(context, function.symbol)

        val typeParam = function.typeParameters.first()
        collector.report(
            CompilerMessageSeverity.INFO,
            "[FlockIrVisitor] typeParam: ${typeParam}"
        )

        // Construct new instance of typeParam
        val typeParamType = typeParam.defaultType
        collector.report(
            CompilerMessageSeverity.INFO,
            "[FlockIrVisitor] Constructing instance of type: ${typeParamType}"
        )

        if (typeParam.isReified) {
            collector.report(
                CompilerMessageSeverity.INFO,
                "[FlockIrVisitor] is reified"
            )
            function.body = builder.irBlockBody {
                val typeParamClass = typeParam.defaultType.classOrNull?.owner
                collector.report(
                    CompilerMessageSeverity.INFO,
                    "[FlockIrVisitor] typeParamClass ${typeParamClass}"
                )

                if (typeParamClass != null) {
                    // Find the primary constructor
                    val constructor = typeParamClass.constructors.first()

                    // Create a call to the constructor
                    val irCall = irCall(constructor)

                    // Return the new instance
                    +irReturn(irCall)
                } else {
                    // Fallback or error handling if the class symbol cannot be resolved
                    +irReturn(
                        irString("FLOCK ${parentClass.name}")
                    )
                }
            }
        } else {
            // Fallback for non-reified types
            function.body = builder.irBlockBody {
                +irReturn(
                    irString("FLOCK ${parentClass.name}")
                )
            }
        }




        println("[DEBUG] Successfully generated body for to() method in ${parentClass.name}")

    }
}