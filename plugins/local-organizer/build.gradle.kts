plugins {
    id("com.android.application")
}

android {
    namespace = "com.mlevngr.mote.plugin.local"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mlevngr.mote.plugin.local.organizer"
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
    testImplementation("junit:junit:4.13.2")
}
