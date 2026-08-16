plugins {
    id("com.android.application")
}

android {
    namespace = "com.mlevngr.mote.plugin.ai"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mlevngr.mote.plugin.ai.organizer"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        create("stableDevelopment") {
            storeFile = rootProject.file("signing/inknote-dev.jks")
            storePassword = "inknote-dev-v1"
            keyAlias = "inknote"
            keyPassword = "inknote-dev-v1"
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("stableDevelopment")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":plugin-api"))
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.14.0")
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    testImplementation("junit:junit:4.13.2")
}
