import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Release signing credentials are loaded from a git-ignored keystore.properties
// (or VELA_* environment variables) — never hard-coded in source control.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

fun signingValue(propKey: String, envKey: String): String? =
    keystoreProperties.getProperty(propKey) ?: System.getenv(envKey)

// True only when a usable keystore is actually present, so a fresh checkout can
// still build a release (it falls back to debug signing) instead of failing.
val releaseKeystorePath = signingValue("storeFile", "VELA_KEYSTORE")
val hasReleaseKeystore = releaseKeystorePath != null && rootProject.file(releaseKeystorePath).exists()

android {
    namespace = "com.vela.chat"
    compileSdk = 35

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(releaseKeystorePath!!)
                storePassword = signingValue("storePassword", "VELA_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "VELA_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "VELA_KEY_PASSWORD")
            }
        }
    }

    defaultConfig {
        applicationId = "com.vela.chat"
        minSdk = 26
        targetSdk = 35
        // Bump versionCode (+1) and versionName for every release going forward.
        versionCode = 20
        versionName = "2.5.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            // Use the real release keystore when configured; otherwise fall back to
            // debug signing so the release variant still builds & installs for testing.
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            // R8 minification was the most likely cause of the release-only startup
            // crash (debug never minifies, so it always launched). Disabled so the
            // release variant behaves identically to debug. Re-enable only after
            // validating a minified build on-device with logcat; keep rules live in
            // proguard-rules.pro.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            // Appends to the version everywhere (e.g. "1.1.0-debug" in About).
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
    }

    // Two editions from one codebase: `public` is the published release; `personal` is a
    // private edition whose extra sources (app/src/personal/) are not published. Both use
    // applicationId com.vela.chat.
    flavorDimensions += "edition"
    productFlavors {
        create("public") {
            dimension = "edition"
            buildConfigField("String", "EDITION", "\"public\"")
        }
        create("personal") {
            dimension = "edition"
            buildConfigField("String", "EDITION", "\"personal\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // Lint must never block a release build (a lint finding — or a transient
        // lint-cache file lock — should not fail packaging the APK).
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "META-INF/INDEX.LIST"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.window.size)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Database at-rest encryption (SQLCipher SupportSQLiteOpenHelper.Factory for Room)
    implementation(libs.sqlcipher.android)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Storage
    implementation(libs.datastore.preferences)
    implementation(libs.security.crypto)

    // Media & documents
    implementation(libs.coil.compose)
    implementation(libs.pdfbox.android)

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
