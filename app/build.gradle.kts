import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// The upload key stays out of Git. Set it in keystore.properties (Git ignores this file) or in CVKE_UPLOAD_*
// environment variables. See README.md. Without an upload key, the release build is unsigned.
val keystoreProperties = providers.fileContents(rootProject.layout.projectDirectory.file("keystore.properties")).asText
    .map { text -> Properties().apply { load(text.reader()) } }

fun uploadKeySetting(property: String, variable: String): String? = providers.environmentVariable(variable)
    .orElse(keystoreProperties.map { it.getProperty(property, "") }).orNull?.takeIf { it.isNotBlank() }

val uploadStoreFile = uploadKeySetting("storeFile", "CVKE_UPLOAD_STORE_FILE")

android {
    namespace = "com.cvke.fridge"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.cvke.fridge"
        minSdk = 29
        targetSdk = 37
        // Increase versionCode for each upload to Google Play. Play refuses a code that it already has.
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (uploadStoreFile != null) {
            create("upload") {
                fun required(property: String, variable: String) = uploadKeySetting(property, variable)
                    ?: error("Set $property in keystore.properties or $variable for the upload key.")
                storeFile = rootProject.file(uploadStoreFile)
                storePassword = required("storePassword", "CVKE_UPLOAD_STORE_PASSWORD")
                keyAlias = required("keyAlias", "CVKE_UPLOAD_KEY_ALIAS")
                keyPassword = required("keyPassword", "CVKE_UPLOAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // A debug build and the Play build can be on the same phone. They have separate data.
            applicationIdSuffix = ".debug"
        }
        release {
            signingConfig = signingConfigs.findByName("upload")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        abortOnError = true
        warningsAsErrors = true
        // Dependency updates need a compatibility review, not a network-dependent build failure.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
}

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
