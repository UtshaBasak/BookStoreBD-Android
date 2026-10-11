plugins {
    alias(libs.plugins.bookstore.android.feature)
}

android {
    namespace = "io.github.utshabasak.bookstorebd.feature.selling"
}

dependencies {
    implementation(libs.androidx.paging.compose)
    implementation(libs.coil.compose)
}
