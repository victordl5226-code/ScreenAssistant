plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.screenassistant.service.system"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }

    // android.util.Log en AppLauncherAction: devuelve defaults en JVM en vez de
    // lanzar "not mocked" (los tests mockean todo lo demás con MockK).
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":feature:overlay"))
    implementation(project(":feature:chat"))

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    // Puente Tasker: verificado por QA — el código de PRODUCCIÓN de este módulo usa Json
    // directamente en firma pública (constructor con default `codec: SystemCommandJsonCodec = ...`
    // → método sintético con Json; no es solo uso de tests) → implementation es OBLIGATORIA
    // aquí (no testImplementation); a su vez es la que provee el runtime a :app por transitividad.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.53.1")
    ksp("com.google.dagger:hilt-android-compiler:2.53.1")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.8.4")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.11.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
