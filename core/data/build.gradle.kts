plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("jacoco")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.screenassistant.core.data"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Lote 12 (M6): Robolectric necesita resources/assets reales (ApplicationProvider).
    // P4-fix (Grupo A): isReturnDefaultValues = true — android.util.Log en
    // ProactiveSuggestionManager/GeminiRepositoryImpl devuelve defaults en JVM
    // en vez de lanzar "not mocked" (espejo de service:system).
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    // Lote 12 (M13): cobertura JaCoCo del módulo (el .exec lo lee jacocoTestReport).
    buildTypes {
        debug {
            enableUnitTestCoverage = true
        }
    }

    // Lote 12 (M6 — P1 BLOQUEANTE): expone schemas/ como assets de UNIT TEST.
    // isIncludeAndroidResources NO empaqueta schemas/ (es salida de KSP, no un
    // source set) → sin este srcDir, MigrationTestHelper lanza FileNotFoundException.
    sourceSets {
        getByName("test").assets.srcDir("$projectDir/schemas")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
    }
}

// Lote 12 (M6): Room exporta el esquema de la versión actual a core/data/schemas.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Lote 12 (M13): JaCoCo por módulo — recetario canónico del diseño (report +
// verification custom con exclusiones de generados Hilt/Room/KSP y dependsOn
// interno de testDebugUnitTest, P2-3).
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
    description = "Verificación de cobertura JaCoCo del módulo (enforcement del gate)."
    dependsOn("testDebugUnitTest") // P2-3: .exec SIEMPRE fresco
    classDirectories.setFrom(jacocoClassTree())
    sourceDirectories.setFrom(jacocoSources())
    executionData.setFrom(jacocoExec())
    // Fase B (Lote 12): umbral calibrado = medición Fase A (62.63%) − 0.05.
    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                minimum = BigDecimal("0.57")
            }
        }
    }
}

dependencies {
    implementation(project(":core:domain"))

    implementation("androidx.core:core-ktx:1.15.0")

    // Room — M24: version catalog (2.6.1 sin bump)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Gemini
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.53.1")
    ksp("com.google.dagger:hilt-android-compiler:2.53.1")

    // Security
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.datastore:datastore-core:1.1.1")

    // Network
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // Testing — M24: version catalog
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation("org.json:json:20240303")

    // Lote 12 (M6 — excepción de regla autorizada): migraciones Room JVM con
    // Robolectric (SQLite real, corre en el gate) + InstrumentationRegistry
    // (androidx.test:core, patrón "Testing Room as JUnit test").
    testImplementation(libs.room.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)

    // AiOrchestratorIntegrationTest necesita StubLocalInferenceEngine de core:ai:local
    testImplementation(project(":core:ai:local"))
}
