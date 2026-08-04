plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
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

    // android.util.Log en AppLauncherAction: devuelve defaults en JVM en vez de
    // lanzar "not mocked" (los tests mockean todo lo demás con MockK).
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // D1+D2 (Lote 9): SIN dependencias de features (AssistantOverlayService se
    // movió a feature:overlay y feature:chat se eliminó) → grafo limpio:
    // core:domain + core:data + libs. Única violación de capas del proyecto saldada.
    implementation(project(":core:domain"))
    implementation(project(":core:data"))

    implementation("androidx.core:core-ktx:1.15.0")
    // Puente Tasker: verificado por QA — el código de PRODUCCIÓN de este módulo usa Json
    // directamente en firma pública (constructor con default `codec: SystemCommandJsonCodec = ...`
    // → método sintético con Json; no es solo uso de tests) → implementation es OBLIGATORIA
    // aquí (no testImplementation); a su vez es la que provee el runtime a :app por transitividad.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.53.1")
    ksp("com.google.dagger:hilt-android-compiler:2.53.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
