plugins {
    alias(libs.plugins.bookstore.android.library)
    alias(libs.plugins.bookstore.android.room)
    alias(libs.plugins.bookstore.hilt)
}

android {
    namespace = "io.github.utshabasak.bookstorebd.core.database"
}

dependencies {
    implementation(projects.core.common)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(projects.core.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
