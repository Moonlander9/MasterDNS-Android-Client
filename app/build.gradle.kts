import java.io.File
import java.util.Properties

val releaseProperties = Properties()
val releasePropertiesFile = rootProject.file("keystore.properties")
if (releasePropertiesFile.isFile) {
    releasePropertiesFile.inputStream().use { releaseProperties.load(it) }
}

fun resolveBuildProperty(name: String): String? {
    val gradleValue = providers.gradleProperty(name).orNull
    if (!gradleValue.isNullOrBlank()) {
        return gradleValue
    }
    val envValue = providers.environmentVariable(name).orNull
    if (!envValue.isNullOrBlank()) {
        return envValue
    }
    return releaseProperties.getProperty(name)?.takeIf { it.isNotBlank() }
}

val releaseStoreFilePath = resolveBuildProperty("RELEASE_STORE_FILE")
val releaseStorePassword = resolveBuildProperty("RELEASE_STORE_PASSWORD")
val releaseKeyAlias = resolveBuildProperty("RELEASE_KEY_ALIAS")
val releaseKeyPassword = resolveBuildProperty("RELEASE_KEY_PASSWORD")
val hasReleaseSigning = !releaseStoreFilePath.isNullOrBlank() &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

val defaultVersionCode = 4
val defaultVersionName = "1.0.2"
val appVersionCode = resolveBuildProperty("APP_VERSION_CODE")?.toIntOrNull() ?: defaultVersionCode
val appVersionName = resolveBuildProperty("APP_VERSION_NAME") ?: defaultVersionName

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
}

android {
    namespace = "com.masterdnsvpn.android"
    compileSdk = 35

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFilePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    defaultConfig {
        applicationId = "com.masterdnsvpn.android.scanner"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // Ship a broadly-installable APK across common real devices and emulators.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("String", "UI_BUILD_MARKER", "\"release\"")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            isMinifyEnabled = false
            versionNameSuffix = "-debug"
            buildConfigField("String", "UI_BUILD_MARKER", "\"debug-20260312-01\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

}

chaquopy {
    defaultConfig {
        val buildPythonEnv = System.getenv("CHAQUOPY_BUILD_PYTHON")
        val pathEntries = System.getenv("PATH")
            ?.split(File.pathSeparator)
            ?.filter { it.isNotBlank() }
            .orEmpty()
        val buildPythonCandidates = listOf(
            "python3.11",
            "/opt/homebrew/bin/python3.11",
            "/usr/local/bin/python3.11",
            "/opt/homebrew/bin/python3.12",
            "/usr/local/bin/python3.12",
            "python3.10",
            "python3.12",
        )
        val buildPythonCmd = if (!buildPythonEnv.isNullOrBlank()) {
            buildPythonEnv
        } else {
            // GUI-launched Android Studio on macOS may not inherit Homebrew in PATH.
            buildPythonCandidates.firstOrNull { candidate ->
                if (candidate.contains("/")) {
                    val file = File(candidate)
                    file.isFile && file.canExecute()
                } else {
                    pathEntries.any { dir ->
                        val file = File(dir, candidate)
                        file.isFile && file.canExecute()
                    }
                }
            } ?: "python3.11"
        }
        buildPython(buildPythonCmd)
        version = "3.11"
        pip {
            install("loguru")
            install("cryptography")
            install("zstandard")
            install("lz4")
            install("tomli")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.appcompat:appcompat:1.7.0")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.runtime:runtime-saveable")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.6")
    implementation("com.google.zxing:core:3.5.3")
    implementation("dnsjava:dnsjava:3.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
