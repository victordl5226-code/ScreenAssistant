plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    id("jacoco")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

dependencies {
    // === Dependencias internas ===
    implementation(project(":core:domain"))

    // === Kotlinx ===
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // === Testing ===
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockk)
}
