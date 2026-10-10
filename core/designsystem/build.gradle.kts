plugins {
    alias(libs.plugins.bookstore.android.library)
    alias(libs.plugins.bookstore.android.compose)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "io.github.utshabasak.bookstorebd.core.designsystem"
}

dependencies {
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons.extended)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)

    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
}
