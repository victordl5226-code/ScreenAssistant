# ScreenAssistant — Reglas ProGuard/R8 (ADR-027 Fase 1 paso 1.3 · OPT-impl-2).
#
# R8 se activa SOLO en release (app/build.gradle.kts: isMinifyEnabled = true).
# Debug queda intacto. Cada keep fue verificado contra el código real antes de
# escribirse (ver PROGRESO.md OPT-impl-2 para el inventario por categoría).
#
# Convenciones:
# - PROHIBIDO -dontwarn global: solo dontwarn específicos y verificados.
# - Los keeps preventivos (sin uso directo en src) se marcan PREVENTIVO + motivo.
# - Tras compilar release, archivar build/outputs/mapping/release/mapping.txt
#   (imprescindible para desofuscar stacktraces de QA).
# - assets/ NO los toca shrinkResources (Vosk "model-es", Piper "piper/voice.onnx"
#   se cargan por ruta literal y quedan intactos).

# ══ 0. Atributos base (sostienen Room + serialization + enums + JNI) ══
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, *Annotation*
-keepattributes Exceptions
-keepattributes SourceFile, LineNumberTable
-keep class kotlin.Metadata { *; }

# ══ 1. Hilt/Dagger (verificado en src) ══
# @AndroidEntryPoint: MainActivity, ScreenContextService, AlarmReceiver,
#   TaskerCommandReceiver, HotwordListeningService, AssistantOverlayService.
# @HiltViewModel ×12 (app, feature:overlay, feature:iot). @HiltWorker:
#   IotSyncWorker, ProactiveCheckWorker (+ PatternLearnerWorker). HiltWorkerFactory
#   inyectado en ScreenAssistantApp. @Module/@Provides/@Binds: AppModule, NlpModule,
#   DataModule, HotwordModule, IotModule, SemanticMemoryModule, LocalAiModule.
-keep class dagger.hilt.** { *; }
-keep class dagger.hilt.android.** { *; }
-keep class javax.inject.** { *; }
-keep interface dagger.hilt.EntryPoint { *; }
-keep @dagger.hilt.EntryPoint class * { *; }
# PREVENTIVO: sin @EntryPoint en src hoy; Hilt lo genera al añadir el primero.
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keepclasseswithmembernames class * { @javax.inject.Inject <init>(...); }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keep class **.hilt_aggregated_deps { *; }
-keep class **_*HiltModules { *; }
-keep class **_*HiltComponents { *; }
-keep class androidx.hilt.work.HiltWorkerFactory { *; }
-keep @androidx.hilt.work.HiltWorker class * { *; }
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }
-keep @dagger.Module class * { *; }
-keepclasseswithmembernames class * { @dagger.Provides <methods>; @dagger.Binds <methods>; }

