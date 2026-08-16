plugins {
    id("com.android.application")
}

android {
    namespace = "com.mlevngr.inknote"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mlevngr.inknote"
        minSdk = 28
        targetSdk = 36
        versionCode = 38
        versionName = "0.7.10"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    testOptions {
        unitTests.isIncludeAndroidResources = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("com.google.android.material:material:1.14.0")

    val markwon = "4.6.2"
    implementation("io.noties.markwon:core:$markwon")
    implementation("io.noties.markwon:ext-strikethrough:$markwon")
    implementation("io.noties.markwon:ext-tables:$markwon")
    implementation("io.noties.markwon:ext-tasklist:$markwon")

    testImplementation("junit:junit:4.13.2")
}
