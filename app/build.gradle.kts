plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

import java.util.Properties

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        load(localPropertiesFile.inputStream())
    }
}

android {
    namespace = "com.nuvio.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nuvio.tv"
        minSdk = 26
        targetSdk = 36
        versionCode = 9
        versionName = "0.3.0-beta"

        buildConfigField("String", "PARENTAL_GUIDE_API_URL", "\"${localProperties.getProperty("PARENTAL_GUIDE_API_URL", "")}\"")
        buildConfigField("String", "INTRODB_API_URL", "\"${localProperties.getProperty("INTRODB_API_URL", "")}\"")
        buildConfigField("String", "TRAILER_API_URL", "\"${localProperties.getProperty("TRAILER_API_URL", "")}\"")
        buildConfigField("String", "TRAKT_CLIENT_ID", "\"${localProperties.getProperty("TRAKT_CLIENT_ID", "")}\"")
        buildConfigField("String", "TRAKT_CLIENT_SECRET", "\"${localProperties.getProperty("TRAKT_CLIENT_SECRET", "")}\"")
        buildConfigField("String", "TRAKT_API_URL", "\"${localProperties.getProperty("TRAKT_API_URL", "https://api.trakt.tv/")}\"")

        // In-app updater (GitHub Releases)
        buildConfigField("String", "GITHUB_OWNER", "\"tapframe\"")
        buildConfigField("String", "GITHUB_REPO", "\"NuvioTV\"")
    }

    signingConfigs {
        create("release") {
            keyAlias = "nuviotv"
            keyPassword = "815787"
            storeFile = file("../nuviotv.jks")
            storePassword = "815787"
        }
    }

    buildTypes {
        debug {
            isDebuggable = false
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Globally exclude stock media3-exoplayer and media3-ui — replaced by forked local AARs
configurations.all {
    exclude(group = "androidx.media3", module = "media3-exoplayer")
    exclude(group = "androidx.media3", module = "media3-ui")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi)
    ksp(libs.moshi.codegen)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Image Loading
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)

    // Navigation
    implementation(libs.navigation.compose)

    // DataStore
    implementation(libs.datastore.preferences)

    // ViewModel
    implementation(libs.lifecycle.viewmodel.compose)

    // Media3 ExoPlayer — using custom forked ExoPlayer from local AARs (like Just Player)
    // The forked lib-exoplayer-release.aar replaces stock media3-exoplayer (globally excluded above)
    // lib-ui-release.aar replaces stock media3-ui (globally excluded above)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.exoplayer.smoothstreaming)
    implementation(libs.media3.exoplayer.rtsp)
    implementation(libs.media3.datasource)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.decoder)
    implementation(libs.media3.session)
    implementation(libs.media3.common)
    implementation(libs.media3.container)
    implementation(libs.media3.extractor)

    
    // Local AAR libraries from forked ExoPlayer (matching Just Player setup):
    // - lib-exoplayer-release.aar    — Custom forked ExoPlayer core (replaces media3-exoplayer)
    // - lib-ui-release.aar           — Custom forked ExoPlayer UI
    // - lib-decoder-ffmpeg-release.aar — FFmpeg audio decoders (vorbis,opus,flac,alac,pcm,mp3,amr,aac,ac3,eac3,dca,mlp,truehd)
    // - lib-decoder-av1-release.aar  — AV1 software video decoder (libgav1)
    // - lib-decoder-iamf-release.aar — IAMF immersive audio decoder
    // - lib-decoder-mpegh-release.aar — MPEG-H 3D audio decoder
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("lib-*.aar"))))

    // libass-android for ASS/SSA subtitle support (from Maven Central)
    implementation("io.github.peerless2012:ass-media:0.4.0-beta01")

    // Local Plugin System
    implementation(libs.quickjs.kt)
    implementation(libs.jsoup)
    implementation(libs.gson)

    // Markdown rendering
    implementation(libs.markdown.renderer.m3)

    // Bundle real crypto-js (JS) for QuickJS plugins
    implementation(libs.crypto.js)
    // QR code + local server for addon management
    implementation(libs.nanohttpd)
    implementation(libs.zxing.core)

    // Bundle real crypto-js (JS) for QuickJS plugins
    implementation("org.webjars.npm:crypto-js:4.2.0")

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("io.mockk:mockk:1.13.12")
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
