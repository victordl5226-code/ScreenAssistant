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

        // OPT-impl-2 (ADR-027 Fase 1 paso 1.4): resConfigs aquí en defaultConfig y
        // NO en el bloque release — DESVIACIÓN DOCUMENTADA de la tarea: la API de
        // AGP 8.7.3 no lo permite en buildTypes. Verificado con javap sobre el jar
        // exacto (gradle-api-8.7.3.jar): ApplicationBuildType ← BuildType ←
        // VariantDimension NO exponen resConfigs/resourceConfigurations (solo
        // BaseFlavor/DefaultConfig: resConfigs(String...)). Ponerlo en release no
        // compila ("Unresolved reference"). Efecto idéntico al buscado: solo se
        // conservan recursos default + es + en (sin values-es/values-en en src, el
        // default en español es el fallback; se podan traducciones de MLKit/Media3
        // /Compose en otros idiomas). En debug el efecto es inocuo.
        resConfigs("es", "en")

        // OPT-fix1: SIN ndk.abiFilters a nivel app — AGP prohíbe combinarlo
        // con splits.abi (los splits ya filtran el empaquetado por APK).
        // core:ai:local conserva sus propios filtros para lo que compila con CMake.
    }

    // OPT-3 ciclo 1 (ADR-027 Fase 1 paso 1.1): un APK por ABI.
    // AGP 8.7.3 Kotlin DSL: isEnable/isUniversalApk (nombres correctos).
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }

    buildTypes {
        release {
            // OPT-impl-2 (ADR-027 Fase 1 pasos 1.3+1.4): R8 + shrink SOLO release.
            // Debug intacto (sin minify) para no ralentizar iteración.
            isMinifyEnabled = true
            isShrinkResources = true
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

    packaging {
        jniLibs {
            // J.A.R.V.I.S. v3.8: Alineación de 16KB para Android 16
            // useLegacyPackaging = false fuerza el uso de librerías sin comprimir y alineadas
            useLegacyPackaging = false
            // pickFirsts para evitar colisiones de libc++_shared
            pickFirsts += listOf("lib/**/libc++_shared.so")
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
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

private fun jacocoClassTree(): FileTree = fileTree(project.layout.buildDirectory.dir("tmp/kotlin-classes/debug").get().asFile) {
    exclude(jacocoClassFilter)
}

private fun jacocoSources(): FileCollection = files("src/main/java", "src/main/kotlin")

private fun jacocoExec(): FileTree = fileTree(project.layout.buildDirectory.dir("outputs/unit_test_code_coverage/debugUnitTest").get().asFile) {
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
    implementation(project(":core:nlp"))
    implementation(project(":core:ai:memory"))
    implementation(project(":core:ai:local"))
    implementation(project(":feature:overlay"))
    implementation(project(":feature:iot"))
    implementation(project(":core:iot:data"))
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
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

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
