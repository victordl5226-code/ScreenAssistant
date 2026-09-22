cmake_minimum_required(VERSION 3.22.1)
project("llama_jni" LANGUAGES C CXX)

# ============================================================
# Configuración base
# ============================================================
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# ============================================================
# Configuración llama.cpp
# ============================================================
# Ruta al submódulo llama.cpp (inyectada desde Gradle via -DLLAMA_CPP_DIR)
if(NOT DEFINED LLAMA_CPP_DIR)
    set(LLAMA_CPP_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../llama.cpp)
    message(WARNING "LLAMA_CPP_DIR no definido, usando default: ${LLAMA_CPP_DIR}")
endif()

if(NOT EXISTS ${LLAMA_CPP_DIR}/CMakeLists.txt)
    message(FATAL_ERROR "llama.cpp no encontrado en ${LLAMA_CPP_DIR}. Ejecuta: git submodule update --init --recursive")
endif()

# ============================================================
# Flags de compilación base
# ============================================================
set(COMMON_COMPILE_FLAGS
    "-Wall"
    "-Wextra"
    "-Wno-unused-parameter"
    "-Wno-sign-compare"
    "-fvisibility=hidden"
    "-fdata-sections"
    "-ffunction-sections"
)

# Flags específicos por ABI (inyectados desde Gradle)
if(NOT DEFINED ABI_SPECIFIC_FLAGS)
    set(ABI_SPECIFIC_FLAGS "")
endif()

# ============================================================
# Incluir llama.cpp como subdirectorio
# ============================================================
# Deshabilitar features que no necesitamos en Android
set(GGML_USE_CPU ON CACHE BOOL "Use CPU backend" FORCE)
set(GGML_USE_METAL OFF CACHE BOOL "Use Metal backend" FORCE)
set(GGML_USE_CUDA OFF CACHE BOOL "Use CUDA backend" FORCE)
set(GGML_USE_VULKAN ON CACHE BOOL "Use Vulkan backend (GPU)" FORCE) # J.A.R.V.I.S. v3.8: Activando potencia de GPU
set(GGML_USE_SYCL OFF CACHE BOOL "Use SYCL backend" FORCE)
set(GGML_USE_RPC OFF CACHE BOOL "Use RPC backend" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "Enable libcurl" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "Build tools" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "Build examples" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "Build tests" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "Build server" FORCE)
# Habilitar common library para common_tokenize
set(LLAMA_BUILD_COMMON ON CACHE BOOL "Build common library" FORCE)
# Deshabilitar llamafile (causa problemas en armv7)
set(LLAMA_LLAMAFILE OFF CACHE BOOL "Build llamafile support" FORCE)

add_subdirectory(${LLAMA_CPP_DIR} llama_build)

# ============================================================
# 1. Librería estática: lógica pura (testeable en host)
# ============================================================
add_library(llama_engine_core STATIC
    llama_engine_core.cpp
)
target_include_directories(llama_engine_core PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${LLAMA_CPP_DIR}
    ${LLAMA_CPP_DIR}/include
    ${LLAMA_CPP_DIR}/ggml/include
)
target_compile_options(llama_engine_core PRIVATE ${COMMON_COMPILE_FLAGS})
target_compile_definitions(llama_engine_core PRIVATE GGML_USE_CPU=1)
target_link_libraries(llama_engine_core PRIVATE llama ggml llama-common)

# ============================================================
# 2. Librería compartida: JNI bridge (Android)
# ============================================================
add_library(llama_jni SHARED
    llama_jni_bridge.cpp
)
target_include_directories(llama_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${LLAMA_CPP_DIR}
    ${LLAMA_CPP_DIR}/include
)
target_compile_definitions(llama_jni PRIVATE
    GGML_USE_CPU=1
    ${ABI_SPECIFIC_FLAGS}
)
target_compile_options(llama_jni PRIVATE ${COMMON_COMPILE_FLAGS})
target_link_libraries(llama_jni PRIVATE
    llama_engine_core
    llama
    ggml
    ggml-base
    ggml-cpu
    log
    android
    dl
    m
)

# ============================================================
# 3. Test host-only: GoogleTest
# ============================================================
if(BUILD_TESTING)
    enable_testing()
    find_package(GTest REQUIRED)
    
    add_executable(llama_engine_core_test
        ${CMAKE_CURRENT_SOURCE_DIR}/../../test/cpp/llama_engine_core_test.cpp
    )
    target_link_libraries(llama_engine_core_test PRIVATE
        llama_engine_core
        GTest::gtest_main
        GTest::gtest
    )
    add_test(NAME llama_engine_core_test COMMAND llama_engine_core_test)
endif()

# ============================================================
# Flags por ABI (ARM NEON/SIMD optimizations)
# ============================================================
if(ANDROID_ABI STREQUAL "arm64-v8a")
    # arm64: NEON siempre disponible, no necesita -mfpu
    target_compile_options(llama_engine_core PRIVATE
        -march=armv8-a+simd -O3 -DGGML_USE_NEON=1
    )
    target_compile_options(llama_jni PRIVATE
        -march=armv8-a+simd -O3 -DGGML_USE_NEON=1
    )
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    # armv7: necesita -mfpu=neon para habilitar NEON
    target_compile_options(llama_engine_core PRIVATE
        -march=armv7-a -mfpu=neon -O3 -DGGML_USE_NEON=1
    )
    target_compile_options(llama_jni PRIVATE
        -march=armv7-a -mfpu=neon -O3 -DGGML_USE_NEON=1
    )
elseif(ANDROID_ABI STREQUAL "x86_64")
    target_compile_options(llama_engine_core PRIVATE -O3)
    target_compile_options(llama_jni PRIVATE -O3)
endif()

# ============================================================
# Propiedades C++17
# ============================================================
set_target_properties(llama_engine_core llama_jni PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
)

# ============================================================
# Strip symbols en release (reduce tamaño ~30%)
# J.A.R.V.I.S. v3.8: Alineación de 16KB para Android 16 readiness
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    set_target_properties(llama_jni PROPERTIES
        LINK_FLAGS "-Wl,--strip-all -Wl,-z,common-page-size=16384 -Wl,-z,max-page-size=16384"
    )
    set_target_properties(llama_engine_core PROPERTIES
        LINK_FLAGS "-Wl,-z,common-page-size=16384 -Wl,-z,max-page-size=16384"
    )
else()
    set_target_properties(llama_jni PROPERTIES
        LINK_FLAGS "-Wl,-z,common-page-size=16384 -Wl,-z,max-page-size=16384"
    )
endif()

# ============================================================
# Android-specific properties
# ============================================================
set_target_properties(llama_jni PROPERTIES
    ANDROID_SO_UNDEF OK
    CXX_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN ON
)