plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)

    // KSP Gradle Plugin
    id("com.google.devtools.ksp")

    // Hilt Gradle Plugin
    id("kotlin-kapt")
    id("com.google.dagger.hilt.android")

    // Kotlinx Serialization Plugin
    kotlin("plugin.serialization") version "2.0.0"
}


android {
    namespace = "com.temi.oh2024"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.temi.oh2024"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.documentfile)

    // Temi SDK
    implementation("com.robotemi:sdk:1.133.0")

    //GID
    implementation("io.coil-kt:coil:2.7.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-gif:2.6.0")

    // Dependencies for Hilt
    implementation("com.google.dagger:hilt-android:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    kapt("com.google.dagger:hilt-android-compiler:2.51.1")

    // Android Activity Library
    implementation("androidx.activity:activity-ktx:1.9.0")

    // JSON serialization library dependency
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Ktor
    implementation ("io.ktor:ktor-client-android:2.2.4")

    // OpenAI
    implementation ("com.aallam.openai:openai-client:3.8.2")

    // Accompanist Permissions
    implementation("com.google.accompanist:accompanist-permissions:0.36.0") // Replace <latest-ver
}

kapt {
    correctErrorTypes = true
}