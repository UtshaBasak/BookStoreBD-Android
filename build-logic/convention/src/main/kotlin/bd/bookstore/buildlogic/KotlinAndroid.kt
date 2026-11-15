package bd.bookstore.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/** The settings every Android module shares, application and library alike. */
internal fun Project.configureKotlinAndroid(android: CommonExtension) {
    android.apply {
        compileSdk = libs.intVersion("compileSdk")
        defaultConfig.minSdk = libs.intVersion("minSdk")

        compileOptions.sourceCompatibility = JavaVersion.VERSION_17
        compileOptions.targetCompatibility = JavaVersion.VERSION_17

        // Robolectric reads the merged resources, so tests see the real strings and themes.
        testOptions.unitTests.isIncludeAndroidResources = true
        // One Robolectric configuration for every module (config/robolectric).
        sourceSets.getByName("test").resources.directories.add(rootProject.file("config/robolectric").path)

        lint.apply {
            abortOnError = true
            warningsAsErrors = true
            checkReleaseBuilds = true
            // Dependency updates are Dependabot's job, not a reason to fail a build.
            disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
            // A library's own lint checks built for an older Lint are skipped by Lint,
            // which then reports the library; that is the library's to fix, not a
            // reason to fail this build. (androidx.lifecycle's two checks, today.)
            disable += "ObsoleteLintCustomCheck"
        }
    }
    configureKotlin()
}

/** Kotlin compiler settings for every module. */
internal fun Project.configureKotlin() {
    configureTestsuite()
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            allWarningsAsErrors.set(true)
            // Tests may use the coroutine test dispatchers, which are still marked experimental.
            if (name.contains("Test")) freeCompilerArgs.add("-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi")
        }
    }
}

/** For pure Kotlin modules, which have no Android extension. */
internal fun Project.configureKotlinJvm() {
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    configureKotlin()
}

/**
 * Hook for CircleCI Smarter Testing (`circleci testsuite`, see .circleci/test-suites.yml): the
 * `testsuiteTests` property, set only by that tool, is a comma-separated list of test classes to
 * run. Modules with none of them still pass, and the tests always run (never up to date), because
 * the tool asked for exactly these. Normal `./gradlew test` runs are unchanged.
 */
internal fun Project.configureTestsuite() {
    val selected = providers.gradleProperty("testsuiteTests").orNull ?: return
    val classes = selected.split(',').map(String::trim).filter(String::isNotEmpty)
    tasks.withType<Test>().configureEach {
        classes.forEach(filter::includeTestsMatching)
        filter.isFailOnNoMatchingTests = false
        outputs.upToDateWhen { false }
    }
}
