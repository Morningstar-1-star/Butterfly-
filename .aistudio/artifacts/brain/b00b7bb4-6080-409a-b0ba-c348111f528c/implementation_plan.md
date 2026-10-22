# High-Performance Butterfly Architecture: APK Shrinking, Butterfly-MediaServer & Sub-Second Playback

Transform **Butterfly** into a lean, ultra-fast streaming client (~45 MB Release APK) with sub-second video startup, real SupJav/JAV streams without demo placeholders, and complete **Butterfly-MediaServer** cloud resolution for Vega and Nuvio.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> All core architectural choices below are aligned with your selections and ready for execution upon your approval.

- **Confirmed Decision 1 — True APK Size Reduction (~45 MB Release APK vs 108 MB Debug)**:
  - **The Root Cause of 108 MB**: When GitHub Actions ran without upload keystore secrets, it fell back to `./gradlew :app:assembleDebug`, producing an uncompressed, unstripped Debug APK with two full architectures (`x86_64` + `arm64-v8a`) and zero R8 minification.
  - **The Solution**: Configure GitHub Actions to always build a signed **Release APK** with R8 ProGuard code shrinking, resource shrinking (`isShrinkResources = true`), and ABI splitting. Generate a dedicated mobile `arm64-v8a` Release APK (~42–48 MB) alongside a universal or emulator build. Also eliminate redundant `yt-dlp-android-compat` dependencies if unneeded.
- **Confirmed Decision 2 — Fix SupJav & Purge All Fake Demo Data**:
  - **The Root Cause**: `JavVideoExtractor.kt` contained a fallback method `getCuratedJavCatalog()` with hardcoded Unsplash photos and dummy codes (`SSIS-999`, `IPX-888`, etc.) that triggered whenever SupJav mirrors hit Cloudflare anti-bot. These dummy cards had no playable streams, creating dead video playback.
  - **The Solution**: Remove `getCuratedJavCatalog()` entirely. Update `SupJavResolver` with working live mirrors (`supjav.mom`, `supjav.biz`, `supjav.tv`), user-agent rotation, and direct multi-extractor fallback to live JAV engines (`123AV`, `JavTiful`, `MissAV`, and `Jable`) with real HLS/MP4 streams.
- **Confirmed Decision 3 — Connect Vega & Nuvio to `Butterfly-MediaServer`**:
  - Connect `VegaProviderClient` and `NuvioProvider` to your **Butterfly-MediaServer** (`https://github.com/Morningstar-1-star/Butterfly-MediaServer`) hosted on Render / custom cloud URL.
  - Support automatic health check, catalog browsing (`/providers`, `/search/:provider`), metadata (`/meta/:provider`), episode lists (`/episodes/:provider`), and stream resolution (`/resolve/:provider` and `/stream/:provider`), with configurable server URL in Settings.
- **Confirmed Decision 4 — Sub-Second Instant Video Playback & Fast Switching**:
  - **800ms Initial Buffer**: Tune Media3/ExoPlayer `DefaultLoadControl` to start rendering as soon as 800ms of video data is buffered (down from 2500–5000ms standard).
  - **Instant Player Recycling**: Rather than destroying and re-allocating `ExoPlayer` on video close or switch (`stopAndClear()`), reuse the warmed decoder pipeline, immediately cancel in-flight extraction coroutines, and reset the surface without UI freeze.
  - **Stream URL Caching & Pre-Resolution**: Cache resolved video URLs in memory so opening previously viewed or related videos plays instantaneously with zero extraction delay.

---

## 1. Architecture & Component Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Butterfly Android Client                        │
│                                                                        │
│  ┌─────────────────────────┐          ┌─────────────────────────────┐  │
│  │     Feed & Search       │          │    UniversalVideoPlayer     │  │
│  │ • 120fps smooth scroll  │          │ • Sub-second startup        │  │
│  │ • Instant HD thumbnails │          │ • 800ms fast buffer window  │  │
│  │ • Zero recompositions   │          │ • Instant decoder reuse     │  │
│  └───────────┬─────────────┘          └──────────────┬──────────────┘  │
└──────────────┼───────────────────────────────────────┼─────────────────┘
               │                                       │
               ▼                                       ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        Extraction & Routing Layer                      │
