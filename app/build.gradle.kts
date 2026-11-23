plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val stableSigningFile = file("auvyx-signing.jks")
val stableStorePassword = System.getenv("AUVYX_SIGNING_STORE_PASSWORD") ?: ""
val stableKeyAlias = System.getenv("AUVYX_SIGNING_KEY_ALIAS") ?: ""
val stableKeyPassword = System.getenv("AUVYX_SIGNING_KEY_PASSWORD") ?: ""
val hasStableSigning = stableSigningFile.exists() && stableStorePassword.isNotBlank() &&
    stableKeyAlias.isNotBlank() && stableKeyPassword.isNotBlank()

val spotifyClientId = providers.gradleProperty("auvyx.spotifyClientId")
    .orElse(providers.environmentVariable("AUVYX_SPOTIFY_CLIENT_ID"))
    .getOrElse("").trim()
require(spotifyClientId.isEmpty() || spotifyClientId.matches(Regex("[a-fA-F0-9]{32}"))) {
    "AUVYX_SPOTIFY_CLIENT_ID must be the public Client ID of AUVYX's Spotify application."
}

android {
    namespace = "com.nexus.music"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nexus.music"
        minSdk = 29
        targetSdk = 34
        versionCode = 16
        versionName = "Alpha 2.0"
        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"$spotifyClientId\"")
    }

    val libraryTest = providers.gradleProperty("auvyx.libraryTest").isPresent
    val providerReview = providers.gradleProperty("auvyx.providerReview").isPresent

    signingConfigs {
        create("stableCi") {
            storeFile = stableSigningFile
            storePassword = stableStorePassword
            keyAlias = stableKeyAlias
            keyPassword = stableKeyPassword
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = when {
                libraryTest -> ".librarytest"
                providerReview -> ".providerreview"
                else -> ".preview"
            }
            versionNameSuffix = if (providerReview || libraryTest) " Library Test" else " Alpha Test"
            // GitHub Actions uses this only after the owner adds the signing secrets.
            // Without them Android's normal debug key remains the fallback.
            if (hasStableSigning) signingConfig = signingConfigs.getByName("stableCi")
        }
        release {
            isMinifyEnabled = false
            if (hasStableSigning) signingConfig = signingConfigs.getByName("stableCi")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions.unitTests.all { it.jvmArgs("--add-opens=java.base/java.io=ALL-UNNAMED") }
    testOptions.unitTests.isIncludeAndroidResources = true
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("org.robolectric:robolectric:4.14.1")
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")

    implementation("androidx.documentfile:documentfile:1.0.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.8.1")
}
