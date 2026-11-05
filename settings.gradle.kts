val jvmOnly = System.getenv("POCKETBOT_SKIP_ANDROID") == "1"

pluginManagement {
    repositories {
        if (System.getenv("POCKETBOT_SKIP_ANDROID") != "1") google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (!jvmOnly) google()
        mavenCentral()
    }
}

rootProject.name = "PocketBot"

include(":shared", ":engine")

// Set POCKETBOT_SKIP_ANDROID=1 to build only the pure Kotlin/JVM modules
// (e.g. on a machine without the Android SDK).
if (!jvmOnly) {
    include(":app")
}