│                                                                        │
│  ┌──────────────────┐   ┌───────────────────┐   ┌───────────────────┐  │
│  │     YouTube      │   │     Bilibili      │   │  Real Adult Tubes │  │
│  │ • NewPipe core   │   │ • Multi-CDN       │   │ • Real SupJav     │  │
│  │ • yt-dlp fallback│   │ • Non-P2P priority│   │ • MissAV / 123AV  │  │
│  │ • Direct streams │   │ • WBI signed API  │   │ • No fake demos   │  │
│  └──────────────────┘   └───────────────────┘   └───────────────────┘  │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│              Butterfly-MediaServer (Render / Cloud Server)             │
│  • Movie, TV Series & Anime Extraction (Nuvio + Vega)                  │
│  • Endpoints: /providers, /search, /meta, /episodes, /resolve, /stream │
│  • Centralized HLS/MP4 Resolution with Zero Client Scraper Overhead    │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Step-by-Step Implementation Sequence

### Step 1: Real APK Size Reduction (~45 MB Release Builds on GitHub Actions)
1. **Remove Redundant Dependencies**: Verify whether `libs.yt.dlp.android.compat` is required or if `yt.dlp.android` alone suffices; prune unused libraries.
2. **Release Keystore in GitHub Actions**:
   - Update `.github/workflows/android.yml` to always run `assembleRelease -PenableAbiSplits=true` using a generated fallback repository keystore when secrets are not populated in GitHub.
   - Ensure R8 code shrinking (`isMinifyEnabled = true`) and resource shrinking (`isShrinkResources = true`) run on all GitHub builds.
   - Output `Butterfly-${VERSION_NAME}-arm64-v8a.apk` as the primary mobile package (~45 MB) and `Butterfly-${VERSION_NAME}-x86_64.apk` for emulators.

### Step 2: SupJav & JAV Scraper Overhaul (Eliminate Fake Demo Data)
1. **Purge Fake Demo Catalog**:
   - Remove `getCuratedJavCatalog()` and all hardcoded demo lists with Unsplash images from `JavVideoExtractor.kt`.
2. **Live SupJav Mirror Scraper**:
   - Update `SupJavResolver.kt` with healthy current mirrors, updated User-Agents, and improved HTML regex parsers for TVLogy / StreamWish / STB embeds.
   - When SupJav mirrors return 403 / Cloudflare challenges, seamlessly fall back to live query resolution against **123AV**, **JavTiful**, **Jable**, or **MissAV** to return genuine playable videos with working streams.
3. **Audit All Video Providers**:
   - Check all registered sources (`YouTube`, `Bilibili`, `Dailymotion`, `Vimeo`, `TubiTV`, `Tencent`, adult tube providers) to verify there are zero simulated or dead demo fallbacks remaining.

### Step 3: Connect Vega and Nuvio to `Butterfly-MediaServer`
1. **Centralize Server URL & Configuration**:
   - Update `AppConfig.kt` and `VegaProviderClient.kt` so both Vega and Nuvio route movie, series, and anime catalog and stream requests through your **Butterfly-MediaServer** URL (`https://butterfly-mediaserver.onrender.com` or custom server URL).
   - In `SettingsScreen.kt`, provide a dedicated "Butterfly-MediaServer" settings card where you can test server health (`/providers` ping) and enter a custom server URL if hosted on a different domain.
2. **Wire Nuvio to Butterfly-MediaServer**:
   - Update `NuvioProvider.kt` and `NuvioProviderEngine.kt` to query Butterfly-MediaServer's `/resolve/nuvio` or `/search/nuvio` endpoints when available, falling back to direct stream options if offline.

### Step 4: Sub-Second Video Playback & Fast Video Switching
1. **ExoPlayer Low-Latency Buffer Configuration**:
   - Update `PlayerCore.kt` to configure `DefaultLoadControl` with:
     - `minBufferMs = 1500`
     - `maxBufferMs = 10000`
     - `bufferForPlaybackMs = 800` (starts playback immediately after 800ms of data is received)
     - `bufferForPlaybackAfterRebufferMs = 1200`
2. **Instant Player Pipeline Recycling**:
   - In `PlaybackSession.kt` and `GlobalPlayerManager.kt`, change `stopAndClear()` so it pauses and detaches media items without deallocating codec instances, allowing new videos to start immediately without a 3-second decoder re-initialization penalty.
3. **Cancellation & Race-Condition Guard**:
   - Ensure that whenever a new video card is clicked, any previous stream extraction coroutine or network call is cancelled instantly with zero delay, ensuring network bandwidth and CPU are 100% dedicated to the new video.
4. **Fast In-Memory Stream Cache**:
   - Add a fast LRU memory cache for resolved stream options so navigating back and forth between videos starts playback instantly with 0ms extraction latency.

---

## 3. Verification & Build Confirmation

1. **Compilation Check**: Run `compile_applet` to ensure zero compilation or lint errors.
2. **Size Audit**: Verify Gradle packaging and dependencies to confirm native library pruning and R8 release rules.
3. **Playback Latency Check**: Test that video switching cancels background operations and initiates playback without delay.
