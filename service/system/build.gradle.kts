plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("jacoco")
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
    // Fase B (Lote 12): umbral calibrado = medición Fase A (65.07%) − 0.05.
    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                minimum = BigDecimal("0.60")
            }
        }
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

    // Testing — M24: version catalog
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
}
