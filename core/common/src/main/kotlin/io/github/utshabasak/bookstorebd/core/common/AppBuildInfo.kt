package io.github.utshabasak.bookstorebd.core.common

/** The build, as the app module knows it: shown in About, and deciding whether the app updates itself. */
data class AppBuildInfo(
    val versionName: String,
    val versionCode: Int,
    /** True in the GitHub build, which installs its own updates; false in the Play build. */
    val selfUpdate: Boolean,
    /** Where the APKs are published, for "see all releases". */
    val releasesUrl: String,
)
