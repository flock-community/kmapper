package community.flock.kmapper.compiler.util

import org.jetbrains.kotlin.diagnostics.AbstractSourceElementPositioningStrategy
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory0DelegateProvider
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory1DelegateProvider
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory

object Diagnostics : KtDiagnosticsContainer() {

    /**
     * The compiler and the IDE use a different version of this class, so use reflection to find the available
     * version.
     * Adapted from https://github.com/TadeasKriz/K2PluginBase/blob/main/kotlin-plugin/src/main/kotlin/com/tadeaskriz/example/ExamplePluginErrors.kt#L8
     *
     */
    private val psiElementClass by lazy {
        try {
            Class.forName("org.jetbrains.kotlin.com.intellij.psi.PsiElement")
        } catch (_: ClassNotFoundException) {
            Class.forName("com.intellij.psi.PsiElement")
        }.kotlin
    }

    val MissingConstructorParameters by error1<String>(
        positioningStrategy = SourceElementPositioningStrategies.DEFAULT,
    )

    override fun getRendererFactory(): BaseDiagnosticRendererFactory = DiagnosticRendererFactory

    /**
     * Copy of [org.jetbrains.kotlin.diagnostics.warning0] with hack for correct `PsiElement`
     * class.
     */
    context(container: KtDiagnosticsContainer)
    private fun warning0(
        positioningStrategy: AbstractSourceElementPositioningStrategy = SourceElementPositioningStrategies.DEFAULT,
    ): DiagnosticFactory0DelegateProvider {
        return DiagnosticFactory0DelegateProvider(
            severity = Severity.WARNING,
            positioningStrategy = positioningStrategy,
            psiType = psiElementClass,
            container = container,
        )
    }

    context(container: KtDiagnosticsContainer)
    private fun error0(
        positioningStrategy: AbstractSourceElementPositioningStrategy = SourceElementPositioningStrategies.DEFAULT,
    ): DiagnosticFactory0DelegateProvider {
        return DiagnosticFactory0DelegateProvider(
            severity = Severity.ERROR,
            positioningStrategy = positioningStrategy,
            psiType = psiElementClass,
            container = container,
        )
    }

    context(container: KtDiagnosticsContainer)
    private inline fun <reified T : Any> error1(
        positioningStrategy: AbstractSourceElementPositioningStrategy = SourceElementPositioningStrategies.DEFAULT,
    ): DiagnosticFactory1DelegateProvider<T> {
        return DiagnosticFactory1DelegateProvider(
            severity = Severity.ERROR,
            positioningStrategy = positioningStrategy,
            psiType = psiElementClass,
            container = container,
        )
    }
}

private object DiagnosticRendererFactory : BaseDiagnosticRendererFactory() {
    override val MAP by KtDiagnosticFactoryToRendererMap("Poko") {
        it.put(
            factory = Diagnostics.MissingConstructorParameters,
            message = "Missing constructor parameters in mapping: {0}.",
            rendererA = null
        )
    }
}