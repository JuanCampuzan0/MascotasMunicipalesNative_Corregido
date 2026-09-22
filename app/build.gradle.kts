plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.mascotasmunicipales"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mascotasmunicipales"
        minSdk = 24
        targetSdk = 33
        versionCode = 2
        versionName = "1.1"
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
    }
}
dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
}
