import java.util.Properties

plugins {
    alias(libs.plugins.bookstore.android.application)
    alias(libs.plugins.bookstore.android.compose)
    alias(libs.plugins.bookstore.hilt)
    alias(libs.plugins.kotlin.serialization)
}

/*
 * The version, in one place. Release tags are `v<versionName>`, and the
 * release workflow refuses a tag that does not match. The version code is
 * derived, so it always increases with the version: 1.2.3 becomes 10203.
 */
val appVersionName = "0.1.0"
val appVersionCode =
    appVersionName.split(".").map(String::toInt).let { (major, minor, patch) ->
        major * 10_000 + minor * 100 + patch
    }

/*
 * Release signing. In GitHub Actions the keystore and its passwords come from
 * encrypted repository secrets, through environment variables. On a developer
 * machine they can come from `keystore.properties` (git-ignored). With neither,
 * the release build is left unsigned.
 */
val localSigning =
    Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.exists()) file.inputStream().use(::load)
    }

fun signingValue(
    env: String,
    key: String,
): String? = providers.environmentVariable(env).orNull ?: localSigning.getProperty(key)

val releaseStoreFile = signingValue("RELEASE_KEYSTORE_PATH", "storeFile")

/*
 * Where the app finds the shop. Release builds always use the live site. A
 * debug build can be pointed at a server on the developer's machine for
 * end-to-end testing: -Pbookstore.debugSite=http://127.0.0.1:4100 together with
 * `adb reverse tcp:4100 tcp:4100` (emulator or phone), or http://10.0.2.2:4100
 * (the emulator's name for the host computer).
 */
val liveSite = "https://bookstorebd-loum.onrender.com"
val debugSite = providers.gradleProperty("bookstore.debugSite").orNull ?: liveSite

android {
    namespace = "io.github.utshabasak.bookstorebd"

    defaultConfig {
        applicationId = "io.github.utshabasak.bookstorebd"
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "API_BASE_URL", "\"$liveSite/api/v1/\"")
        buildConfigField("String", "SITE_URL", "\"$liveSite\"")
        buildConfigField("String", "RELEASES_URL", "\"https://github.com/UtshaBasak/BookStoreBD-Android/releases\"")
    }

    buildFeatures {
        buildConfig = true
    }

    androidResources {
        // English and Bangla only: strings from libraries in other languages are left out.
        localeFilters += listOf("en", "bn")
        generateLocaleConfig = true
    }

    signingConfigs {
        getByName("debug") {
            // One debug key for every machine and for CI, so the debug build's
            // Google sign-in client matches wherever it was built.
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = signingValue("RELEASE_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("RELEASE_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("RELEASE_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField("String", "API_BASE_URL", "\"$debugSite/api/v1/\"")
            buildConfigField("String", "SITE_URL", "\"$debugSite\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    /*
     * Where the app is distributed. `github` is the APK on GitHub Releases, with
     * its own update check; `play` is for the Play Store later, where Play
     * delivers updates and an app may not install packages itself.
     */
    flavorDimensions += "distribution"
    productFlavors {
        create("github") {
            dimension = "distribution"
            buildConfigField("boolean", "SELF_UPDATE", "true")
        }
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "SELF_UPDATE", "false")
        }
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/LICENSE*", "/META-INF/NOTICE*")
    }

    dependenciesInfo {
        // The dependency block in an APK is readable only by Google; the GitHub
        // APK has no use for it. Bundles for Play keep it.
        includeInApk = false
        includeInBundle = true
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.data)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.designsystem)
    implementation(projects.core.model)
    implementation(projects.core.network)
    implementation(projects.core.ui)
    implementation(projects.feature.auth)
    implementation(projects.feature.book)
    implementation(projects.feature.cart)
    implementation(projects.feature.catalogue)
    implementation(projects.feature.checkout)
    implementation(projects.feature.home)
    implementation(projects.feature.orders)
    implementation(projects.feature.profile)
    implementation(projects.feature.reviews)
    implementation(projects.feature.settings)
    implementation(projects.feature.shop)
    implementation(projects.feature.wanted)
    implementation(projects.feature.wishlist)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(projects.core.testing)
}
