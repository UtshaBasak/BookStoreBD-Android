import bd.bookstore.buildlogic.addUnitTestDependencies
import bd.bookstore.buildlogic.configureKotlinJvm
import bd.bookstore.buildlogic.configureQuality
import org.gradle.api.Plugin
import org.gradle.api.Project

/** A pure Kotlin module, with no Android dependency: the models, for one. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            configureKotlinJvm()
            addUnitTestDependencies()
            configureQuality()
        }
    }
}
