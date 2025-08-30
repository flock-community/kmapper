package community.flock.kmapper

import community.flock.kmapper.compiler.KMapperExtension
import community.flock.kmapper.compiler.KMapperFirExtensionRegistrar
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoot
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.messageCollector
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.directives.CodegenTestDirectives.IGNORE_DEXING
import org.jetbrains.kotlin.test.directives.ConfigurationDirectives.WITH_STDLIB
import org.jetbrains.kotlin.test.directives.JvmEnvironmentConfigurationDirectives.FULL_JDK
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.runners.codegen.AbstractFirLightTreeBlackBoxCodegenTest
import org.jetbrains.kotlin.test.services.EnvironmentConfigurator
import org.jetbrains.kotlin.test.services.RuntimeClasspathProvider
import org.jetbrains.kotlin.test.services.TestServices
import java.io.File
import kotlin.collections.buildSet

@TestMetadata("compiler-plugin/src/test/data")
open class AbstractBoxTest : AbstractFirLightTreeBlackBoxCodegenTest() {
    override fun configure(builder: TestConfigurationBuilder) {
        super.configure(builder)

        with(builder) {
            configurePlugin()
            defaultDirectives {
                +FULL_JDK
                +WITH_STDLIB
                +IGNORE_DEXING
            }
        }
    }
}

fun TestConfigurationBuilder.configurePlugin() {
    useConfigurators( ::MetroExtensionRegistrarConfigurator, ::RuntimeEnvironmentConfigurator)
    useCustomRuntimeClasspathProviders(::RuntimeClassPathProvider)
}

private val runtimeClasspath =
    System.getProperty("runtime.classpath")?.split(File.pathSeparator)?.map(::File)
        ?: error("Unable to get a valid classpath from 'runtime.classpath' property")

class RuntimeEnvironmentConfigurator(testServices: TestServices) :
    EnvironmentConfigurator(testServices) {
    override fun configureCompilerConfiguration(
        configuration: CompilerConfiguration,
        module: TestModule,
    ) {
        for (file in runtimeClasspath) {
            configuration.addJvmClasspathRoot(file)
        }
    }
}

class RuntimeClassPathProvider(testServices: TestServices) :
    RuntimeClasspathProvider(testServices) {
    override fun runtimeClassPaths(module: TestModule): List<File> {
        return runtimeClasspath
    }
}

class MetroExtensionRegistrarConfigurator(testServices: TestServices) :
    EnvironmentConfigurator(testServices) {
    @OptIn(ExperimentalCompilerApi::class)
    override fun CompilerPluginRegistrar.ExtensionStorage.registerCompilerExtensions(
        module: TestModule,
        configuration: CompilerConfiguration,
    ) {
        FirExtensionRegistrarAdapter.registerExtension(KMapperFirExtensionRegistrar(configuration.messageCollector))
        IrGenerationExtension.registerExtension(
            KMapperExtension(configuration.messageCollector)
        )
    }
}
