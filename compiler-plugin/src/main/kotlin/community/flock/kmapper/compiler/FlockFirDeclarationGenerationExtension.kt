package community.flock.kmapper.compiler

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.*
import org.jetbrains.kotlin.fir.extensions.predicate.DeclarationPredicate
import org.jetbrains.kotlin.fir.plugin.createMemberFunction
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.types.constructStarProjectedType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * FIR declaration generation extension that adds to() method to @Flock annotated classes
 */
class FlockFirDeclarationGenerationExtension(
    session: FirSession,
) : FirDeclarationGenerationExtension(session) {
    
    companion object {
        val FLOCK_FUN_NAME = Name.identifier("to")
        
        private val FLOCK_PREDICATE = DeclarationPredicate.create {
            annotated(FqName("community.flock.kmapper.Flock"))
        }
    }

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(FLOCK_PREDICATE)
    }

    override fun getCallableNamesForClass(
        classSymbol: FirClassSymbol<*>,
        context: MemberGenerationContext,
    ): Set<Name> {
        val provider = session.predicateBasedProvider
        if (!provider.matches(FLOCK_PREDICATE, classSymbol)) {
            return emptySet()
        }
        return setOf(FLOCK_FUN_NAME)
    }

    override fun generateFunctions(
        callableId: CallableId,
        context: MemberGenerationContext?,
    ): List<FirNamedFunctionSymbol> {
        if (callableId.callableName != FLOCK_FUN_NAME) {
            return emptyList()
        }

        val classSymbol = context?.owner ?: return emptyList()
        val provider = session.predicateBasedProvider
        if (!provider.matches(FLOCK_PREDICATE, classSymbol)) {
            return emptyList()
        }

        // Get String type from built-ins
        val stringType = session.builtinTypes.stringType.coneType

        val flockFunction = createMemberFunction(
            owner = classSymbol,
            key = FlockKey,
            name = callableId.callableName,
            returnType = stringType,
        )

        return listOf(flockFunction.symbol)
    }
}