plugins {
    alias(libs.plugins.bookstore.android.feature)
}

android {
    namespace = "io.github.utshabasak.bookstorebd.feature.wanted"
}

dependencies {
    implementation(libs.androidx.paging.compose)
}
