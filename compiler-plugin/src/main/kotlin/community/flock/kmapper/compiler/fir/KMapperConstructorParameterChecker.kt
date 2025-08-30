import community.flock.kmapper.compiler.util.Diagnostics
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
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirTypeProjectionWithVariance
import org.jetbrains.kotlin.fir.types.coneTypeOrNull
import org.jetbrains.kotlin.fir.types.isPrimitive
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class KMapperConstructorParameterChecker(val collector: MessageCollector, private val session: FirSession) :

    FirCallChecker(MppCheckerKind.Common) {

    data class Field(
        val name: Name,
        val type: ConeKotlinType,
        val fields: List<Field>
    )
    infix fun Field.structuralCompare(other: Field): Boolean =
        name == other.name && ((type.isPrimitive == other.type.isPrimitive && type == other.type) || other.fields.zip(fields).all { (a, b) -> a structuralCompare b })

    companion object {
        val kMapperAnnotation = FqName("community.flock.kmapper.KMapper")
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirCall) {

        val annotation = ClassId.topLevel(kMapperAnnotation)
        val hasAnnotation = expression.toReference(session)
            ?.toResolvedBaseSymbol()
            ?.hasAnnotation(annotation, session) ?: false

        if (!hasAnnotation) return

        val function = expression as? FirFunctionCall ?: return

        val fromFields = function.typeArguments
            .getOrNull(1)
            ?.let { it as? FirTypeProjectionWithVariance }
            ?.extractFields()
            ?: return

        val toFields = function.typeArguments
            .firstOrNull()
            ?.let { it as? FirTypeProjectionWithVariance }
            ?.extractFields()
            ?: return

        val mapping = function.arguments.firstOrNull().let { it as? FirAnonymousFunctionExpression }
            ?.let { arg ->
                arg.anonymousFunction.body
                    ?.statements?.filterIsInstance<FirFunctionCall>()
                    ?.mapNotNull { call ->
                        call.extensionReceiver
                            ?.toReference(session)
                            ?.toResolvedPropertySymbol()
                            ?.let { receiver ->
                                val arg = call.arguments.first()
                                Field(
                                    name = receiver.name,
                                    type = arg.resolvedType,
                                    fields = arg.resolvedType.resolveFields()
                                )
                            }

                    }
            }
            ?: emptyList()


        val diff = toFields
            .filterNot { to -> mapping.any { mapping -> to structuralCompare  mapping } }
            .filterNot { to -> fromFields.any { from -> to structuralCompare from } }

        val missingParameterNames = diff.joinToString(", ") { it.name.asString() }

        // Add a diagnostic message to the report
        StringBuilder()
            .apply {
                appendLine("constructorFields: ${toFields}")
                appendLine("receiverFields: ${fromFields}")
                appendLine("mapping: $mapping")
                appendLine("diff: $diff")
            }
            .apply {
                collector.report(CompilerMessageSeverity.INFO, toString())
            }

        // Report if there are missing mapping parameters
        if (diff.isNotEmpty()) {
            reporter.reportOn(
                source = function.calleeReference.source,
                factory = Diagnostics.MissingConstructorParameters,
                a = missingParameterNames
            )
        }
    }

    private fun FirTypeProjectionWithVariance.extractFields(): List<Field>? {
        val resolvedTypeArgument = typeRef.coneTypeOrNull
        val classSymbol = resolvedTypeArgument?.toRegularClassSymbol(session)
        val primaryConstructor = classSymbol?.constructors(session)?.firstOrNull()
        return primaryConstructor?.valueParameterSymbols?.map { parameter ->
            Field(
                name = parameter.name,
                type = parameter.resolvedReturnType,
                fields = parameter.resolvedReturnType.resolveFields()
            )
        }
    }

    private fun ConeKotlinType.resolveFields(): List<Field> {
        val classSymbol = toRegularClassSymbol(session)
        val primaryConstructor = classSymbol?.constructors(session)?.firstOrNull()
        return primaryConstructor?.valueParameterSymbols?.map { parameter ->
            Field(
                name = parameter.name,
                type = parameter.resolvedReturnType,
                fields = parameter.resolvedReturnType.resolveFields()
            )
        }.orEmpty()
    }

    internal class Extension(
        collector: MessageCollector,
        session: FirSession,
    ) : FirAdditionalCheckersExtension(session) {
        override val expressionCheckers: ExpressionCheckers =
            object : ExpressionCheckers() {
                override val callCheckers: Set<FirCallChecker> =
                    setOf(KMapperConstructorParameterChecker(collector, session))
            }
    }
}

