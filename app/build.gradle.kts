import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("jacoco")
}

android {
    namespace = "com.screenassistant"
    compileSdk = 35

    val secretsFile = rootProject.file("secrets.properties")
    val secretsProperties = Properties()
    if (secretsFile.exists()) {
        secretsFile.inputStream().use { secretsProperties.load(it) }
    } else {
        logger.warn("⚠️ secrets.properties no encontrado. Usando BuildConfig fallback.")
    }

    val geminiApiKeyValue = secretsProperties.getProperty("GEMINI_API_KEY", "")
    val geminiApiKey = if (geminiApiKeyValue.isNotEmpty()) {
        "\"${geminiApiKeyValue}\""
    } else {
        "\"\""
    }

    defaultConfig {
        applicationId = "com.screenassistant"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GEMINI_API_KEY", geminiApiKey)
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Lote 12 (M13): cobertura JaCoCo del módulo (report-only, P2-1 — ver abajo).
    buildTypes {
        debug {
            enableUnitTestCoverage = true
        }
    }
}

// Lote 12 (M13): JaCoCo por módulo — recetario canónico del diseño. :app es
// REPORT-ONLY (P2-1, decisión del Arquitecto): su cobertura JVM es
// estructuralmente baja (composables/Activity/DI no se testean en JVM) y la real
// de UI llega vía androidTest (que JaCoCo no mide sin dispositivo) → la
// verification se registra SIN violationRules y NO entra en la tarea `gate`.
// El report queda disponible bajo demanda: gradlew :app:jacocoTestReport.
jacoco {
    toolVersion = libs.versions.jacoco.get()
}

private val jacocoClassFilter = listOf(
    "**/BuildConfig*",
    "**/R.class",
    "**/R$*.class",
    "**/hilt_aggregated_deps/**",
    "**/Hilt_*.class",
    "**/*_HiltModules*",
    "**/*_HiltComponents*",
    "**/Dagger*",
    "**/*_Factory.class",
    "**/*_Impl.class",
    "**/*_GeneratedInjector.class"
)

private fun jacocoClassTree(): FileTree = fileTree("$buildDir/tmp/kotlin-classes/debug") {
    exclude(jacocoClassFilter)
}

private fun jacocoSources(): FileCollection = files("src/main/java", "src/main/kotlin")

private fun jacocoExec(): FileTree = fileTree("$buildDir/outputs/unit_test_code_coverage/debugUnitTest") {
    include("*.exec")
}

tasks.register<JacocoReport>("jacocoTestReport") {
    group = "verification"
    description = "Reporte JaCoCo del módulo (cobertura de testDebugUnitTest)."
    dependsOn("testDebugUnitTest") // P2-3: .exec SIEMPRE fresco
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    classDirectories.setFrom(jacocoClassTree())
    sourceDirectories.setFrom(jacocoSources())
    executionData.setFrom(jacocoExec())
}

tasks.register<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    group = "verification"
    description = "Verificación JaCoCo de :app — REPORT-ONLY (P2-1): sin violationRules, excluida del gate."
    dependsOn("testDebugUnitTest") // P2-3: .exec SIEMPRE fresco
    classDirectories.setFrom(jacocoClassTree())
    sourceDirectories.setFrom(jacocoSources())
    executionData.setFrom(jacocoExec())
    // Sin violationRules a propósito (P2-1): el umbral de :app es deuda registrada.
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":feature:overlay"))
    implementation(project(":service:system"))

    // Core
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    // Puente Tasker (D5, Lote 9): AppModule compila contra el constructor sintético
    // con default de SystemCommandJsonCodec (firma con Json) → implementation EXPLÍCITO.
    // El runtime ya llegaba por transitividad de :service:system, pero compileOnly era
    // frágil: si ese módulo dejara de exponerla → NoClassDefFoundError silencioso en
    // producción (versión 1.7.3 idéntica en todo el grafo → cero conflictos).
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.11.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.5")
    implementation("androidx.navigation:navigation-compose:2.8.4")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.53.1")
    ksp("com.google.dagger:hilt-android-compiler:2.53.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Room (needed by AppModule for Room.databaseBuilder) — M24: version catalog
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)

    // Testing — M24: version catalog
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockk)
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.11.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Lote 12 (M13): androidTest mínimo (Compose puro, sin espresso ni hilt-testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
}
