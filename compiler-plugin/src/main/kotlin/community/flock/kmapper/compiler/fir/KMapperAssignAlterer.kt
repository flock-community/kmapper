package community.flock.kmapper.compiler.fir

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.fakeElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirFunctionCallOrigin
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.expressions.FirVariableAssignment
import org.jetbrains.kotlin.fir.expressions.builder.buildArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.buildFunctionCall
import org.jetbrains.kotlin.fir.expressions.builder.buildPropertyAccessExpression
import org.jetbrains.kotlin.fir.extensions.FirAssignExpressionAltererExtension
import org.jetbrains.kotlin.fir.references.FirNamedReference
import org.jetbrains.kotlin.fir.references.builder.buildSimpleNamedReference
import org.jetbrains.kotlin.fir.references.toResolvedVariableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.types.coneTypeOrNull
import org.jetbrains.kotlin.name.Name

/**
 * FIR extension that transforms val-property assignments inside mapper lambdas
 * into `mapAssign()` function calls.
 *
 * When the user writes `age = value` inside a mapper lambda where `age` resolves
 * to a val property on the receiver type, this alterer rewrites it as
 * `age.mapAssign(value)` so the compiler does not report "val cannot be reassigned".
 */
class KMapperAssignAlterer(session: FirSession) : FirAssignExpressionAltererExtension(session) {

    override fun transformVariableAssignment(variableAssignment: FirVariableAssignment): FirStatement? {
        if (!variableAssignment.shouldTransform()) return null
        return buildFunctionCall(variableAssignment)
    }

    private fun FirVariableAssignment.shouldTransform(): Boolean {
        val lValue = lValue as? FirQualifiedAccessExpression ?: return false
        val symbol = lValue.calleeReference.toResolvedVariableSymbol() ?: return false
        return when (symbol) {
            is FirPropertySymbol -> symbol.isVal && symbol.callableId?.classId != null
            else -> false
        }
    }

    private fun buildFunctionCall(variableAssignment: FirVariableAssignment): FirFunctionCall {
        val lValue = variableAssignment.lValue as FirQualifiedAccessExpression
        val leftReference = lValue.calleeReference as FirNamedReference
        val leftSymbol = leftReference.toResolvedVariableSymbol()!!
        val leftResolvedType = leftSymbol.resolvedReturnTypeRef
        val rightArgument = variableAssignment.rValue

        return buildFunctionCall {
            source = variableAssignment.source?.fakeElement(KtFakeSourceElementKind.AssignmentPluginAltered)
            explicitReceiver = buildPropertyAccessExpression {
                source = leftReference.source
                coneTypeOrNull = leftResolvedType.coneType
                calleeReference = leftReference
                lValue.typeArguments.let(typeArguments::addAll)
                annotations += variableAssignment.annotations
                explicitReceiver = lValue.explicitReceiver
                dispatchReceiver = lValue.dispatchReceiver
                extensionReceiver = lValue.extensionReceiver
                contextArguments += lValue.contextArguments
            }
            argumentList = buildArgumentList {
                arguments += rightArgument
            }
            calleeReference = buildSimpleNamedReference {
                source = variableAssignment.source
                name = Name.identifier("mapAssign")
            }
            origin = FirFunctionCallOrigin.Regular
        }
    }
}