# ══ 2. Room (verificado en src) ══
# AppDatabase v6 (core:data/local: 12 entities, 7 DAOs) + MemoryDatabase v2
# (core:ai:memory: LongTermMemoryEntity, KnowledgeFactEntity, SemanticFactEntity).
# KSP genera AppDatabase_Impl, MemoryDatabase_Impl y *_Dao_Impl.
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }
# PREVENTIVO: sin @TypeConverter en src hoy; el keep evita IllegalArgumentException
# el día que se añada uno sin actualizar este archivo.
-keep @androidx.room.TypeConverter class * { *; }
-keepclasseswithmembernames class * { @androidx.room.TypeConverter <methods>; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class com.screenassistant.core.data.local.** { *; }
-keep class com.screenassistant.core.ai.memory.** { *; }
-keep class com.screenassistant.core.data.local.AppDatabase_Impl { *; }
-keep class com.screenassistant.core.ai.memory.MemoryDatabase_Impl { *; }
-keep class **.*_Dao_Impl { *; }

# ══ 3. kotlinx-serialization (verificado en src) ══
# @Serializable masivo: CommandEnvelope sellado + 22 subtipos con @SerialName
# (core:domain/bridge/model), VehicleTypes + HealthRecord + wearables
# (core:iot/domain). SystemCommandJsonCodec es codec MANUAL (no @Serializable)
# pero se usa por reflexión de firmas con defaults → keep explícito.
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclasseswithmembernames class * { kotlinx.serialization.KSerializer serializer(...); }
-keepclassmembers class **.$$serializer { *; }
-keepclassmembers class **.Companion { kotlinx.serialization.KSerializer serializer(...); }
-keep class com.screenassistant.core.domain.bridge.** { *; }
-keep class com.screenassistant.core.iot.domain.repository.VehicleTypes* { *; }
-keep class com.screenassistant.core.iot.domain.repository.** { *; }
-keep class com.screenassistant.core.domain.bridge.SystemCommandJsonCodec { *; }

# ══ 4. Vosk (verificado: VoskSpeechToTextManager) ══
# Imports reales: org.vosk.Model/Recognizer + org.vosk.android.SpeechService/
# StorageService/RecognitionListener. StorageService.unpack(context, "model-es",
# "model", ...) usa ruta literal de assets (assets no los toca shrinkResources).
# LibVosk no se importa directo: es el cargador JNI interno de la lib → keep.
-keep class org.vosk.** { *; }
-keep class org.vosk.android.** { *; }
-keep class org.vosk.LibVosk { *; }
-keep class org.vosk.Model { *; }
-keep class org.vosk.Recognizer { *; }
-keep class org.vosk.android.SpeechService { *; }
-keep class org.vosk.android.StorageService { *; }
# OPT-fix-jna (BUG-OPT4-001): vosk-android:0.3.47 usa JNA; Native.initIDs resuelve
# por JNI el campo `peer` de Pointer → sin keep R8 lo ofusca/elimina y crash
# UnsatisfiedLinkError en LibVosk.<clinit>.
-keep class com.sun.jna.** { *; }
-keepclasseswithmembernames class com.sun.jna.** { native <methods>; }
# OPT-fix-jna2: JNA-AWT jamás se ejecuta en Android; Vosk solo usa
# Native/Pointer/Memory/Library. El keep com.sun.jna.** incluye las clases AWT
# de JNA (Native$AWT) y R8 avisa Missing class java.awt.* → -dontwarn acotado
# (sugerido en app/build/outputs/mapping/release/missing_rules.txt). NO estrechar
# el keep (el campo `peer` debe conservarse íntegro) y NO -dontwarn global.
-dontwarn java.awt.**

# ══ 5. JNI propio llama.cpp (verificado en src + cpp) ══
# LlamaCppBridge: 8 external fun (nativeInit/Destroy/LoadModel/FreeModel/Generate/
# StreamGenerate/IsReady/TokenCount) con JNIEXPORTs 1:1 en llama_jni_bridge.cpp.
# LlamaInitParams: data class con defaults que cruza la frontera JNI.
-keep class com.screenassistant.core.ai.local.llama.bridge.LlamaCppBridgeInterface { *; }
-keep class com.screenassistant.core.ai.local.llama.bridge.LlamaCppBridge { native <methods>; }
-keep class com.screenassistant.core.ai.local.llama.config.LlamaInitParams { *; }
# Regla JNI global canónica (cubre llama_jni + cualquier nativo futuro por reflexión):
-keepclasseswithmembernames class * { native <methods>; }
# NOTA InferenceEngineImpl (com.arm.aichat): SIN keep a propósito. Vive solo en
# core/ai/local/llama.cpp/examples (código de ejemplo, FUERA de settings.gradle.kts
# → no entra al grafo de compilación). Si algún día se incluye como módulo, añadir:
# -keep class com.arm.aichat.internal.InferenceEngineImpl { *; native <methods>; }

# ══ 6. ONNX Runtime (verificado: PiperTtsManager + OnDeviceEmbeddingGenerator) ══
# Usos directos: OrtEnvironment.getEnvironment(), createSession, OnnxTensor.
# SessionOptions/NodeInfo sin import directo: superficie API que ORT usa por
# reflexión interna → keep. onnxruntime alineado a 1.21.0 (OPT-impl-1).
-keep class ai.onnxruntime.OrtEnvironment { *; }
-keep class ai.onnxruntime.OrtSession { *; }
-keep class ai.onnxruntime.OnnxTensor { *; }
-keep class ai.onnxruntime.OrtSession$SessionOptions { *; }
-keep class ai.onnxruntime.NodeInfo { *; }
# dontwarn ESPECÍFICO permitido (sin esto R8 avisa de refs opcionales de ORT):
-dontwarn ai.onnxruntime.**
-dontwarn com.microsoft.onnxruntime.**

# ══ 7. Porcupine (verificado: PorcupineHotwordDetector) ══
# Porcupine.Builder().setKeyword(Porcupine.BuiltInKeyword.JARVIS).
-keep class ai.picovoice.porcupine.Porcupine { *; }
-keep class ai.picovoice.porcupine.Porcupine$Builder { *; }
-keep class ai.picovoice.porcupine.Porcupine$BuiltInKeyword { *; values(); valueOf(java.lang.String); }

# ══ 8. MLKit por producto (verificado: service:system actions) ══
# translate + language-id (TranslateAction), barcode (QrScanAction),
# face (FaceDetectionAction), text latin (OcrAction). InputImage común a los 3
# de vision. gms.internal.mlkit_** es el runtime compartido de todos.
-keep class com.google.mlkit.nl.translate.** { *; }
-keep class com.google.mlkit.nl.languageid.** { *; }
-keep class com.google.mlkit.vision.barcode.** { *; }
-keep class com.google.mlkit.vision.face.** { *; }
-keep class com.google.mlkit.vision.text.** { *; }
-keep class com.google.mlkit.common.** { *; }
-keep class com.google.mlkit.vision.common.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.internal.mlkit_**

# ══ 9. Gemini (keep previo, se conserva: repo remoto en core:data) ══
-keep class com.google.ai.client.generativeai.** { *; }

# ══ 10. Navigation + DataStore REFINADOS (sustituyen wildcard compose) ══
# Navigation verificado: rememberNavController (MainActivity), NavHost+composable
# (IotNavigation), hiltViewModel ×4 (app, overlay, iot ×2).
-keep class androidx.navigation.compose.** { *; }
-keep class androidx.hilt.navigation.compose.** { *; }
# DataStore verificado: SOLO preferencesDataStore (HotwordPreferences,
# Personality/Proactive/MonitoringPreferences, HolidayStore). Sin Serializer
# custom en src → keep de Serializer es PREVENTIVO (protoDataStore futuro).
-keep class androidx.datastore.preferences.** { *; }
-keep class androidx.datastore.core.** { *; }
-keep class * extends androidx.datastore.core.Serializer { *; }

# ══ 11. Media3/ExoPlayer REFINADO (sustituye wildcard media3) ══
# Verificado (AssistantVideoPlayer): ExoPlayer.Builder, MediaItem, Player,
# PlayerView, @UnstableApi. DefaultRenderersFactory no se importa directo pero
# ExoPlayer lo instancia por reflexión → keep oficial mínimo.
-keep class androidx.media3.exoplayer.ExoPlayer { *; }
-keep class androidx.media3.exoplayer.DefaultRenderersFactory { *; }
-keep class androidx.media3.common.MediaItem { *; }
-keep class androidx.media3.common.Player { *; }
-keep class androidx.media3.ui.PlayerView { *; }

# ══ 12. Coil REFINADO (sustituye wildcard coil) ══
# Verificado (AssistantOverlayUI): AsyncImage + ImageRequest +
# GifDecoder/ImageDecoderDecoder.Factory (GIF según SDK).
-keep class coil.compose.** { *; }
-keep class coil.decode.** { *; }
-keep class coil.request.ImageRequest { *; }

# ══ 13. Componentes del Manifest + BuildConfig (verificado) ══
# Servicios/receivers declarados en manifests (app + feature:overlay) y
# referenciados por el sistema, no por código: el merger los conserva por nombre
# pero R8 puede vaciarlos → keep explícito. BuildConfig.GEMINI_API_KEY se lee
# en ScreenAssistantApp.sembrarDesdeBuildConfig.
-keep class com.screenassistant.BuildConfig { public static final java.lang.String GEMINI_API_KEY; }
-keep class com.screenassistant.MainActivity { *; }
-keep class com.screenassistant.ScreenAssistantApp { *; }
-keep class com.screenassistant.service.system.ScreenContextService { *; }
-keep class com.screenassistant.service.system.AlarmReceiver { *; }
-keep class com.screenassistant.service.system.bridge.TaskerCommandReceiver { *; }
-keep class com.screenassistant.feature.overlay.AssistantOverlayService { *; }
-keep class com.screenassistant.feature.overlay.hotword.HotwordListeningService { *; }

# ══ Over-keeps ELIMINADOS (refinados arriba, §10-12) ══
# - "-keep class androidx.compose.** {*;}" + "-dontwarn androidx.compose.**"
#   → NINGÚN uso de Compose runtime por reflexión en src (solo Navigation +
#   hiltViewModel, cubiertos en §10). Compose compiler genera código estático.
# - "-keep class androidx.media3.** {*;}" + dontwarn → §11 (5 clases exactas).
# - "-dontwarn coil.**" + "-keep class coil.** {*;}" → §12 (compose+decode+request).
# - "-keep class com.screenassistant.service.** {*;}" → §13 (7 componentes exactos).
# EXCEPCIONES MANTENIDAS: ninguna (cero wildcards ** {*;} de librería completa;
# los ** restantes son paquetes propios pequeños o APIs con reflexión docum.).
# Si la compilación release falla por una clase podada de más, el camino es
# añadir el keep MÍNIMO de esa clase (no restaurar wildcards).
