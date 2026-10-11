plugins {
    alias(libs.plugins.bookstore.android.feature)
}

android {
    namespace = "io.github.utshabasak.bookstorebd.feature.catalogue"
}

dependencies {
    implementation(libs.androidx.paging.compose)
    testImplementation(libs.androidx.paging.testing)
}
