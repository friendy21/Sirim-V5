# Keep ML Kit models and CameraX metadata
-keep class com.google.android.gms.internal.** { *; }
-keep class com.google.mlkit.vision.text.** { *; }
-keep class com.google.mlkit.** { *; }
-keep class androidx.camera.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep interface androidx.room.Dao
-dontwarn androidx.room.paging.**

# Coroutine internals
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Networking
-keep class com.squareup.okhttp3.** { *; }

# Retrofit (for future use)
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }

# Vision & OCR dependencies
-keep class org.opencv.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-keep class com.itextpdf.** { *; }
