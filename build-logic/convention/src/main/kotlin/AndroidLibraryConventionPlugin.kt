import bd.bookstore.buildlogic.addUnitTestDependencies
import bd.bookstore.buildlogic.configureKotlinAndroid
import bd.bookstore.buildlogic.configureQuality
import bd.bookstore.buildlogic.intVersion
import bd.bookstore.buildlogic.libs
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")

            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
                // Libraries have no target SDK of their own; tests and lint use the app's.
                testOptions.targetSdk = libs.intVersion("targetSdk")
                lint.targetSdk = libs.intVersion("targetSdk")
                testOptions.animationsDisabled = true
                // Each library's resources are prefixed by its own name, so two can never collide.
                resourcePrefix = path.split(":").drop(1).joinToString("_").lowercase() + "_"
            }
            addUnitTestDependencies()
            configureQuality()
        }
    }
}
