plugins {
    alias(libs.plugins.bookstore.android.feature)
}

android {
    namespace = "io.github.utshabasak.bookstorebd.feature.settings"
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.coil.compose)
}
