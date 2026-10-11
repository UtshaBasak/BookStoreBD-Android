plugins {
    alias(libs.plugins.bookstore.android.library)
    alias(libs.plugins.bookstore.android.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "io.github.utshabasak.bookstorebd.core.ui"
}

dependencies {
    api(projects.core.designsystem)
    api(projects.core.model)
    api(libs.androidx.navigation3.runtime)
    api(libs.coil.compose)
    api(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.coil.test)
}
