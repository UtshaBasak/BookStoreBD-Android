plugins {
    alias(libs.plugins.bookstore.android.library)
    alias(libs.plugins.bookstore.android.compose)
}

android {
    namespace = "io.github.utshabasak.bookstorebd.core.scanner"
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.compose)
    // Reads the barcode on the phone itself: open source, no network and no Google services.
    implementation(libs.zxing.core)
}
