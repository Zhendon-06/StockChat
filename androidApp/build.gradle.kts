import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}
val mimoApiKey = System.getenv("MIMO_API_KEY")
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?: localProperties.getProperty("MIMO_API_KEY", "").trim()
val escapedMimoApiKey = mimoApiKey
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

android {
    namespace = "com.guet.liang.stockchat"
    compileSdk = 34
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        applicationId = "com.guet.liang.stockchat"
        minSdk = 23
        targetSdk = 30
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "MIMO_API_KEY", "\"$escapedMimoApiKey\"")
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
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
    implementation(project(":shared"))

    implementation("androidx.recyclerview:recyclerview:1.2.1")
    implementation("androidx.appcompat:appcompat:1.3.1")

    implementation("com.squareup.picasso:picasso:2.71828")

    implementation("androidx.core:core-ktx:1.6.0")
    implementation("androidx.dynamicanimation:dynamicanimation:1.0.0")
    implementation("com.github.bumptech.glide:glide:4.12.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.12.0")
}
