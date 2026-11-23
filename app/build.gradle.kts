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
fun envOrNull(name: String) = builtInKey(name).takeIf { it.isNotEmpty() }
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
        // CI sets these from the git tag (see .github/workflows/release.yml).
        versionCode = envOrNull("CARTOGLISS_VERSION_CODE")?.toInt() ?: 1
        versionName = envOrNull("CARTOGLISS_VERSION_NAME") ?: "0.1.0"
        keyNames.forEach { buildConfigField("String", "KEY_$it", quoted(builtInKey("${it}_KEY"))) }
    }

    /*
     * Release signing: a keystore given by CARTOGLISS_KEYSTORE (path), CARTOGLISS_KEYSTORE_PASSWORD,
     * CARTOGLISS_KEY_ALIAS and CARTOGLISS_KEY_PASSWORD, from keys.env or the environment (CI). Without
     * them, release builds are signed with the local debug key.
     */
    val releaseKeystore = envOrNull("CARTOGLISS_KEYSTORE")?.let { rootProject.file(it) }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = envOrNull("CARTOGLISS_KEYSTORE_PASSWORD")
                keyAlias = envOrNull("CARTOGLISS_KEY_ALIAS")
                keyPassword = envOrNull("CARTOGLISS_KEY_PASSWORD") ?: envOrNull("CARTOGLISS_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
