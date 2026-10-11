plugins {
    alias(libs.plugins.bookstore.android.feature)
}

android {
    namespace = "io.github.utshabasak.bookstorebd.feature.selling"
}

dependencies {
    implementation(projects.core.scanner)
    implementation(libs.androidx.paging.compose)
    implementation(libs.coil.compose)
}
