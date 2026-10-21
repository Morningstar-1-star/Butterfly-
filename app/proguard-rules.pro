# Application Models, Database & Serialization
-keep class com.example.model.** { *; }
-keep class com.example.db.** { *; }
-keep class com.example.vega.** { *; }
-keep class com.example.supabase.** { *; }

# Chaquopy / Python runtime
-keep class com.chaquo.python.** { *; }
-dontwarn com.chaquo.python.**

# NewPipeExtractor & Rhino JS Engine
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**

# Media3 ExoPlayer (keep JNI / reflection entry points only)
-keepclasseswithmembers class androidx.media3.** {
    native <methods>;
    public <init>(...);
}
-dontwarn androidx.media3.**

# OkHttp & JSoup warnings
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.jsoup.**
-dontwarn coil.**

# Room Database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**

# Moshi & JSON models
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
    @com.squareup.moshi.JsonClass *;
}

# yt-dlp / youtubedl-android & Jackson JSON mapper
-keep class com.yausername.** { *; }
-dontwarn com.yausername.**
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**

# Native C++ JNI bindings
-keepclassmembers class * {
    native <methods>;
}

# Kotlin Coroutines & Reflection
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
