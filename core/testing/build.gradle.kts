plugins {
    alias(libs.plugins.bookstore.android.library)
}

android {
    namespace = "io.github.utshabasak.bookstorebd.core.testing"
}

dependencies {
    api(libs.junit4)
    api(libs.truth)
    api(libs.mockk)
    api(libs.turbine)
    api(libs.kotlinx.coroutines.test)
    implementation(projects.core.common)
}
