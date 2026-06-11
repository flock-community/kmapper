package community.flock.kmapper.gradle.plugin

import community.flock.kmapper.BuildConfig
import community.flock.kmapper.BuildConfig.ANNOTATIONS_LIBRARY_COORDINATES
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

@Suppress("unused") // Used via reflection.
class KMapperGradlePlugin : KotlinCompilerPluginSupportPlugin {

    companion object {
        // The compiler plugin is built against the Kotlin 2.4 compiler API and
        // cannot be loaded by older compilers; fail fast with a clear message
        // instead of crashing compilation (see issue #31 for the 2.3-on-2.4
        // counterpart of that crash).
        private val MINIMUM_KOTLIN_VERSION = KotlinVersion(2, 4, 0)
    }

    override fun apply(target: Project) {
        target.extensions.create("flockPlugin", KMapperGradleExtension::class.java)
        target.plugins.withType(KotlinBasePlugin::class.java).configureEach { kotlinPlugin ->
            checkKotlinVersion(kotlinPlugin.pluginVersion)
        }
    }

    private fun checkKotlinVersion(version: String) {
        val parts = version.substringBefore('-').split('.').map { it.toIntOrNull() ?: return }
        if (parts.size < 3) return
        if (KotlinVersion(parts[0], parts[1], parts[2]) < MINIMUM_KOTLIN_VERSION) {
            throw GradleException(
                "kmapper requires Kotlin $MINIMUM_KOTLIN_VERSION or newer, but this project uses " +
                    "Kotlin $version. Older compilers cannot load the kmapper compiler plugin; " +
                    "either upgrade Kotlin or use a kmapper release built for Kotlin $version."
            )
        }
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun getCompilerPluginId(): String = BuildConfig.KOTLIN_PLUGIN_ID

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = BuildConfig.KOTLIN_PLUGIN_GROUP,
        artifactId = BuildConfig.KOTLIN_PLUGIN_NAME,
        version = BuildConfig.KOTLIN_PLUGIN_VERSION,
    )

    override fun applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>
    ): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project

        kotlinCompilation.dependencies { implementation(ANNOTATIONS_LIBRARY_COORDINATES) }
        if (kotlinCompilation.implementationConfigurationName == "metadataCompilationImplementation") {
            project.dependencies.add("commonMainImplementation", ANNOTATIONS_LIBRARY_COORDINATES)
        }

        return project.provider {
            val extension = project.extensions.getByType(KMapperGradleExtension::class.java)

            emptyList()
        }
    }
}
