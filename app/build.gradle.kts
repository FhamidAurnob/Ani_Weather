import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

// Read secrets from local.properties (never committed to git)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val openWeatherApiKey: String = localProps.getProperty("OPENWEATHER_API_KEY")
    ?: System.getenv("OPENWEATHER_API_KEY")
    ?: ""

android {
    namespace = "com.example.wuwaweather"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.fhamid.velora"
        minSdk = 28
        targetSdk = 34
        versionCode = 2
        versionName = "2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "OPENWEATHER_API_KEY", "\"$openWeatherApiKey\"")
    }
    buildFeatures {
        compose = true      // Jetpack Compose UI
        buildConfig = true  // Exposes BuildConfig.OPENWEATHER_API_KEY
    }
    composeOptions {
        // Must match the Kotlin version (1.9.0 -> 1.5.1)
        kotlinCompilerExtensionVersion = "1.5.1"
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
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("com.airbnb.android:lottie:6.1.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Jetpack Compose
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Looping video backgrounds for "Real" mode
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    // Home-screen widget
    implementation(libs.androidx.glance.appwidget)

    // Background earthquake checks
    implementation(libs.androidx.work.runtime)
}