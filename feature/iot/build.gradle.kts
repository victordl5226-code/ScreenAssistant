plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("jacoco")
}

android {
    namespace = "com.screenassistant.feature.iot"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
    }
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

private val jacocoClassFilter = listOf(
    "**/BuildConfig*",
    "**/R.class",
    "**/R\$*.class",
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
    description = "Reporte JaCoCo del módulo feature:iot."
    dependsOn("testDebugUnitTest")
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    classDirectories.setFrom(jacocoClassTree())
    sourceDirectories.setFrom(jacocoSources())
    executionData.setFrom(jacocoExec())
}

dependencies {
    // === Dependencias internas ===
    implementation(project(":core:iot:domain"))
    implementation(project(":core:ui"))

    // === AndroidX ===
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // === Navigation Compose ===
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // === Compose ===
    implementation(platform("androidx.compose:compose-bom:2024.11.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.5")
    implementation("androidx.compose.animation:animation")

    // === Hilt ===
    implementation("com.google.dagger:hilt-android:2.53.1")
    ksp("com.google.dagger:hilt-android-compiler:2.53.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // === Testing ===
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("app.cash.turbine:turbine:1.2.0")

    // === Compose UI Testing (androidTest) ===
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.11.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
}
