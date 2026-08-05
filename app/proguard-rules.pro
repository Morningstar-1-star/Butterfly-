# NewPipeExtractor
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**

# Media3 ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# OkHttp & JSoup
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**
