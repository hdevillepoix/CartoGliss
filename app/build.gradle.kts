import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Optional built-in API keys, from the gitignored keys.env at the repo root (KEY=value lines, see
 * keys.env.example) or from environment variables of the same names. Missing keys build as "".
 */
val keysFile = rootProject.file("keys.env")
val keyProps = Properties().apply { if (keysFile.exists()) keysFile.reader().use { load(it) } }
fun builtInKey(name: String): String =
    (keyProps.getProperty(name)?.takeIf { it.isNotBlank() } ?: System.getenv(name) ?: "").trim().removeSurrounding("\"")
fun quoted(v: String) = "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
val keyNames = listOf("THUNDERFOREST", "MAPBOX", "TRACESTRACK", "OS", "AZURE")

android {
    namespace = "app.cartogliss"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "app.cartogliss"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        keyNames.forEach { buildConfigField("String", "KEY_$it", quoted(builtInKey("${it}_KEY"))) }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Sideloaded personal app: sign release builds with the debug key for now.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.suncalc)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
