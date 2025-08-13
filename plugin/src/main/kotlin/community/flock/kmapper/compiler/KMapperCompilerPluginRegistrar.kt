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

    // K2-style entry point: we only log a marker here to validate plugin loading in tests
    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val collector: MessageCollector = configuration.get(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY) ?: MessageCollector.NONE
        collector.report(CompilerMessageSeverity.INFO, "[KMapperPlugin] Compiler plugin registrar loaded")
    }
}
