plugins {
    alias(libs.plugins.android.application)
}

val debugApiUrl = providers.gradleProperty("musicApiUrl").orElse("http://10.0.2.2:8080/").get()
require(debugApiUrl.matches(Regex("https?://[a-zA-Z0-9.:/-]+/"))) {
    "musicApiUrl must be an HTTP(S) URL ending in /"
}

val releaseApiUrl = providers.gradleProperty("musicReleaseApiUrl").orElse("").get()
require(releaseApiUrl.isEmpty() || releaseApiUrl.matches(Regex("https://[a-zA-Z0-9.:-]+(/[a-zA-Z0-9._/-]*)?/"))) {
    "musicReleaseApiUrl must be an HTTPS URL ending in /"
}

android {
    namespace = "vn.edu.usth.online_music_player"
    compileSdk = 35

    defaultConfig {
        applicationId = "vn.edu.usth.online_music_player"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    buildTypes {
        debug {
            buildConfigField("String", "BASE_URL", "\"$debugApiUrl\"")
        }
        release {
            buildConfigField("String", "BASE_URL", "\"$releaseApiUrl\"")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    
    // Navigation
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    
    // Lifecycle
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    
    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging)
    
    // Glide
    implementation(libs.glide)

    // Pull-to-refresh for the personal library.
    implementation(libs.swipe.refresh)

    // Background audio playback, media controls, and authenticated HTTP streaming.
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource.okhttp)

    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

// Prevent accidental publication with an empty or example API endpoint.
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    val configuredUrl = releaseApiUrl
    doFirst {
        require(configuredUrl.matches(Regex("https://[a-zA-Z0-9.:-]+(/[a-zA-Z0-9._/-]*)?/"))) {
            "Release requires -PmusicReleaseApiUrl=https://YOUR_BACKEND/"
        }
    }
}
