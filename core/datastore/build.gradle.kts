plugins {
    alias(libs.plugins.bookstore.android.library)
    alias(libs.plugins.bookstore.hilt)
}

android {
    namespace = "io.github.utshabasak.bookstorebd.core.datastore"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    api(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(projects.core.testing)
}
