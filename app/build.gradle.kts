plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.pepper.wansky"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.pepper.wansky"
        minSdk = 26
        targetSdk = 34
        versionCode = 10
        versionName = "0.2.7"
        // YouTube Data API key: injected from the YOUTUBE_API_KEY Actions
        // secret (or a local.properties entry for local builds). The key
        // never lives in the repo. A blank key makes the YouTube tab show
        // a "key missing" error instead of crashing.
        val ytKey = (project.findProperty("YOUTUBE_API_KEY") as String?)
            ?: System.getenv("YOUTUBE_API_KEY") ?: ""
        buildConfigField("String", "YOUTUBE_API_KEY", "\"$ytKey\"")
    }

    // Release signing (v0.2.1+): ONE stable keystore, decoded from the
    // KEYSTORE_BASE64 Actions secret by the workflow. Debug builds keep
    // using the throwaway debug key — they can never update over each
    // other, which is why releases are signed. A local build without
    // KEYSTORE_PATH set simply can't assemble a signed release.
    signingConfigs {
        create("release") {
            val ksPath = System.getenv("KEYSTORE_PATH")
            if (ksPath != null) {
                storeFile = file(ksPath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // LiveTv + SportsSoccer live in the extended icons pack, not core.
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.0")

    // Media3 (ExoPlayer) — HLS (.m3u8) live TV + R2 movie playback.
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")

    // Posters (TMDB) via Coil.
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Playlist + catalog fetching.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
