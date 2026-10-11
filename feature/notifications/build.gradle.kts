plugins {
    alias(libs.plugins.bookstore.android.feature)
}

android {
    namespace = "io.github.utshabasak.bookstorebd.feature.notifications"
}

dependencies {
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.browser)
}
