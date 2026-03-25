import community.flock.kmapper.compiler.fir.Field
import community.flock.kmapper.compiler.fir.deepEqual
import community.flock.kmapper.compiler.fir.enumEntryNames
import community.flock.kmapper.compiler.util.Diagnostics
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirBinaryDependenciesModuleData
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirCallChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.declarations.constructors
import org.jetbrains.kotlin.fir.declarations.declaredProperties
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.utils.isEnumClass
import org.jetbrains.kotlin.fir.expressions.FirAnonymousFunctionExpression
import org.jetbrains.kotlin.fir.expressions.FirCall
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.expressions.explicitReceiver
import org.jetbrains.kotlin.fir.expressions.toReference
import org.jetbrains.kotlin.fir.references.FirNamedReference
import org.jetbrains.kotlin.fir.references.toResolvedBaseSymbol
import org.jetbrains.kotlin.fir.references.toResolvedPropertySymbol
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirTypeProjectionWithVariance
import org.jetbrains.kotlin.fir.types.coneTypeOrNull
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.name.FqName

@OptIn(DirectDeclarationsAccess::class, SymbolInternals::class)
class KMapperFirMappingChecker(val collector: MessageCollector, private val session: FirSession) :

    FirCallChecker(MppCheckerKind.Common) {

    companion object {
        val kMapperAnnotation = FqName("community.flock.kmapper.KMapper")
    }


    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirCall) = context(session, collector) {

        val annotation = ClassId.topLevel(kMapperAnnotation)
        val hasAnnotation = expression.toReference(session)
            ?.toResolvedBaseSymbol()
            ?.hasAnnotation(annotation, session) ?: false

        if (!hasAnnotation) return

        val function = expression as? FirFunctionCall ?: return

        // Skip field resolution for direct enum-to-enum mapping with matching entry names
        val fromType = function.typeArguments.getOrNull(1)
            ?.let { it as? FirTypeProjectionWithVariance }?.typeRef?.coneTypeOrNull
        val toType = function.typeArguments.firstOrNull()
            ?.let { it as? FirTypeProjectionWithVariance }?.typeRef?.coneTypeOrNull
        if (fromType != null && toType != null) {
            val fromEnum = fromType.toRegularClassSymbol(session)?.takeIf { it.isEnumClass }
            val toEnum = toType.toRegularClassSymbol(session)?.takeIf { it.isEnumClass }
            if (fromEnum != null && toEnum != null) {
                val fromEntries = fromEnum.enumEntryNames().toSet()
                val toEntries = toEnum.enumEntryNames().toSet()
                if (fromEntries == toEntries) return
            }
        }

        val fromFields = function.typeArguments
            .getOrNull(1)
            ?.let { it as? FirTypeProjectionWithVariance }
            ?.constructorFields()
            ?: return

        val toFields = function.typeArguments
            .firstOrNull()
            ?.let { it as? FirTypeProjectionWithVariance }
            ?.extractConstructorFields()
            ?: return

        val mapping = function.arguments.firstOrNull().let { it as? FirAnonymousFunctionExpression }
            ?.let { arg ->
                arg.anonymousFunction.body
                    ?.statements?.filterIsInstance<FirFunctionCall>()
                    ?.mapNotNull { call ->
                        val functionName = (call.calleeReference as? FirNamedReference)?.name?.asString()
                        when (functionName) {
                            "to" -> {
                                val propSymbol = call.explicitReceiver
                                    ?.toReference(session)
                                    ?.toResolvedPropertySymbol()
                                val valueExpr = call.arguments.firstOrNull()
                                if (propSymbol != null && valueExpr != null) {
                                    Field(
                                        name = propSymbol.name,
                                        type = valueExpr.resolvedType,
                                        hasDefaultValue = false,
                                        fields = valueExpr.resolvedType.resolveConstructorFields()
                                    )
                                } else null
                            }
                            "ignore" -> {
                                val propSymbol = call.extensionReceiver
                                    ?.toReference(session)
                                    ?.toResolvedPropertySymbol()
                                if (propSymbol != null) {
                                    // Find the matching toField to include its type info
                                    toFields.find { it.name == propSymbol.name }
                                } else null
                            }
                            else -> null
                        }
                    }
            }
            ?: emptyList()

        val diff = toFields
            .filterNot { to -> mapping.any { mapping -> to deepEqual mapping } }
            .filterNot { to -> fromFields.any { from -> to deepEqual from } }
            .filterNot { to -> to.hasDefaultValue }

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

    private fun FirTypeProjectionWithVariance.extractConstructorFields(): List<Field>? {
        val resolvedTypeArgument = typeRef.coneTypeOrNull
        return resolvedTypeArgument?.resolveConstructorFields()
    }

    private fun ConeKotlinType.resolveConstructorFields(): List<Field> {
        val classSymbol = toRegularClassSymbol(session)
        if (classSymbol?.moduleData is FirBinaryDependenciesModuleData) return emptyList()
        val primaryConstructor = classSymbol?.constructors(session)?.firstOrNull()
        return primaryConstructor?.valueParameterSymbols?.map { parameter ->
            Field(
                name = parameter.name,
                type = parameter.resolvedReturnType,
                hasDefaultValue = parameter.hasDefaultValue,
                fields = parameter.resolvedReturnType.resolveConstructorFields()
            )
        }.orEmpty()
    }

    private fun FirTypeProjectionWithVariance.constructorFields(): List<Field>? {
        val resolvedTypeArgument = typeRef.coneTypeOrNull
        return resolvedTypeArgument?.resolvePropertyFields()
    }

    private fun ConeKotlinType.resolvePropertyFields(): List<Field> {
        val classSymbol = toRegularClassSymbol(session)
        if (classSymbol?.moduleData is FirBinaryDependenciesModuleData) return emptyList()
        return classSymbol?.declaredProperties(session)
            .orEmpty()
            .map { property ->
                Field(
                    name = property.name,
                    type = property.resolvedReturnType,
                    hasDefaultValue = property.resolvedDefaultValue != null,
                    fields = property.resolvedReturnType.resolvePropertyFields()
                )
            }
    }

    internal class Extension(
        collector: MessageCollector,
        session: FirSession,
    ) : FirAdditionalCheckersExtension(session) {
        override val expressionCheckers: ExpressionCheckers =
            object : ExpressionCheckers() {
                override val callCheckers: Set<FirCallChecker> =
                    setOf(KMapperFirMappingChecker(collector, session))
            }
    }
}

