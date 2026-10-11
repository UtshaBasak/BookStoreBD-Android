package bd.bookstore.buildlogic

import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

/** ktlint for formatting and detekt for code smells, on every module. */
internal fun Project.configureQuality() {
    pluginManager.apply("org.jlleitschuh.gradle.ktlint")
    pluginManager.apply("io.gitlab.arturbosch.detekt")

    extensions.configure<KtlintExtension> {
        version.set(libs.findVersion("ktlint").get().requiredVersion)
        reporters {
            reporter(ReporterType.PLAIN)
            reporter(ReporterType.SARIF)
        }
        filter {
            exclude { it.file.path.contains("${java.io.File.separator}build${java.io.File.separator}") }
        }
    }

    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        parallel = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        baseline = file("detekt-baseline.xml")
    }

    // ktlint-gradle logs through logback 1.3, which has known issues; 1.5.34 has the fixes.
    configurations.matching { it.name == "ktlint" }.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "ch.qos.logback") useVersion("1.5.34")
        }
    }

    // detekt 1.23 is built against Kotlin 2.0.21 and must run on that compiler,
    // whatever version the project itself compiles with.
    configurations.matching { it.name == "detekt" }.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin") useVersion("2.0.21")
        }
    }

    // Android Lint runs from its own classpath in each module, which the root
    // build file's constraints do not reach: the same patched versions here.
    configurations.matching { it.name == "androidLintTool" }.configureEach {
        resolutionStrategy.eachDependency {
            PATCHED_LINT_LIBRARIES["${requested.group}:${requested.name}"]?.let(::useVersion)
        }
    }
}

private val PATCHED_LINT_LIBRARIES =
    mapOf(
        "org.bouncycastle:bcprov-jdk18on" to "1.86",
        "org.bouncycastle:bcpkix-jdk18on" to "1.86",
        "org.bouncycastle:bcutil-jdk18on" to "1.86",
        "org.apache.commons:commons-lang3" to "3.21.0",
        "org.apache.httpcomponents:httpclient" to "4.5.14",
    )
