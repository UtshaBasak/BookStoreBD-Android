import bd.bookstore.buildlogic.libs
import bd.bookstore.buildlogic.library
import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

/** Jetpack Compose for an application or library module. Apply after the Android plugin. */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            extensions.configure<CommonExtension> {
                buildFeatures.compose = true
            }

            extensions.configure<ComposeCompilerGradlePluginExtension> {
                stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("config/compose/stability.conf"))
                // `-PcomposeReports=true` writes the compiler's stability reports under build/compose.
                if (providers.gradleProperty("composeReports").orNull == "true") {
                    reportsDestination.set(layout.buildDirectory.dir("compose/reports"))
                    metricsDestination.set(layout.buildDirectory.dir("compose/metrics"))
                }
            }

            dependencies {
                val bom = platform(libs.library("androidx-compose-bom"))
                add("implementation", bom)
                add("testImplementation", bom)
                add("implementation", libs.library("androidx-compose-ui-tooling-preview"))
                add("debugImplementation", libs.library("androidx-compose-ui-tooling"))
                add("testImplementation", libs.library("androidx-compose-ui-test-junit4"))
                add("debugImplementation", libs.library("androidx-compose-ui-test-manifest"))
                add("testImplementation", libs.library("robolectric"))
                add("testImplementation", libs.library("androidx-test-core"))
                add("testImplementation", libs.library("androidx-test-ext-junit"))
            }
        }
    }
}
