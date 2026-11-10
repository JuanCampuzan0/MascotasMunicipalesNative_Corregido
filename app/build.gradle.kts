plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
}

val compatibilityTests = providers.gradleProperty("compatibilityTests").isPresent
val animalRecognitionTests = providers.gradleProperty("animalRecognitionTests").isPresent

android {
    namespace = "com.mascotasmunicipales"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mascotasmunicipales"
        minSdk = 24
        targetSdk = 33
        versionCode = 7
        versionName = "1.6"
        testInstrumentationRunner = if (animalRecognitionTests)
            "com.mascotasmunicipales.AnimalRecognitionInstrumentation"
        else if (compatibilityTests)
            "com.mascotasmunicipales.FunctionalCompatibilityInstrumentation"
        else "com.mascotasmunicipales.PhotoCompatibilityInstrumentation"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildTypes {
        getByName("debug") {
            if (providers.gradleProperty("unsignedBuild").isPresent) {
                signingConfig = null
            }
        }
        create("compatibility") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".compatqa"
            versionNameSuffix = "-qa-local"
            matchingFallbacks += listOf("debug")
        }
    }
    testBuildType = if (compatibilityTests) "compatibility" else "debug"
}
dependencies {
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.6.2")
    implementation("androidx.lifecycle:lifecycle-livedata-core:2.6.2")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    implementation("com.google.mlkit:image-labeling:17.0.9")
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
}
