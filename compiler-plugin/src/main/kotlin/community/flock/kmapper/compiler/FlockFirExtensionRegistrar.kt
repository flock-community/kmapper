package community.flock.kmapper.compiler

import ConstructorParameterCheckerExtension
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

/**
 * FIR extension registrar for the Flock plugin
 * Handles frontend analysis and validation of @Flock annotations
 */
class FlockFirExtensionRegistrar(val collector: MessageCollector) : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +{ session: FirSession -> ConstructorParameterCheckerExtension(collector, session) }
    }
}