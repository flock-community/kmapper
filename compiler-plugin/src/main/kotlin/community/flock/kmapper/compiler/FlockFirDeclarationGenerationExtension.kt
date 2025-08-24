package community.flock.kmapper.compiler

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.predicate.DeclarationPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.plugin.createMemberFunction
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
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
            // Add name parameter of type Pair<String, Any>
            run {
                val pairType = org.jetbrains.kotlin.fir.types.impl.ConeClassLikeTypeImpl(
                    org.jetbrains.kotlin.fir.symbols.impl.ConeClassLikeLookupTagImpl(org.jetbrains.kotlin.name.ClassId.topLevel(FqName("kotlin.Pair"))),
                    arrayOf(
                        session.builtinTypes.stringType.coneType,
                        session.builtinTypes.anyType.coneType
                    ),
                    false
                )
                valueParameter(Name.identifier("name"), pairType)
            }
        }

        return listOf(flockFunction.symbol)
    }
}