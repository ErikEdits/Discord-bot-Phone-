import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    api(project(":shared"))
    // No voice/music in PocketBot -> drop the native opus library.
    api(libs.jda) { exclude(module = "opus-java") }

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
