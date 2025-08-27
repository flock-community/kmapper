package community.flock.kmapper.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

@OptIn(ExperimentalCompilerApi::class)
class KMapperCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val collector: MessageCollector = configuration.get(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY) ?: MessageCollector.NONE
        collector.report(CompilerMessageSeverity.INFO, "[KMapperPlugin] Compiler plugin registrar loaded")

        // Register FIR extension using proper K2 adapter
        FirExtensionRegistrarAdapter.registerExtension(MapperFirExtensionRegistrar(collector))
        
        // Register IR extension for code generation
        IrGenerationExtension.registerExtension(MapperExtension(collector))
    }
}
