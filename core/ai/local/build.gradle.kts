plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("jacoco")
}

android {
    namespace = "com.screenassistant.core.ai.local"
    compileSdk = 35

    defaultConfig {
        minSdk = 26

        // NDK: Compilar para las 3 arquitecturas objetivo
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        // CMake para compilación nativa
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                // J.A.R.V.I.S. v3.8: Alineación de 16KB para Android 16 (Compiler & Linker flags)
                cppFlags += "-falign-functions=16"
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DANDROID_CPP_FEATURES=exceptions",
                    "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384",
                    "-DCMAKE_EXE_LINKER_FLAGS=-Wl,-z,max-page-size=16384",
                    // Ruta al submódulo llama.cpp (relativo a este build.gradle.kts)
                    "-DLLAMA_CPP_DIR=${projectDir}/llama.cpp",
                    // Deshabilitar tests en build Android
                    "-DBUILD_TESTING=OFF",
                )
            }
        }
    }

    // Configuración por variante de build (debug/release)
    buildTypes {
        debug {
            enableUnitTestCoverage = true
            // En debug: sin strip, con símbolos
            externalNativeBuild {
                cmake {
                    arguments += "-DCMAKE_BUILD_TYPE=Debug"
                }
            }
        }
        release {
            // En release: strip symbols, optimizaciones máximas
            externalNativeBuild {
                cmake {
                    arguments += "-DCMAKE_BUILD_TYPE=Release"
                }
            }
        }
    }

    // Configuración CMake
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // J.A.R.V.I.S. v3.8: Alineación de 16KB para Android 16
    packaging {
        jniLibs {
            useLegacyPackaging = false
            pickFirsts += listOf("lib/**/libc++_shared.so")
            keepDebugSymbols += listOf("**/*.gguf")
        }
    }
}

// ============================================================
// NDK version fija para reproducibilidad
// ============================================================
android {
    ndkVersion = "27.0.12077973"

    defaultConfig {
        externalNativeBuild {
            cmake {
                // Flags comunes para todas las ABIs (llama.cpp config)
                // J.A.R.V.I.S. v3.8: Activando GPU Vulkan
                arguments += listOf(
                    "-DGGML_USE_CPU=ON",
                    "-DGGML_USE_METAL=OFF",
                    "-DGGML_USE_CUDA=OFF",
                    "-DGGML_USE_VULKAN=ON",
                    "-DLLAMA_CURL=OFF",
                    "-DLLAMA_BUILD_TOOLS=OFF",
                    "-DLLAMA_BUILD_EXAMPLES=OFF",
                    "-DLLAMA_BUILD_TESTS=OFF",
                    "-DLLAMA_BUILD_SERVER=OFF",
                )
            }
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

dependencies {
    // === Dependencias internas ===
    implementation(project(":core:domain"))

    // === AndroidX ===
    implementation("androidx.core:core-ktx:1.15.0")

    // === Hilt ===
    implementation("com.google.dagger:hilt-android:2.53.1")
    ksp("com.google.dagger:hilt-android-compiler:2.53.1")

    // === Testing ===
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)

    // === Instrumented tests (para integración real con .so) ===
    androidTestImplementation("androidx.test:runner:1.6.0")
    androidTestImplementation("androidx.test:rules:1.6.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.0")
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.coroutines.test)
}