plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.phani.pdfkit"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.phani.pdfkit"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "0.1." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
        vectorDrawables { useSupportLibrary = true }
    }

    // Dev key: every test APK installs over the previous one. The Play bundle is signed with the
    // private upload key, which CI reads from GitHub secrets (never stored in the repo).
    val uploadStore = System.getenv("UPLOAD_STORE_FILE")
    val playBundle = System.getenv("PLAY_BUNDLE") == "1" && uploadStore != null
    signingConfigs {
        create("dev") {
            storeFile = file("keystore/dev.jks")
            storePassword = "pdfkitdev"
            keyAlias = "pdfkit"
            keyPassword = "pdfkitdev"
        }
        if (uploadStore != null) create("upload") {
            storeFile = file(uploadStore)
            storePassword = System.getenv("UPLOAD_STORE_PASSWORD")
            keyAlias = System.getenv("UPLOAD_KEY_ALIAS")
            keyPassword = System.getenv("UPLOAD_KEY_PASSWORD") ?: System.getenv("UPLOAD_STORE_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName(if (playBundle) "upload" else "dev")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            signingConfig = signingConfigs.getByName("dev")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.coil.compose)
    implementation(libs.pdfbox.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.exifinterface)
    implementation(libs.mlkit.docscanner)
    implementation(libs.androidx.biometric)
    implementation(libs.mlkit.genai.summarization)
    implementation(libs.mlkit.text)
    implementation(libs.mlkit.text.devanagari)
    implementation(libs.kotlinx.coroutines.play.services)
    debugImplementation(libs.compose.ui.tooling)
}
