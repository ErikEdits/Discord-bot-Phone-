// The Android Gradle plugin must live in the root build classloader next to the Kotlin plugin
// (otherwise kotlin-android cannot see AGP classes). It is put on the classpath here instead of
// the plugins block so the pure JVM modules can still be built without Google's Maven repo
// (POCKETBOT_SKIP_ANDROID=1). Keep the version in sync with `agp` in gradle/libs.versions.toml.
buildscript {
    repositories {
        if (System.getenv("POCKETBOT_SKIP_ANDROID") != "1") google()
        mavenCentral()
    }
    dependencies {
        if (System.getenv("POCKETBOT_SKIP_ANDROID") != "1") {
            classpath("com.android.tools.build:gradle:8.10.1")
        }
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
