// AGP 9 has Kotlin support built in, so there is no kotlin-android plugin here. Adding it fails
// the build outright: "no longer required for Kotlin support since AGP 9.0".
plugins {
    alias(libs.plugins.android.application) apply false
}
