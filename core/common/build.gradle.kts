plugins {
    alias(libs.plugins.bookstore.android.library)
    alias(libs.plugins.bookstore.hilt)
}

android {
    namespace = "io.github.utshabasak.bookstorebd.core.common"
}

dependencies {
    api(libs.kotlinx.coroutines.android)
}
