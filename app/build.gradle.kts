plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// ---------------------------------------------------------------------------
// Release signing.
//
// Reads keystore settings from environment variables so the GitHub Actions
// pipeline can sign with a stable key (repository secret KEYSTORE_BASE64) or
// an ephemeral CI keystore. When nothing is configured (local development),
// the release build falls back to the debug keystore so APKs remain
// installable out of the box.
// ---------------------------------------------------------------------------
val envKeystore = System.getenv("KEYSTORE_FILE")
val hasReleaseKeystore = !envKeystore.isNullOrBlank() && file(envKeystore).exists()

android {
    namespace = "com.apex.files"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.apex.files"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        // The release workflow exports VERSION_TAG (v1.0.<run_number>); strip
        // the leading "v" so installed builds show the real release version.
        versionName = (System.getenv("VERSION_TAG") ?: "v1.0").removePrefix("v")

        // Native ABI support: arm64-v8a (current devices), armeabi-v7a
        // (legacy 32-bit), x86_64 (emulators / x64 devices). The app is
        // 100% Kotlin today, but the filter pins the supported ABIs and
        // prepares the packaging for any future native code.
        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a", "x86_64"))
        }
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(envKeystore!!)
                storePassword = System.getenv("KEYSTORE_PASSWORD").orEmpty()
                keyAlias = System.getenv("KEY_ALIAS") ?: "apex"
                keyPassword = (System.getenv("KEY_PASSWORD") ?: System.getenv("KEYSTORE_PASSWORD")).orEmpty()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    // Per-ABI optimized APK splits (one lean APK per architecture) plus a
    // universal APK. Consumes only the machine code relevant to each device.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/NOTICE*"
        }
        jniLibs {
            // Keep native libs compressed and unmapped at install time.
            useLegacyPackaging = false
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

    lint {
        abortOnError = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.coil.compose)
    implementation(libs.androidx.documentfile)
    testImplementation(libs.junit)
}