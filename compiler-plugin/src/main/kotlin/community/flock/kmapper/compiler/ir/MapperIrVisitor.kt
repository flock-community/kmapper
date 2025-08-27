package community.flock.kmapper.compiler.ir

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
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
        val functionName = function.name.asString()
        val functionFqName = function.symbol.signature.toString()
        
        collector.report(
            CompilerMessageSeverity.INFO,
            "[MapperIrVisitor] Visiting call: $functionName, FQN: $functionFqName"
        )
        
        // Log if this looks like a mapper-related call
        if (functionName.contains("mapper") || functionName.contains("generated") || 
            functionFqName.contains("community.flock.kmapper")) {
            collector.report(
                CompilerMessageSeverity.WARNING,
                "[MapperIrVisitor] *** MAPPER-RELATED CALL: $functionName, FQN: $functionFqName, hasAnnotation: ${function.hasAnnotation(KMAPPER_ANNOTATION_FQN)}"
            )
        }
        
        
        // Check if this is a call to the generated() function that needs to be replaced
        if (function.name.asString() == "generated" && functionFqName.contains("community.flock.kmapper")) {
            collector.report(
                CompilerMessageSeverity.INFO,
                "[MapperIrVisitor] Found call to generated() function - replacing with mapper logic. FQN: $functionFqName"
            )
            
            // Replace the generated() call with actual mapping logic
            return createMapperImplementation(expression)
        }
        
        // Check if this is a call to the mapper extension function
        if (function.name.asString() == "mapper" && 
            function.hasAnnotation(KMAPPER_ANNOTATION_FQN)
        ) {
            collector.report(
                CompilerMessageSeverity.INFO,
                "[MapperIrVisitor] Found call to @KMapper mapper function - replacing with implementation"
            )
            
            // Replace the mapper call with actual object construction
            return createMapperImplementationFromCall(expression)
        }
        
        return transformedCall
    }

    override fun visitSimpleFunction(declaration: IrSimpleFunction): IrStatement {
        collector.report(
            CompilerMessageSeverity.INFO,
            "[MapperIrVisitor] Visiting function: ${declaration.name}, hasAnnotation: ${declaration.hasAnnotation(KMAPPER_ANNOTATION_FQN)}, annotations: ${declaration.annotations.map { it.symbol.owner.name }}, body: ${declaration.body}"
        )
        
        // Check if this is the @KMapper annotated mapper function and replace its body
        if (declaration.name.asString() == "mapper" && 
            declaration.hasAnnotation(KMAPPER_ANNOTATION_FQN)
        ) {
            collector.report(
                CompilerMessageSeverity.INFO,
                "[MapperIrVisitor] Replacing body of @KMapper mapper function"
            )
            generateMapperMethodBody(declaration)
        }
        
        val transformedFunction = super.visitSimpleFunction(declaration)
        return transformedFunction
    }

    private fun generateMapperMethodBody(function: IrSimpleFunction) {
        val builder = DeclarationIrBuilder(context, function.symbol)

        // Get the reified type parameter TO
        val typeParam = function.typeParameters.firstOrNull()
        if (typeParam == null) {
            collector.report(
                CompilerMessageSeverity.ERROR,
                "[MapperIrVisitor] No type parameter found for mapper function"
            )
            return
        }

        collector.report(
            CompilerMessageSeverity.INFO,
            "[MapperIrVisitor] Processing type parameter: ${typeParam}"
        )

        if (typeParam.isReified) {
            function.body = builder.irBlockBody {
                val typeParamType = typeParam.defaultType
                val typeParamClass = typeParamType.classOrNull?.owner

                collector.report(
                    CompilerMessageSeverity.INFO,
                    "[MapperIrVisitor] Target type class: ${typeParamClass}"
                )

                if (typeParamClass != null) {
                    // Find the primary constructor
                    val constructor = typeParamClass.constructors.firstOrNull()
                    
                    if (constructor != null) {
                        // Create a call to the constructor - simplified version
                        // TODO: Implement proper mapping logic based on mapper lambda
                        val constructorCall = irCall(constructor)
                        +irReturn(constructorCall)
                    } else {
                        collector.report(
                            CompilerMessageSeverity.ERROR,
                            "[MapperIrVisitor] No constructor found for type: ${typeParamClass.name}"
                        )
                        +irReturn(irNull())
                    }
                } else {
                    collector.report(
                        CompilerMessageSeverity.ERROR,
                        "[MapperIrVisitor] Could not resolve class for type parameter"
                    )
                    +irReturn(irNull())
                }
            }
        } else {
            collector.report(
                CompilerMessageSeverity.ERROR,
                "[MapperIrVisitor] Type parameter must be reified"
            )
            function.body = builder.irBlockBody {
                +irReturn(irNull())
            }
        }

        collector.report(
            CompilerMessageSeverity.INFO,
            "[MapperIrVisitor] Successfully generated body for mapper function"
        )
    }

    private fun createMapperImplementation(expression: IrCall): IrExpression {
        collector.report(
            CompilerMessageSeverity.INFO,
            "[MapperIrVisitor] Creating mapper implementation to replace generated() call"
        )
        
        val builder = DeclarationIrBuilder(context, expression.symbol)
        
        // We need to find the containing mapper() function call to get the type parameter
        // For now, we'll try to extract type information from the context
        
        // The generated() call is inside a mapper<TO> call, so we need to get the reified type TO
        // This is a simplified implementation that creates a default constructor call
        
        collector.report(
            CompilerMessageSeverity.INFO,
            "[MapperIrVisitor] Attempting to create constructor call for target type"
        )
        
        // Try to get type information from the call site
        val returnType = expression.type
        val targetClass = returnType.classOrNull?.owner
        
        if (targetClass != null) {
            collector.report(
                CompilerMessageSeverity.INFO,
                "[MapperIrVisitor] Found target class: ${targetClass.name}"
            )
            
            // Find the primary constructor
            val constructor = targetClass.constructors.firstOrNull()
            
            if (constructor != null) {
                collector.report(
                    CompilerMessageSeverity.INFO,
                    "[MapperIrVisitor] Creating constructor call for: ${targetClass.name}"
                )
                
                // Create a call to the constructor
                val constructorCall = builder.irCall(constructor)
                
                // TODO: Populate constructor parameters based on mapper lambda
                // For now, this will create instances with default/null values
                
                return constructorCall
            } else {
                collector.report(
                    CompilerMessageSeverity.WARNING,
                    "[MapperIrVisitor] No constructor found for type: ${targetClass.name}, returning null"
                )
            }
        } else {
            collector.report(
                CompilerMessageSeverity.WARNING,
                "[MapperIrVisitor] Could not resolve target class from return type, returning null"
            )
        }
        
        return builder.irNull()
    }

    private fun createMapperImplementationFromCall(expression: IrCall): IrExpression {
        collector.report(
            CompilerMessageSeverity.INFO,
            "[MapperIrVisitor] Creating mapper implementation from call"
        )
        
        val builder = DeclarationIrBuilder(context, expression.symbol)
        
        // Get the type arguments from the mapper call
        val typeArguments = expression.typeArguments
        if (typeArguments.isNotEmpty()) {
            val targetType = typeArguments[0]
            if (targetType != null) {
                val targetClass = targetType.classOrNull?.owner
                
                collector.report(
                    CompilerMessageSeverity.INFO,
                    "[MapperIrVisitor] Creating instance of: ${targetClass?.name}"
                )
                
                if (targetClass != null) {
                    // Find the primary constructor
                    val constructor = targetClass.constructors.firstOrNull()
                    
                    if (constructor != null) {
                        collector.report(
                            CompilerMessageSeverity.INFO,
                            "[MapperIrVisitor] Creating constructor call for mapper"
                        )
                        
                        // For now, return null to avoid deprecated API issues
                        // TODO: Implement proper constructor call with modern APIs
                        collector.report(
                            CompilerMessageSeverity.INFO,
                            "[MapperIrVisitor] Returning null instead of constructor call to avoid API issues"
                        )
                        return builder.irNull()
                    } else {
                        collector.report(
                            CompilerMessageSeverity.WARNING,
                            "[MapperIrVisitor] No constructor found for mapper target type"
                        )
                    }
                } else {
                    collector.report(
                        CompilerMessageSeverity.WARNING,
                        "[MapperIrVisitor] Could not resolve target class for mapper"
                    )
                }
            }
        }
        
        collector.report(
            CompilerMessageSeverity.INFO,
            "[MapperIrVisitor] Falling back to null for mapper call"
        )
        
        return builder.irNull()
    }
}