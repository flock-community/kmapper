package community.flock.kmapper.compiler

import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class KMapperFirExtensionRegistar: FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        + ::FlockExtension
    }
}