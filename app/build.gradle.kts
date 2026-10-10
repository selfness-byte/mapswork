import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
    id("com.google.devtools.ksp")
}

val googleMapsKey = providers.environmentVariable("MAPSWORK_GOOGLE_MAPS_API_KEY").orNull?.trim()
    ?: Properties().apply {
        val propertiesFile = rootProject.file("local.properties")
        if (propertiesFile.isFile) propertiesFile.inputStream().use { load(it) }
    }.getProperty("mapswork.googleMapsApiKey", "").trim()
require(googleMapsKey.isEmpty() || Regex("[A-Za-z0-9_-]{20,200}").matches(googleMapsKey)) {
    "Google Maps key must be supplied as a real Android API key, not a proxy placeholder."
}

val firebaseHostingSite = providers.environmentVariable("MAPSWORK_FIREBASE_HOSTING_SITE").orNull?.trim()
    ?: Properties().apply {
        val propertiesFile = rootProject.file("local.properties")
        if (propertiesFile.isFile) propertiesFile.inputStream().use { load(it) }
    }.getProperty("mapswork.firebaseHostingSite", "inanimals-1977").trim()
require(firebaseHostingSite.isEmpty() || Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?").matches(firebaseHostingSite)) {
    "Firebase Hosting site must be a DNS label."
}

android {
    namespace = "com.selfness.mapswork"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "com.selfness.mapswork"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "0.3.0"
        resValue("string", "google_maps_api_key", googleMapsKey)
        buildConfigField("boolean", "HAS_GOOGLE_MAPS_KEY", googleMapsKey.isNotEmpty().toString())
        buildConfigField("String", "FIREBASE_HOSTING_SITE", "\"$firebaseHostingSite\"")
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isIncludeAndroidResources = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    lint { abortOnError = true }
    signingConfigs.getByName("debug") {
        System.getenv("ANDROID_USER_HOME")?.let { storeFile = file("$it/debug.keystore") }
    }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2025.05.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation("com.google.android.gms:play-services-maps:19.2.0")
    implementation("com.google.android.libraries.places:places:4.3.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("test-junit"))
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
