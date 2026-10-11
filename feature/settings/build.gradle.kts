plugins {
    alias(libs.plugins.bookstore.android.feature)
}

android {
    namespace = "io.github.utshabasak.bookstorebd.feature.settings"
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.browser)
    implementation(libs.coil.compose)
}
