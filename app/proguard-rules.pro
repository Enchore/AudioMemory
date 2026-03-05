# Proguard rules for AudioMemory

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class *

# Data entities (needed for Room + Gson)
-keep class com.audiomemory.data.entity.** { *; }
-keep class com.audiomemory.service.processing.** { *; }
-keep class com.audiomemory.ml.transcription.WhisperResponse { *; }
-keep class com.audiomemory.ml.transcription.WhisperSegment { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }

# Retrofit
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class *
