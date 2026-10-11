plugins {
    alias(libs.plugins.bookstore.android.library)
    alias(libs.plugins.bookstore.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.utshabasak.bookstorebd.core.data"
}

dependencies {
    api(projects.core.model)
    api(projects.core.common)
    implementation(projects.core.network)
    implementation(projects.core.database)
    implementation(projects.core.datastore)

    api(libs.androidx.paging.runtime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(projects.core.testing)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.androidx.paging.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
