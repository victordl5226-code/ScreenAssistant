plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("jacoco")
}

android {
    namespace = "com.screenassistant.feature.overlay"
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

    // Lote 12 (M13): cobertura JaCoCo del módulo.
    buildTypes {
        debug {
            enableUnitTestCoverage = true
        }
    }
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
    description = "Verificación de cobertura JaCoCo del módulo (enforcement del gate)."
    dependsOn("testDebugUnitTest") // P2-3: .exec SIEMPRE fresco
    classDirectories.setFrom(jacocoClassTree())
    sourceDirectories.setFrom(jacocoSources())
    executionData.setFrom(jacocoExec())
    // Fase B (Lote 12): umbral = medición Fase A (37.22%) − 0.05 = 0.32. El módulo
    // mide bajo el suelo sugerido (0.45–0.50) → regla de no-ruptura: el enforcement
    // se fija bajo la medición real; subir la cobertura es deuda registrada (BACKLOG).
    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                minimum = BigDecimal("0.32")
            }
        }
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:ui"))

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    // D1 (Lote 9): el servicio del overlay se MOVIÓ aquí desde service:system →
    // LifecycleService (lifecycle-service) y WorkManager (solo para
    // cancelConnectivityWorkerZombie, M22) pasan a ser deps de esta feature.
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.5")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.53.1")
    ksp("com.google.dagger:hilt-android-compiler:2.53.1")

    // Media3 for ExoPlayer
    implementation("androidx.media3:media3-exoplayer:1.5.0")
    implementation("androidx.media3:media3-ui:1.5.0")

    // Coil
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")

    // Testing — M24: version catalog
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
}
