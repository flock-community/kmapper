@file:Suppress("DEPRECATION", "DEPRECATION_ERROR")

package community.flock.kmapper.compiler

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.predicate.DeclarationPredicate
import org.jetbrains.kotlin.name.FqName

/**
 * FIR extension that:
 * - for any class annotated with @community.flock.kmapper.Flock, generates a parameterless
 *   function fun flock(): String = "FLOCK " + toString()
 */
class FlockExtension(session: FirSession) : FirDeclarationGenerationExtension(session) {

    val FLOCK_PREDICATE = DeclarationPredicate.create {
        annotated(FqName("community.flock.kmapper.Flock"))
    }
    val HAS_FLOCK_PREDICATE = DeclarationPredicate.create {
        hasAnnotated(FqName("community.flock.kmapper.Flock"))
    }
    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(FLOCK_PREDICATE)
        register(HAS_FLOCK_PREDICATE)
    }
}
