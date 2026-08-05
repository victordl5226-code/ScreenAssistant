plugins {
    id("com.android.application") version "8.7.3" apply false
    id("com.android.library") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.0" apply false
    id("org.jetbrains.kotlin.jvm") version "2.0.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.0" apply false
    id("com.google.devtools.ksp") version "2.0.0-1.0.22" apply false
    id("com.google.dagger.hilt.android") version "2.53.1" apply false
    id("jacoco")
}

// Lote 12 (M13): JaCoCo 0.8.13 — fuente única de versión en el catalog
// (gradle/libs.versions.toml, convención Lote 10). Cada módulo con tests lo
// aplica con su propio toolVersion (la del root no se propaga por sí sola).
jacoco {
    toolVersion = libs.versions.jacoco.get()
}

// Lote 12 (M13): gate mecanizado. Sustituye el comando manual de "Notas de
// proceso": unit tests de TODOS los módulos + assembleDebug + enforcement JaCoCo
// de los 4 módulos con umbral. Sin :app (P2-1: report-only, su cobertura JVM es
// estructuralmente baja y la de UI llega vía androidTest) y sin
// connectedDebugAndroidTest (requiere dispositivo — tarea documentada y aparte).
tasks.register("gate") {
    group = "verification"
    description = "Gate mecanizado (Lote 12): tests JVM + assembleDebug + cobertura JaCoCo (sin :app ni connected)."
    dependsOn(
        ":app:testDebugUnitTest",
        ":core:data:testDebugUnitTest",
        ":core:domain:test",
        ":feature:overlay:testDebugUnitTest",
        ":service:system:testDebugUnitTest",
        ":app:assembleDebug",
        ":core:data:jacocoTestCoverageVerification",
        ":core:domain:jacocoTestCoverageVerification",
        ":feature:overlay:jacocoTestCoverageVerification",
        ":service:system:jacocoTestCoverageVerification"
    )
}
