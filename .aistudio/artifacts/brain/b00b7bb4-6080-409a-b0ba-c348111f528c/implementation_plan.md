# Butterfly Optimization & Playback Reliability Plan

## 1. Executive Summary & Root Causes

Our deep audit of the codebase and build pipeline uncovered the exact root causes behind all 7 issues:

1. **Previous Video Keeps Playing Over New / Failed Sources (Issue #6)**:
   - **Wrong-Thread Silent Failure in `PlayerCore.kt` & `GlobalPlayerManager.kt`**: `PlayerCore.resetPlayback()` calls `player?.stop()` and `player?.clearMediaItems()` inside a `try/catch` block without switching to the Android Main Thread (`Looper.getMainLooper()`). Because Media3 `ExoPlayer` enforces main-thread access, calling `stopAndClear()` from a background coroutine or IO dispatcher throws `IllegalStateException: Player is accessed on the wrong thread`, which the `catch` block silently swallows. As a result, the previous video never stops in `ExoPlayer`.
   - **Missing Stop on Extraction Failure & `onResume` Race**: When switching to a source that hangs or returns `ExtractionResult.Error`, `VideoPlayerScreen` and `MainViewModel.playVideo()` do not force a main-thread `GlobalPlayerManager.stopAndClear()`, and `MainActivity.onResume()` calls `syncWithGlobalPlayer()`, which re-attaches the still-playing previous stream.

2. **OlaMovies Shows Thumbnails/Titles But Fails to Play Videos (Issue #5)**:
   - **Stage 1 Works, Stage 2 Times Out Before Fallback**: Stage 1 (`getHome`, `search`, `getMeta`) succeeds via your Cloudflare Worker (`falling-wildflower-9a91.samdem612.workers.dev`). However, in Stage 2 (`getStreamData`), `MainViewModel.playVideo()` wraps extraction in a hard **20-second timeout (`withTimeoutOrNull(20000L)`)**.
   - `OlaMoviesProvider.getStreamData()` sequentially tries up to 3 `ol-am.top` shorteners (including a 14-second headless WebView wait per link) and then calls `VegaProviderClient.resolveUnified()` (which has a 100-second timeout) *before* reaching the fast local TMDB / VidSrc / VixSrc / Showbox / Decryptor extractors. Consequently, the 20-second timer cancels OlaMovies every time before any working fallback stream is returned.

3. **Dead / Hanging Vega, Nuvio, Decryptor & VidSrc Sources (Issue #4)**:
   - When a Vega, Nuvio, Decryptor, or VidSrc source is offline or returns no playable HLS/MP4 URL, the app either waits up to 60–100 seconds on a single request or stops at an error screen instead of automatically skipping to the next working source/server.

4. **GitHub ZIP & APK Size Over 50+ MB and 3 APKs Generated (Issues #1, #2, #3)**:
   - **3 APKs in GitHub Release ZIP**: `app/build.gradle.kts` enables `arm64-v8a`, `x86_64`, and `isUniversalApk = true`, while `.github/workflows/android.yml` copies `app-arm64-v8a-release.apk` **twice** (`Butterfly-<ver>.apk` and `Butterfly-<ver>-arm64-v8a.apk`) alongside `Butterfly-<ver>-x86_64.apk` into the `release-apk/` artifact folder (producing a 140+ MB multi-APK zip).
   - **Uncompressed Native & DEX Packaging**: `app/build.gradle.kts` sets `useLegacyPackaging = true` for both `dex` and `jniLibs`, which stores native `.so` binaries (`yt-dlp` Python bundle, SQLCipher, Media3) and DEX files with legacy packaging overhead, and workspace temp/attachment files (`public/assets/aistudio/`, `.build-outputs/`, `tmp/`) bloat the repository ZIP.

5. **10+ Second App Startup Time (Issue #7)**:
   - `MainActivity` blocks the initial UI behind `ButterflyOpeningAnimation`, and `MainViewModel.loadTrending()` waits for an entire `supervisorScope` batch of 6 providers (including slow 6.5-second scrapers) to finish before calling `commitFeedUpdate()`.

---

## 2. Proposed Changes by Area

### A. Fix Ghost Video / Audio Playback & Source Switching (`PlayerCore.kt`, `GlobalPlayerManager.kt`, `MainViewModel.kt`, `VideoPlayerScreen.kt`)
- **Enforce Main-Thread Execution on All Player Controls**:
  - Update `PlayerCore.resetPlayback()`, `GlobalPlayerManager.stopAndClear()`, `GlobalPlayerManager.pause()`, and `GlobalPlayerManager.prepareAndPlay()` to always execute on the main thread (using a main-thread `Handler(Looper.getMainLooper())` or immediate execution if `Looper.myLooper() == Looper.getMainLooper()`) so `player.stop()` and `player.clearMediaItems()` **never** throw `IllegalStateException` from background threads.
- **Session Token & Immediate Stop on Video Change / Error**:
  - At the very start of `MainViewModel.playVideo()`, immediately invoke `GlobalPlayerManager.stopAndClear()` on the main thread and clear `UniversalVideoPlayer`'s active URL state before starting stream resolution for the new video.
  - When `extractionResult` transitions to `ExtractionResult.Loading` or `ExtractionResult.Error`, explicitly call `GlobalPlayerManager.stopAndClear()` in `VideoPlayerScreen` so an older stream can never play underneath a loading or failed source.
  - Guard `MainActivity.onResume() -> syncWithGlobalPlayer()` so it never resumes playback if `extractionResult` is `Loading` or `Error`, or if the user closed the player.

### B. Make OlaMovies Play Reliably (`OlaMoviesProvider.kt` & `MainViewModel.kt`)
- **Parallel Race Architecture (Shortener Bypass + Fast TMDB/Nuvio/VidSrc/Decryptor Streams)**:
  - When you tap any Movie or Series on OlaMovies, `OlaMoviesProvider.getStreamData()` will run **two parallel tracks simultaneously**:
    1. **Track 1 (Direct OlaMovies Shortener + Worker + Fast Headless WebView)**: Attempts `ol-am.top` -> HubCloud/GDFlix/Gofile resolution with a tight 8-second cap so it never starves the overall timeout.
    2. **Track 2 (Instant Multi-Source Cinema Resolution)**: Concurrently resolves the exact TMDB ID (matching title + year + movie/TV type + season/episode) across **Direct Kotlin Extractors (`VidSrcStreamExtractor`, `TMDBEmbedExtractorEngine` — VixSrc, Showbox, AutoEmbed, VidSrc, Decryptor)** and **Nuvio (`/nuvio/resolve`)**.
  - **Combined Playable Server List**:
    - Whichever track resolves first starts playing in **2–4 seconds**, while all resolved servers (OlaMovies Direct links + VixSrc + Showbox + VidSrc + Nuvio + Vega) are populated into the player's quality/server selector so OlaMovies titles **always** play even when `ol-am.top` blocks automated requests.

### C. Keep All Sources Visible & Auto-Skip Dead Sources (`MainViewModel.kt`, `VegaProviderClient.kt`, `NuvioProviderEngine.kt`, `TMDBEmbedExtractorEngine.kt`, `VideoPlayerScreen.kt`)
- **Keep All Providers Visible in the App**:
  - Retain all Vega, Nuvio, VidSrc, and Decryptor sources in the UI and source switcher as requested.
- **Fast Timeout & Automatic Fallback Cascade on Dead Sources**:
  - Reduce per-provider network timeouts during playback resolution so a dead Vega/Nuvio/VidSrc/Decryptor scraper fails fast (within 6–8 seconds instead of hanging for 90–100 seconds).
  - Add **Auto-Skip on Dead Source / Playback Error**:
    - During stream extraction: if the selected Vega/Nuvio/VidSrc/Decryptor provider returns empty streams or times out, automatically cascade to the next available working provider (`Nuvio` -> `Direct TMDB/VidSrc/Decryptor` -> `Vega Unified`) and display a subtle toast/status badge indicating the switch.
    - During `ExoPlayer` playback: if a resolved stream URL throws a media source / HTTP 403 / 404 / manifest error in `ExoPlayer`, automatically advance to the next `PlayableStreamOption` in `streamOptions` without requiring manual user intervention.

### D. Reduce APK Size (~32 MB), Keep `yt-dlp`, & Build `arm64-v8a` Only (`app/build.gradle.kts`, `.github/workflows/android.yml`, `.gitignore`)
- **`arm64-v8a` Only Build Configuration**:
  - In `app/build.gradle.kts`, update `splits.abi` to `include("arm64-v8a")` and set `isUniversalApk = false`, with `ndk.abiFilters += listOf("arm64-v8a")` when building release/split APKs.
  - In `.github/workflows/android.yml`, remove the duplicate APK copy and `x86_64` APK copy so GitHub Actions builds and uploads **only a single `arm64-v8a` APK** (`Butterfly-${VERSION_NAME}-arm64-v8a.apk`) plus `update.json`.
- **Compress Native Libraries & DEX while Keeping `yt-dlp`**:
  - Keep `libs.yt.dlp.android` intact for full YouTube/social extraction and OTA updates.
  - Set `useLegacyPackaging = false` for `dex` and `jniLibs` in `app/build.gradle.kts` (or compress non-executable assets), add `resourceConfigurations += listOf("en", "hi")`, and strip unnecessary debug metadata from `jniLibs` (`excludes += listOf("**/lib**.syms", "**/LICENSE/**", "**/META-INF/*.kotlin_module")`) to shrink the installed/download APK size to ~30–32 MB and improve install/launch speed.
- **Clean Up Repository Bloat for Smaller GitHub ZIP (< 15 MB)**:
  - Remove temporary uploaded files in `/public/assets/aistudio/` and build caches, and update `.gitignore` so exported GitHub source ZIPs only contain clean source code and required assets.

### E. Cut App Startup Time from 10+ Seconds to Under 2 Seconds (`MainActivity.kt`, `MainViewModel.kt`, `HomeScreen.kt`)
- **Non-Blocking Progressive Home Feed**:
  - Refactor `MainViewModel.loadTrending()` so each provider emits results to the Home screen **immediately as soon as that single provider responds** (with a 250ms debounce), instead of waiting for all 6 providers in the `supervisorScope` batch (where a single 6.5s scraper stalls the whole screen).
- **Instant Cached Feed & Fast Splash**:
  - Shorten `ButterflyOpeningAnimation` duration so the Home UI appears immediately, render cached/fast providers (TMDB / YouTube / cached home items) in the first frame, and run heavy `SmartRecommendationEngine` scoring asynchronously in the background after the initial cards are already visible.
  - Optimize Coil image loading with hardware bitmaps and crossfade caching for instant thumbnail rendering.

---

## 3. Verification Plan
1. **Compilation & Packaging Check**: Run `compile_applet` to ensure all Kotlin, Compose, and Gradle changes compile cleanly.
2. **Playback Lifecycle Verification**: Verify that `PlayerCore` and `GlobalPlayerManager` execute all `stopAndClear()` calls on the main thread and that switching from a playing video to a loading or failing source immediately silences and clears the first video.
3. **OlaMovies & Auto-Skip Verification**: Verify that OlaMovies resolves playable streams within seconds via the parallel shortener + TMDB/Nuvio/VidSrc race, and that dead Vega/Nuvio/VidSrc/Decryptor streams automatically cascade to working options.
