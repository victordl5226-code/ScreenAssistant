# Add project specific ProGuard rules here.

# Gemini AI - Keep model classes
-keep class com.google.ai.client.generativeai.** { *; }

# Coil - Keep all
-dontwarn coil.**
-keep class coil.** { *; }

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Keep Compose
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# Keep Media3/ExoPlayer
-dontwarn androidx.media3.**
-keep class androidx.media3.** { *; }

# Keep Service classes (not accessed via direct reference in manifest)
-keep class com.screenassistant.service.** { *; }
