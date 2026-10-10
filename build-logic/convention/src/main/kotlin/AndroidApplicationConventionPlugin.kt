import bd.bookstore.buildlogic.addUnitTestDependencies
import bd.bookstore.buildlogic.configureKotlinAndroid
import bd.bookstore.buildlogic.configureQuality
import bd.bookstore.buildlogic.intVersion
import bd.bookstore.buildlogic.libs
import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")

            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)
                defaultConfig.targetSdk = libs.intVersion("targetSdk")
                testOptions.animationsDisabled = true
            }
            addUnitTestDependencies()
            configureQuality()
        }
    }
}
