plugins {
    alias(libs.plugins.bookstore.android.library)
    alias(libs.plugins.bookstore.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.utshabasak.bookstorebd.core.network"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)

    api(libs.okhttp)
    implementation(libs.okhttp.logging)
    api(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    api(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.okhttp.mockwebserver)
}
