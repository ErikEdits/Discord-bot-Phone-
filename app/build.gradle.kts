import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
}

// Public address of the PocketBot gateway worker (not a secret). Set the GitHub repo
// variable POCKETBOT_API_URL or the Gradle property pocketbot.apiUrl.
val apiUrl: String = System.getenv("POCKETBOT_API_URL")?.takeIf { it.isNotBlank() }
    ?: (findProperty("pocketbot.apiUrl") as String?)
    ?: "https://pocketbot-gateway.example.workers.dev"

// Signing from CI secrets (see .github/workflows/android.yml). Without them the
// debug key is used for debug builds and release builds stay unsigned.
val keystoreFile = System.getenv("POCKETBOT_KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.exists() }

android {
    namespace = "de.erikedits.pocketbot"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.erikedits.pocketbot"
        minSdk = 26
        targetSdk = 35
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "0.1.0"
        buildConfigField("String", "POCKETBOT_API_URL", "\"$apiUrl\"")
        buildConfigField("String", "GITHUB_REPO", "\"ErikEdits/Discord-bot-Phone-\"")
    }

    signingConfigs {
        if (keystoreFile != null) {
            create("pocketbot") {
                storeFile = keystoreFile
                storePassword = System.getenv("POCKETBOT_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("POCKETBOT_KEY_ALIAS")
                keyPassword = System.getenv("POCKETBOT_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            if (keystoreFile != null) signingConfig = signingConfigs.getByName("pocketbot")
        }
        release {
            // JDA + Jackson use reflection; shrinking comes later with proper rules.
            isMinifyEnabled = false
            if (keystoreFile != null) signingConfig = signingConfigs.getByName("pocketbot")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/INDEX.LIST",
                "META-INF/*.md",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "META-INF/versions/9/module-info.class",
                "module-info.class",
            )
        }
    }

    lint {
        // Missing translations must fail the build: every string exists in EN and DE.
        error += "MissingTranslation"
        abortOnError = true
        checkReleaseBuilds = true
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":engine"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.browser)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
