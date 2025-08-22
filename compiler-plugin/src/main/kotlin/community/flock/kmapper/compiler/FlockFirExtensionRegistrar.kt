package community.flock.kmapper.compiler

import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

/**
 * FIR extension registrar for the Flock plugin
 * Handles frontend analysis and validation of @Flock annotations
 */
class FlockFirExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::FlockFirDeclarationGenerationExtension
    }
}