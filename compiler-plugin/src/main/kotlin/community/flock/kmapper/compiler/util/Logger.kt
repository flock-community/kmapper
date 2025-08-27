package community.flock.kmapper.compiler.util;

import org.jetbrains.kotlin.fir.expressions.FirCall
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.expressions.resolvedArgumentMapping
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.text
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.collections.isNotEmpty

object Logger {


    private fun String.spacer(space: Int = 1) = split("\n")
        .joinToString("\n") {
            if (it.isNotBlank()) {
                "${(1..space).joinToString("") { "  " }}$it"
            } else {
                it
            }
        }

    fun dumpFirCall(firCall: FirCall):String = StringBuilder()
        .apply {

            // Root node information
            appendLine("FirCall: ${firCall::class.simpleName}")

            // Source information
            try {
                val sourceText = firCall.source?.text ?: "null"
                appendLine("├── Source: $sourceText")
            } catch (e: Exception) {
                appendLine("├── Source: unavailable")
            }

            // Handle different types of FirCall
            when (firCall) {
                is FirFunctionCall -> {
                    // Callee reference
                    appendLine("├── CalleeReference: ${firCall.calleeReference::class.simpleName}")
                    appendLine("├── Function Name: ${firCall.calleeReference.name}")

                    // annotations
                    val annotations = firCall.annotations
                    if (annotations.isNotEmpty()) {
                        appendLine("├── Annotations (${annotations.size}):")
                        annotations.forEachIndexed { index, arg ->
                            val isLast = index == annotations.size - 1
                            val prefix = if (isLast) "└──" else "├──"
                            appendLine("│   $prefix ${arg.source.text}")
                            appendLine("│   $prefix ${arg::class.simpleName}")

                            // Recursively dump nested FirCall expressions
                            if (arg is FirCall) {
                                val nestedTree = dumpFirCall(arg).spacer(space = 1)
                                append(nestedTree)
                            }
                        }
                    } else {
                        appendLine("├── Annotations: none")
                    }

                    // Type arguments
                    if (firCall.typeArguments.isNotEmpty()) {
                        appendLine("├── Type Arguments (${firCall.typeArguments.size}):")
                        firCall.typeArguments.forEachIndexed { index, typeArg ->
                            val isLast = index == firCall.typeArguments.size - 1
                            val prefix = if (isLast) "└──" else "├──"
                            appendLine("│   $prefix ${typeArg::class.simpleName}: ${typeArg}")
                        }
                    } else {
                        appendLine("├── Type Arguments: none")
                    }

                    // Arguments
                    val arguments = firCall.arguments
                    if (arguments.isNotEmpty()) {
                        appendLine("├── Arguments (${arguments.size}):")
                        arguments.forEachIndexed { index, arg ->
                            val isLast = index == arguments.size - 1
                            val prefix = if (isLast) "└──" else "├──"
                            appendLine("│   $prefix ${arg::class.simpleName}")

                            // Recursively dump nested FirCall expressions
                            if (arg is FirCall) {
                                val nestedTree = dumpFirCall(arg).spacer(space = 1)
                                append(nestedTree)
                            }
                        }
                    } else {
                        appendLine("├── Arguments: none")
                    }

                    // Resolved argument mapping
                    try {
                        val resolvedMapping = firCall.resolvedArgumentMapping
                        if (resolvedMapping != null && resolvedMapping.isNotEmpty()) {
                            appendLine("├── Resolved Argument Mapping:")
                            resolvedMapping.forEach { (arg, param) ->
                                appendLine("│   ├── ${arg::class.simpleName} -> ${param.name}")
                            }
                        } else {
                            appendLine("├── Resolved Argument Mapping: none")
                        }
                    } catch (e: Exception) {
                        appendLine("├── Resolved Argument Mapping: unavailable")
                    }

                    // Return type
                    try {
                        val returnType = firCall.resolvedType
                        appendLine("└── Return Type: ${returnType}")
                    } catch (e: Exception) {
                        appendLine("└── Return Type: unresolved")
                    }
                }
                else -> {
                    appendLine("└── Type: ${firCall::class.simpleName}")
                }
            }
        }
        .toString()
}
