// The Android Gradle plugin is declared in app/build.gradle.kts only, so the
// pure JVM modules can be built without the Android SDK (POCKETBOT_SKIP_ANDROID=1).
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
