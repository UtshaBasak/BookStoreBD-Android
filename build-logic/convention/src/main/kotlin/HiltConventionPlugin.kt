import bd.bookstore.buildlogic.libs
import bd.bookstore.buildlogic.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Hilt dependency injection, with KSP generating the code. For Android modules. */
class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("com.google.dagger.hilt.android")

            dependencies {
                add("implementation", libs.library("hilt-android"))
                add("ksp", libs.library("hilt-compiler"))
                add("testImplementation", libs.library("hilt-android-testing"))
                add("kspTest", libs.library("hilt-compiler"))
            }
        }
    }
}
