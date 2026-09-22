# Sprint 4: Implementación de TTS Local (Piper)

Este plan detalla la integración de **Piper TTS** para permitir que el asistente hable sin necesidad de conexión a internet, utilizando modelos ONNX de alta calidad.

## User Review Required

> [!IMPORTANT]
> **Modelo de Voz (ONNX)**: Al igual que con Vosk, Piper requiere un modelo (.onnx) y un archivo de configuración (.json). Prepararé el sistema para cargarlos desde `assets/piper/`. Necesitarás descargar una voz en español (ej. `es_ES-renee-medium.onnx`).

## Proposed Changes

### [Dependencias]

#### [MODIFY] [libs.versions.toml](file:///C:/Users/QuintiVG/AndroidStudioProjects/ScreenAssistant/gradle/libs.versions.toml)
- Agregar `onnxruntime = "1.18.0"` (o la última estable compatible).
- Agregar `onnxruntime-android`.

#### [MODIFY] [feature/overlay/build.gradle.kts](file:///C:/Users/QuintiVG/AndroidStudioProjects/ScreenAssistant/feature/overlay/build.gradle.kts)
- Implementar la dependencia de ONNX Runtime.

---

### [Lógica de Voz Local]

#### [NEW] [PiperTtsManager.kt](file:///C:/Users/QuintiVG/AndroidStudioProjects/ScreenAssistant/feature/overlay/src/main/kotlin/com/screenassistant/feature/overlay/PiperTtsManager.kt)
- Implementación de `TextToSpeech`.
- Carga de modelos ONNX usando ONNX Runtime.
- Procesamiento de texto a audio (PCM) y reproducción mediante `AudioTrack`.
- Manejo de búfer para frases largas.

---

### [Integración]

#### [MODIFY] [AssistantOverlayUI.kt](file:///C:/Users/QuintiVG/AndroidStudioProjects/ScreenAssistant/feature/overlay/src/main/kotlin/com/screenassistant/feature/overlay/AssistantOverlayUI.kt)
- Reemplazar `TextToSpeechManager` (sistema) por `PiperTtsManager` (local).

---

## Verification Plan

### Manual Verification
1. **Inicialización**: Verificar en Logcat que el motor ONNX se inicia correctamente.
2. **Prueba Offline**: En modo avión, pedir algo al asistente. Ella debe responder con su voz local.
3. **Calidad de Audio**: Asegurar que el audio es fluido y sin cortes.
