plugins {
    alias(libs.plugins.bookstore.android.feature)
}

android {
    namespace = "io.github.utshabasak.bookstorebd.feature.profile"
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.coil.compose)
}
