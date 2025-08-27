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
import org.jetbrains.kotlin.fir.expressions.FirCall
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.toReference
import org.jetbrains.kotlin.text


internal class PokoFirCheckersExtension(
    collector: MessageCollector,
    session: FirSession,
) : FirAdditionalCheckersExtension(session) {
    override val expressionCheckers: ExpressionCheckers =
        object : ExpressionCheckers() {
            override val callCheckers = setOf(MyConstructorParameterChecker(collector, session))
        }
}

class MyConstructorParameterChecker(val collector: MessageCollector, val session: FirSession) :
    FirCallChecker(MppCheckerKind.Common) {


    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirCall) {
        // Only report when function name is "to"
        when (expression) {
            is FirFunctionCall -> {
                expression.typeArguments.forEach { arg ->
                    val ref = arg.toReference(session)
                    println("==================REF================")
                    println(ref)
                    collector.report(CompilerMessageSeverity.INFO, "[KMapperPlugin] Blalalala tets ${arg.source.text}")
//                    reporter.reportOn(
//                        source = arg.source,
//                        factory = Diagnostics.PokoOnNonClass,
//                    )
                }
            }

            else -> null
        }
    }




}