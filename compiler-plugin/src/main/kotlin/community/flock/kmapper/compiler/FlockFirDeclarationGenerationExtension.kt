package community.flock.kmapper.compiler

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.*
import org.jetbrains.kotlin.fir.extensions.predicate.DeclarationPredicate
import org.jetbrains.kotlin.fir.plugin.createMemberFunction
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.types.constructStarProjectedType
import org.jetbrains.kotlin.fir.types.ConeTypeParameterType
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

        val flockFunction = createMemberFunction(
            owner = classSymbol,
            key = FlockKey,
            name = callableId.callableName,
            returnTypeProvider = { typeParameters ->
                // Return the first type parameter (T)
                if (typeParameters.isNotEmpty()) {
                    typeParameters.first().symbol.defaultType
                } else {
                    session.builtinTypes.anyType.coneType
                }
            }
        ) {
            // Add type parameter T
            typeParameter(Name.identifier("T"))
            // Add name parameter of type String
            valueParameter(Name.identifier("name"), session.builtinTypes.stringType.coneType)
        }

        return listOf(flockFunction.symbol)
    }
}