package community.flock.kmapper.compiler.fir

import community.flock.kmapper.compiler.FlockKey
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.predicate.DeclarationPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.plugin.createMemberFunction
import org.jetbrains.kotlin.fir.symbols.impl.ConeClassLikeLookupTagImpl
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.types.impl.ConeClassLikeTypeImpl
import org.jetbrains.kotlin.fir.types.impl.ConeTypeParameterTypeImpl
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * FIR declaration generation extension that adds to() method to @Flock annotated classes
 */
class SecondFirDeclarationGenerationExtension(
    session: FirSession,
) : FirDeclarationGenerationExtension(session) {

    companion object {
        val FLOCK_FUN_NAME = Name.identifier("second")

        private val FLOCK_PREDICATE = DeclarationPredicate.Companion.create {
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
//                if (typeParameters.isNotEmpty()) {
//                    typeParameters.first().symbol.defaultType
//                } else {
//                    session.builtinTypes.anyType.coneType
//                }
                session.builtinTypes.stringType.coneType
            },


            ) {
            // Add type parameter T
            typeParameter(
                name = Name.identifier("T"),
                isReified = true,
            )
            
            // Create Mapper<T> type - use Any for T since we can't access the type parameter directly
            val mapperType = ConeClassLikeTypeImpl(
                ConeClassLikeLookupTagImpl(ClassId.topLevel(FqName("community.flock.kmapper.Mapper"))),
                arrayOf(session.builtinTypes.anyType.coneType), // Using Any for T
                false
            )

            // Create function type (mapper: (Mapper<TO>.() -> Unit))
            val functionType = ConeClassLikeTypeImpl(
                ConeClassLikeLookupTagImpl(ClassId.topLevel(FqName("kotlin.Function1"))),
                arrayOf(
                    mapperType,  // receiver type: Mapper<T>
                    session.builtinTypes.unitType.coneType  // return type: Unit
                ),
                false
            )

            valueParameter(
                name = Name.identifier("mapper"),
                type = functionType
            )
            
        }

        return listOf(flockFunction.symbol)
    }
}