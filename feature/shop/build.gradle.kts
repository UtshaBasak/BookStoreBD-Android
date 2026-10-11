plugins {
    alias(libs.plugins.bookstore.android.feature)
}

android {
    namespace = "io.github.utshabasak.bookstorebd.feature.shop"
}

dependencies {
    implementation(libs.androidx.paging.compose)
}
