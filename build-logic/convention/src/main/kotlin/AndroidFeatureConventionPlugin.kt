import bd.bookstore.buildlogic.libs
import bd.bookstore.buildlogic.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * A feature: one area of the app, such as the cart or the catalogue, with its
 * screens, view models and navigation entries.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("bookstore.android.library")
            pluginManager.apply("bookstore.android.compose")
            pluginManager.apply("bookstore.hilt")
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

            dependencies {
                add("implementation", project(":core:common"))
                add("implementation", project(":core:model"))
                add("implementation", project(":core:designsystem"))
                add("implementation", project(":core:ui"))
                add("implementation", project(":core:data"))
                add("implementation", libs.library("androidx-lifecycle-runtime-compose"))
                add("implementation", libs.library("androidx-lifecycle-viewmodel-compose"))
                add("implementation", libs.library("androidx-lifecycle-viewmodel-navigation3"))
                add("implementation", libs.library("androidx-navigation3-runtime"))
                add("implementation", libs.library("androidx-hilt-lifecycle-viewmodel-compose"))
                add("implementation", libs.library("kotlinx-serialization-json"))
                add("implementation", libs.library("kotlinx-collections-immutable"))
                add("implementation", libs.library("androidx-compose-material-icons-extended"))
                add("testImplementation", project(":core:testing"))
            }
        }
    }
}
