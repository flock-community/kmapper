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




class ConstructorParameterChecker(val collector: MessageCollector, private val session: FirSession) :
    FirCallChecker(MppCheckerKind.Common) {


    data class Field(
        val name: Name,
        val type: ConeKotlinType,
    )

    companion object {
        val kMapperAnnotation = FqName("community.flock.kmapper.KMapper")
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirCall) {

        val annotation = ClassId.topLevel(kMapperAnnotation)
        val hasAnnotation = expression.toReference(session)
            ?.toResolvedBaseSymbol()
            ?.hasAnnotation(annotation, session) ?: false

        // Return if the call is not a constructor call and does not have the @KMapper annotation
        if (!hasAnnotation) return

        val function = expression as? FirFunctionCall ?: return
        val typeArgument = expression.typeArguments.firstOrNull() as? FirTypeProjectionWithVariance ?: return
        val resolvedTypeArgument = typeArgument.typeRef.coneTypeOrNull ?: return
        val classSymbol = resolvedTypeArgument.toRegularClassSymbol(session) ?: return
        val primaryConstructor = classSymbol.constructors(session).firstOrNull() ?: return
        val constructor = primaryConstructor.valueParameterSymbols.map { parameter ->
            Field(
                name = parameter.name,
                type = parameter.resolvedReturnType
            )
        }

        val arg = function.arguments.first() as? FirAnonymousFunctionExpression ?: return
        val statements = arg.anonymousFunction.body?.statements?.filterIsInstance<FirFunctionCall>() ?: return
        val fields = statements.mapNotNull { call ->
            call.extensionReceiver
                ?.toReference(session)
                ?.toResolvedPropertySymbol()
                ?.let { receiver ->
                    val arg = call.arguments.first()
                    Field(
                        name = receiver.name,
                        type = arg.resolvedType,
                    )
                }

        }

        val diff = constructor.filterNot { c -> fields.any { m -> m == c } }
        val missingParameterNames = diff.joinToString(", ") { it.name.asString() }

        // Report error if there are missing constructor parameters
        if (diff.isNotEmpty()) {
            reporter.reportOn(
                source = typeArgument.source,
                factory = Diagnostics.MissingConstructorParameters,
                a = missingParameterNames
            )
        }

        // Add a diagnostic message to the report
        StringBuilder()
            .apply {
                appendLine("Missing constructor parameters: $missingParameterNames")
                appendLine("constructor: ${constructor}")
                appendLine("mapping: $fields")
                appendLine("diff: $diff")
                appendLine(dumpFirCall(expression))
            }
            .apply {
                collector.report(CompilerMessageSeverity.INFO, toString())
            }
    }

    internal class Extension(
        collector: MessageCollector,
        session: FirSession,
    ) : FirAdditionalCheckersExtension(session) {
        override val expressionCheckers: ExpressionCheckers =
            object : ExpressionCheckers() {
                override val callCheckers: Set<FirCallChecker> =
                    setOf(ConstructorParameterChecker(collector, session))
            }
    }
}

