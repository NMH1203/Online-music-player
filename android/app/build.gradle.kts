plugins {
    alias(libs.plugins.android.application)
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
            buildConfigField("String", "BASE_URL", "\"https://dev.api.musicplayer.com/\"")
        }
        release {
            buildConfigField("String", "BASE_URL", "\"https://api.musicplayer.com/\"")
            isMinifyEnabled = false
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

    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)

    // ... Giữ nguyên các dòng implementation cũ của dự án ở đây ...

        // Thêm đoạn cấu hình xử lý xung đột này vào cuối khối:
    constraints {
            implementation("androidx.transition:transition:1.4.1") {
                because("Xử lý xung đột trùng lặp phiên bản khi hạ AGP")
            }
    }


}

configurations.all {
    resolutionStrategy {
        force("androidx.transition:transition:1.4.1")
    }
}
