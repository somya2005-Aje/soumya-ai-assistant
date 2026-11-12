plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

android {
    namespace = "ai.soumya.assistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "ai.soumya.assistant"
        minSdk = 29
        targetSdk = 34
        versionCode = 2
        versionName = "1.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Baked-in key slot (kept for future use). No real key is ever committed:
        // the value comes only from the GEMINI_API_KEY env var or the
        // -PgeminiApiKey Gradle property, and defaults to "" (empty = no key).
        // The app always prefers the user's own key from Settings.
        buildConfigField(
            "String",
            "GEMINI_API_KEY",
            "\"" + (System.getenv("GEMINI_API_KEY")
                ?: project.findProperty("geminiApiKey") as? String
                ?: "") + "\""
        )
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Room (long-term memory)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // Encrypted storage for the user-supplied Gemini API key
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Gemini REST client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Reminders (WorkManager fallback)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
