plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "fr.cliohome"
    compileSdk = 34

    defaultConfig {
        applicationId = "fr.cliohome"
        // L'autoradio annonce Android 13 mais son processeur (MT8163) tourne souvent
        // sur une base plus ancienne : on reste compatible dès Android 7.
        minSdk = 24
        targetSdk = 33
        versionCode = 9
        versionName = "0.7"
    }

    signingConfigs {
        create("perso") {
            storeFile = file("cliohome.keystore")
            storePassword = "cliohome"
            keyAlias = "cliohome"
            keyPassword = "cliohome"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("perso")
        }
        debug {
            signingConfig = signingConfigs.getByName("perso")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // QR code pour envoyer les données au téléphone (seule la partie QR est gardée à la compilation)
    implementation("com.google.zxing:core:3.5.3")
}
