# Application Models, Extractors, Resolvers & Database
-keep class com.example.model.** { *; }
-keep class com.example.db.** { *; }
-keep class com.example.extractor.** { *; }
-keep class com.example.resolver.** { *; }
-keep class com.example.decryptor.** { *; }
-keep class com.example.torrent.** { *; }
-keep class com.example.vega.** { *; }
-keep class com.example.subtitles.** { *; }
-keep class com.example.supabase.** { *; }
-keep class com.example.bunkr.** { *; }
-keep class com.example.smartskip.** { *; }
-keep class com.example.effects.** { *; }
-keep class com.example.downloader.** { *; }
-keep class com.example.cloudsocial.** { *; }
-keep class com.example.vault.** { *; }
-keep class com.example.ui.models.** { *; }
-keep class com.example.ui.player.** { *; }

# Chaquopy / Python runtime
-keep class com.chaquo.python.** { *; }
-dontwarn com.chaquo.python.**

# NewPipeExtractor & Rhino JS Engine
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**

# Media3 ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# OkHttp & JSoup
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# ML Kit OCR & Google Play Services
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.vision.** { *; }
-dontwarn com.google.mlkit.**

# libtorrent4j
-keep class org.libtorrent4j.swig.libtorrent_jni { *; }
-keep class org.libtorrent4j.** { *; }
-dontwarn org.libtorrent4j.**

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

# yt-dlp
-keep class dev.ffmpegkit_maintained.ytdlp.** { *; }
-dontwarn dev.ffmpegkit_maintained.ytdlp.**

# Whisper & Native C++ JNI bindings
-keepclassmembers class * {
    native <methods>;
}

# SnakeYAML
-keep class org.yaml.snakeyaml.** { *; }
-dontwarn org.yaml.snakeyaml.**

# Coil Image Loader
-keep class coil.** { *; }
-dontwarn coil.**

# Kotlin Coroutines & Reflection
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod


