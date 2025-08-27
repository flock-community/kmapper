@file:OptIn(SymbolInternals::class, DirectDeclarationsAccess::class)

import community.flock.kmapper.compiler.util.Diagnostics
import community.flock.kmapper.compiler.util.Logger.dumpFirCall
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirCallChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.constructors
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.expressions.FirAnonymousFunctionExpression
import org.jetbrains.kotlin.fir.expressions.FirCall
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.expressions.toReference
import org.jetbrains.kotlin.fir.references.toResolvedBaseSymbol
import org.jetbrains.kotlin.fir.references.toResolvedPropertySymbol
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirTypeProjectionWithVariance
import org.jetbrains.kotlin.fir.types.coneTypeOrNull
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.text


internal class ConstructorParameterCheckerExtension(
    collector: MessageCollector,
    session: FirSession,
) : FirAdditionalCheckersExtension(session) {
    override val expressionCheckers: ExpressionCheckers =
        object : ExpressionCheckers() {
            override val callCheckers: Set<FirCallChecker> =
                setOf(ConstructorParameterChecker(collector, session))
        }
}

// The checker itself
class ConstructorParameterChecker(val collector: MessageCollector, private val session: FirSession) :
    FirCallChecker(MppCheckerKind.Common) {

    interface Ref {
        val name: Name
        val type: ConeKotlinType
    }

    data class Constructor(
        override val name: Name,
        override val type: ConeKotlinType,
    ) : Ref {
        override fun toString(): String {
            return "Constructor(name=$name, type=$type)"
        }
    }

    data class Mapping(
        override val name: Name,
        override val type: ConeKotlinType,
        val value: FirExpression
    ) : Ref {
        override fun toString(): String {
            return "Mapping(name=$name, type=$type, value=${value.source.text})"
        }
    }

    companion object {
        val kMapperAnnotation = FqName("community.flock.kmapper.KMapper")
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirCall) {

        val function: FirFunctionCall = expression as? FirFunctionCall ?: return


        val typeArgument = expression.typeArguments.firstOrNull() as? FirTypeProjectionWithVariance ?: return
        val resolvedTypeArgument = typeArgument.typeRef.coneTypeOrNull ?: return
        val classSymbol = resolvedTypeArgument.toRegularClassSymbol(session) ?: return
        val primaryConstructor = classSymbol.constructors(session).firstOrNull() ?: return
        val constructor = primaryConstructor.valueParameterSymbols.map {
            Constructor(
                name = it.name,
                type = it.resolvedReturnType
            )
        }

        val anno = ClassId.topLevel(kMapperAnnotation)
        val calleeReference = expression.toReference(session)

        // Resolve to the callable symbol and get the declaration
        val hasAnnotation = calleeReference?.toResolvedBaseSymbol()?.hasAnnotation(anno, session) ?: false

        if (!hasAnnotation) return

        val arg = function.arguments.first() as? FirAnonymousFunctionExpression ?: return
        val mappings = arg.anonymousFunction.body
            ?.statements
            ?.filterIsInstance<FirFunctionCall>()
            ?.mapNotNull { call ->
                call.extensionReceiver
                    ?.toReference(session)
                    ?.toResolvedPropertySymbol()
                    ?.let { receiver ->
                        val arg = call.arguments.first()
                        Mapping(
                            name = receiver.name,
                            type = arg.resolvedType,
                            value = arg
                        )
                    }

            }
            .orEmpty()


        val diff = constructor.filterNot { c -> mappings.any { m -> m.name == c.name && m.type == c.type }}

        // Report error if there are missing constructor parameters
        if (diff.isNotEmpty()) {
            reporter.reportOn(
                source = typeArgument.source,
                factory = Diagnostics.MissingConstructorParameters,
            )
        }

        val missingParameterNames = diff.joinToString(", ") { it.name.asString() }
        val builder = StringBuilder()
            .apply {
                appendLine("Missing constructor parameters: $missingParameterNames")
                appendLine("constructor: ${constructor}")
                appendLine("mapping: $mappings")
                appendLine("diff: $diff")
                appendLine(dumpFirCall(expression))
            }

        collector.report(CompilerMessageSeverity.INFO, builder.toString())

    }
}

