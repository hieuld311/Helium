plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.hieuld.helium"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.hieuld.helium"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

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
}

dependencies {
    // UI & Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.swiperefreshlayout)

    // Architecture
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.startup)

    // Media & Tools
    implementation(libs.androidx.media)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.tracing)
    implementation(libs.androidx.print)

    // Google Services & Google Drive API
//    implementation(libs.google.play.auth)
//    implementation(libs.google.play.base)
//    implementation(libs.google.api.drive)
//    implementation(libs.google.api.client.android)

    // Data execution
    implementation(libs.google.gson)
    implementation(libs.google.guava)
    implementation(libs.google.protobuf.lite)

    // Third Party
    implementation(libs.github.glide)
    implementation(libs.jsoup)
    implementation(libs.colorpicker)
    implementation(libs.photoview)

    // Testing
    testImplementation(libs.test.junit)
    androidTestImplementation(libs.test.espresso.core)
}