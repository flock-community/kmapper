package community.flock.kmapper.compiler

import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration

@OptIn(ExperimentalCompilerApi::class)
class KMapperCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        // Minimal marker to verify the plugin is loaded during compilation
        val collector: MessageCollector = configuration.get(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY) ?: MessageCollector.NONE
        collector.report(CompilerMessageSeverity.INFO, "[KMapperPlugin] Compiler plugin registrar loaded")
    }
}
