plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.creditriskpredictor"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.creditriskpredictor"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // ONNX model is placed in assets/
    androidResources {
        noCompress += listOf("onnx")
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // ONNX Runtime for Android (on-device ML inference)
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}