package community.flock.kmapper.compiler.util

import org.jetbrains.kotlin.diagnostics.AbstractSourceElementPositioningStrategy
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory0DelegateProvider
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory1DelegateProvider
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import kotlin.getValue

object Diagnostics : KtDiagnosticsContainer() {

    /**
     * The compiler and the IDE use a different version of this class, so use reflection to find the available
     * version.
     */
    // Adapted from https://github.com/TadeasKriz/K2PluginBase/blob/main/kotlin-plugin/src/main/kotlin/com/tadeaskriz/example/ExamplePluginErrors.kt#L8
    private val psiElementClass by lazy {
        try {
            Class.forName("org.jetbrains.kotlin.com.intellij.psi.PsiElement")
        } catch (_: ClassNotFoundException) {
            Class.forName("com.intellij.psi.PsiElement")
        }.kotlin
    }

    val PokoOnNonClass by warning0(
        positioningStrategy = SourceElementPositioningStrategies.NAME_IDENTIFIER,
    )

    val PokoOnDataClass by error0(
        positioningStrategy = SourceElementPositioningStrategies.DATA_MODIFIER,
    )

    val PokoOnValueClass by error0(
        positioningStrategy = SourceElementPositioningStrategies.INLINE_OR_VALUE_MODIFIER,
    )

    val PokoOnInnerClass by error0(
        positioningStrategy = SourceElementPositioningStrategies.INNER_MODIFIER,
    )

    val PrimaryConstructorRequired by error0(
        positioningStrategy = SourceElementPositioningStrategies.NAME_IDENTIFIER,
    )

    val PrimaryConstructorPropertiesRequired by error0(
        positioningStrategy = SourceElementPositioningStrategies.NAME_IDENTIFIER,
    )

    val SkippedPropertyWithCustomAnnotation by warning0(
        positioningStrategy = SourceElementPositioningStrategies.ANNOTATION_USE_SITE,
    )

    val MissingConstructorParameters by error0(
        positioningStrategy = SourceElementPositioningStrategies.DEFAULT,
    )

    override fun getRendererFactory(): BaseDiagnosticRendererFactory = DiagnosticRendererFactory

    /**
     * Copy of [org.jetbrains.kotlin.diagnostics.error0] with hack for correct `PsiElement`
     * class.
     */
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

    /**
     * Copy of [org.jetbrains.kotlin.diagnostics.error1] with hack for correct `PsiElement`
     * class.
     */
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
            factory = Diagnostics.PokoOnNonClass,
            message = "Poko can only be applied to a class",
        )
        it.put(
            factory = Diagnostics.PokoOnDataClass,
            message = "Poko cannot be applied to a data class",
        )
        it.put(
            factory = Diagnostics.PokoOnValueClass,
            message = "Poko cannot be applied to a value class",
        )
        it.put(
            factory = Diagnostics.PokoOnInnerClass,
            message = "Poko cannot be applied to an inner class"
        )
        it.put(
            factory = Diagnostics.PrimaryConstructorRequired,
            message = "Poko class must have a primary constructor"
        )
        it.put(
            factory = Diagnostics.PrimaryConstructorPropertiesRequired,
            message = "Poko class primary constructor must have at least one not-skipped property",
        )
        it.put(
            factory = Diagnostics.SkippedPropertyWithCustomAnnotation,
            message = "The @Skip annotation is experimental and its behavior may change; use with caution",
        )
        it.put(
            factory = Diagnostics.MissingConstructorParameters,
            message = "Missing constructor parameters in KMapper mapping. Check the INFO log for parameter details.",
        )
    }
}