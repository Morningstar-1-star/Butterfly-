# Butterfly Lightweight & High-Performance Architecture Plan

Transform **Butterfly** from a heavy, 112+ MB multi-scraper bundle into a fast, ~45–55 MB 64-bit streaming client with 120fps scrolling, instant HD thumbnails, live `yt-dlp` OTA updates from GitHub, and centralized movie/series extraction through **Butterfly-MediaServer**.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> All core architectural, packaging, and performance decisions below have been confirmed from your selections and are ready for execution upon approval.

- **Confirmed Decision 1 — Feature & Dependency Pruning (~25–30 MB Raw / ~16 MB Compressed Saved)**:
  - Remove **Google ML Kit Text Recognition (`play-services-mlkit-text-recognition`)** and the in-player Visual Lens OCR/Catbox cropper (`~14 MB` of native C++ OCR models).
  - Remove **Dead Widevine DRM OTT Scrapers** (`Disney+`, `HBO`, `Hotstar`, `SonyLIV`, `Discovery+`, `Amazon miniTV`, `Crunchyroll`) that cannot play DRM streams locally and slow down multi-source search.
  - Remove **CloudSocial (Telegram / Mega), Bunkr, and Catbox Upload Vault** screens, dialogs, databases, and dummy help cards.
  - Remove **Cosmetic & Simulated Bloat**: Gamified Personality Badges, Avatar Customizer sheets, fake CinemaEco / VideoEnhancement shader overlays, and hardcoded `"Channel 1..5"` dummy circles in Subscriptions.
- **Confirmed Decision 2 — Native ABI Splits & R8 Release Shrinking (~45–55 MB Saved per APK)**:
  - Configure ABI splits (`arm64-v8a` for mobile devices, `x86_64` for emulators/PC) and ensure GitHub Actions builds a signed, R8-minified and resource-shrunk Release APK instead of bundling both architectures uncompressed into a single fat debug APK.
- **Confirmed Decision 3 — Hybrid Extraction (`yt-dlp` In-App + `Butterfly-MediaServer` Cloud API)**:
  - Keep **YouTube (`NewPipeExtractor` + `yt-dlp` fallback)**, **Bilibili**, and **in-app `yt-dlp`** untouched and self-contained inside the Android app, while fixing `YtDlpUpdateManager` so it genuinely applies upstream updates from `github.com/yt-dlp/yt-dlp`.
  - Route movie, series, anime, and embed resolution (`Nuvio`, `VidSrc`, `TMDBEmbed`, `Decryptor`, and catalog providers) through **Butterfly-MediaServer** (`https://github.com/Morningstar-1-star/Butterfly-MediaServer`).
- **Confirmed Decision 4 — 120fps Scrolling & Instant HD Thumbnails**:
  - Eliminate full-screen recompositions on every 200ms player progress tick, remove per-frame `onGloballyPositioned` layout listeners on video cards, simplify screen transitions, and fix YouTube `hq720.jpg` 404 stalls with automatic `hqdefault.jpg` fallback and direct connection-pooled DNS.

---

## 1. Overview & Core Concept

- **What It Does**: Butterfly is a unified streaming client combining native high-speed playback for YouTube, Bilibili, and direct video tubes with cloud-powered Movie/Series/Anime aggregation via **Butterfly-MediaServer**.
- **Target Audience / Persona**: Mobile users who want a responsive, lightweight media player that opens instantaneously, scrolls at native 120Hz without frame drops, loads crisp thumbnails immediately, and plays streams reliably on 64-bit Android phones.
- **Key Value**:
  - **~50–60% Smaller Mobile APK**: Drops from 112+ MB to ~45–52 MB on `arm64-v8a` phones without removing `yt-dlp`.
  - **Self-Updating `yt-dlp` Engine**: Connects `YtDlpUpdateManager` directly to `PYTHONPATH` and upstream GitHub releases so new extractor fixes take effect without rebuilding the APK.
  - **Zero-Jank UI & Instant Thumbnails**: Isolates player progress state from feed composition and eliminates HTTP 404 thumbnail stalls.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Home Feed & Source Switching**: Users launch directly into a clean, responsive Home feed with a streamlined Top Bar (Brand logo, Search, and Source Filter chip) — free of upload vault buttons or dummy cloud cards.
  2. **Instant Thumbnail Rendering**: As the user scrolls, video cards render crisp 16:9 HD thumbnails (`hqdefault.jpg` / `hq720.jpg` with automatic 404 fallback) from a dedicated connection-pooled image loader with hardware bitmaps.
  3. **Seamless Playback & Auto-Recovery**: Tapping any video slides up the player with lightweight hardware-accelerated motion. Playback starts via native extractors (YouTube/Bilibili) or **Butterfly-MediaServer** (Movies/Series), with automatic CDN mirror failover and `yt-dlp` fallback.
  4. **Settings & OTA `yt-dlp` Updates**: Users can check and trigger live `yt-dlp` engine updates from `github.com/yt-dlp/yt-dlp` and inspect **Butterfly-MediaServer** health in Settings.

- **Visual Identity & Theme**:
  - *Aesthetic Direction*: **AMOLED Cinema Minimal** — high-contrast dark surfaces, crisp 16:9 edge-to-edge media cards, and zero visual clutter.
  - *Color Palette & Mood*:
    - `--background`: `#09090B` (True AMOLED / Deep Obsidian)
    - `--surface`: `#121216` (Elevated Card & Bar Surface)
    - `--primary`: `#FF3366` (Butterfly Crimson Accent)
    - `--secondary`: `#00E5FF` (Stream Status / HD Badge Accent)
    - `--text-primary`: `#F4F4F6` / `--text-secondary`: `#A1A1AA`
  - *Typography & Hierarchy*: Bold, compact display headers (`20.sp` tight tracking), clean `14.sp` semi-bold 2-line video titles, and `12.sp` muted metadata rows (`Channel • Views • Relative Time`).
  - *Component Styling & Layout*: Full-bleed 16:9 thumbnails on compact phones, adaptive 2–3 column grids on tablets/landscape, and standard Material 3 bottom navigation respecting system gesture insets.
- **Interactive Feedback & Motion**:
  - Fast `180ms` hardware-layer fade/slide transitions instead of simultaneous multi-layer scale+fade+offset animations that cause frame drops during video loading.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1 — ABI Split Packaging vs. Universal Fat APK**
  - *Chosen Approach*: Generate architecture-specific APKs (`arm64-v8a` for mobile phones, `x86_64` for emulators) with R8 minification and resource shrinking enabled on GitHub Releases.
  - *Why*: Bundling both `x86_64` and `arm64-v8a` copies of Python, OpenSSL, and `yt-dlp` doubles the native library footprint (+42 MB of unused x86 code on every phone). Splitting by ABI cuts the mobile APK size nearly in half with zero feature loss.
  - *Alternatives Considered*: Removing `yt-dlp` from the APK entirely — rejected because keeping `yt-dlp` on-device guarantees offline/direct extraction resilience for YouTube, Bilibili, and adult sources.

- **Decision 2 — Fixing In-App `yt-dlp` OTA Updates (`github.com/yt-dlp/yt-dlp`)**
  - *Chosen Approach*: Inject `PYTHONPATH` pointing to the downloaded `yt_dlp_updated` package directory on every `YtDlpRequest` AND support updating the standalone zipapp binary directly from `https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp`.
  - *Why*: Previously, `YtDlpUpdateManager` downloaded the `.whl` archive and extracted it to `filesDir/yt_dlp_updated`, but `YtDlpResolver` never passed `PYTHONPATH` or updated the binary executed by `youtubedl-android`, leaving the bundled version frozen in time.

- **Decision 3 — Offloading Movie/Series Scrapers to `Butterfly-MediaServer`**
  - *Chosen Approach*: Route Movie, TV Series, and Anime catalog/stream extraction through `Butterfly-MediaServer` REST endpoints (`/providers`, `/search/:provider`, `/meta/:provider`, `/episodes/:provider`, `/stream/:provider`, `/resolve/:provider`), while removing broken local Widevine DRM OTT scrapers (`Disney+`, `HBO`, `Hotstar`, `SonyLIV`, `Discovery+`, `Amazon miniTV`).
  - *Why*: Local HTML scrapers for DRM OTT services cannot decrypt Widevine streams and break frequently, whereas `Butterfly-MediaServer` centrally resolves working HLS/MP4 movie and series streams without bloating the Android app.

- **Decision 4 — Decoupling Player Progress State from Feed Composition**
  - *Chosen Approach*: Read `GlobalPlayerManager.progressFraction` only inside the MiniPlayer progress bar composable rather than collecting it at the root of `HomeScreen`, and remove `onGloballyPositioned` from `VideoCard`.
  - *Why*: Collecting `progressFraction` at the top of `HomeScreen` triggered a full-screen recomposition of the entire `LazyColumn` 5 times per second whenever a video played in the background.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```
┌───────────────────────────────────────────────────────────────────────────┐
│                         Butterfly Android UI Layer                        │
│  ┌─────────────────┐   ┌────────────────────┐   ┌──────────────────────┐  │
│  │   HomeScreen    │   │ Subscriptions/Lib  │   │  VideoPlayerScreen   │  │
│  │ (Isolated State │   │ (Real Channels &   │   │ (Hardware ExoPlayer  │  │
│  │  120fps Scroll) │   │  Room Watchlist)   │   │  + Auto-Failover)    │  │
│  └────────┬────────┘   └─────────┬──────────┘   └──────────┬───────────┘  │
└───────────┼──────────────────────┼─────────────────────────┼──────────────┘
            │                      │                         │
            ▼                      ▼                         ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                      ViewModel & Stream Session Layer                     │
│  ┌──────────────────────────────────┐  ┌───────────────────────────────┐  │
│  │          MainViewModel           │  │        PlaybackSession        │  │
│  │ • Concurrency-capped feed fetch  │  │ • Direct HTTP/1.1 & DoH DNS   │  │
│  │ • Active source filtering        │  │ • Multi-mirror CDN failover   │  │
│  └──────┬───────────────────┬───────┘  └───────────────┬───────────────┘  │
└─────────┼───────────────────┼──────────────────────────┼──────────────────┘
          │                   │                          │
          ▼                   ▼                          ▼
┌─────────────────────┐ ┌────────────────────────┐ ┌────────────────────────┐
│ In-App Core Engines │ │  yt-dlp Native Engine  │ │ Butterfly-MediaServer  │
│ • YouTube (NewPipe) │ │ • Bundled Python/yt-dlp│ │ • /providers & /search │
│ • Bilibili (WBI +   │ │ • Live GitHub OTA via  │ │ • /meta & /episodes    │
│   Multi-CDN Mirrors)│ │   PYTHONPATH & zipapp  │ │ • /resolve & /stream   │
│ • Fast Tube Parsers │ │ • Cookie & Header sync │ │ • Server-side scrapers │
└─────────────────────┘ └────────────────────────┘ └────────────────────────┘
```

### Data Model & State Strategy

1. **APK Build & Packaging Pipeline**:
   - Remove `libs.play.services.mlkit.text.recognition` from Gradle dependencies.
   - Configure ABI splits (`arm64-v8a` and `x86_64`) and update the GitHub Actions workflow so release builds produce lean, R8-shrunk APKs for 64-bit mobile devices (`arm64-v8a`) alongside a universal or emulator build.
2. **Stream & Extractor Routing**:
   - **YouTube**: Untouched primary `YouTubeExtractorHelper` (`NewPipeExtractor` + PoToken support) with automatic fallback to `YtDlpResolver`.
   - **Bilibili**: Multi-CDN mirror extraction (`BilibiliProvider`) with non-P2P prioritization, DoH + carrier DNS validation, and `YtDlpResolver` fallback.
   - **Movies / Series / Anime**: Routed to `Butterfly-MediaServer` (`VegaProviderClient` / `VegaProviderRepository`) using canonical `/search/:provider`, `/meta/:provider`, and `/resolve/:provider` endpoints.
   - **Live `yt-dlp` OTA Updater (`YtDlpUpdateManager` + `YtDlpResolver`)**:
     - When an OTA update is downloaded from GitHub (`yt-dlp/yt-dlp`), `YtDlpResolver` injects `Os.setenv("PYTHONPATH", updatedDir.absolutePath, true)` before process execution so the updated Python extractors are actively used by the embedded Python runtime.
3. **Thumbnail & Scroll Pipeline**:
   - Dedicated `ImageLoader` using fast system DNS with connection pooling (`maxRequests = 64`, `maxRequestsPerHost = 16`), RGB_565 / Hardware bitmaps, and smart YouTube thumbnail resolution (`hqdefault.jpg` primary or `hq720.jpg` with automatic `.error()` fallback to `hqdefault.jpg` so missing 720p thumbnails never stall on 404).
   - State reads in `HomeScreen` are scoped to child composables (`MiniPlayerHost`, `TopBarOverlay`) so scrolling the `LazyColumn` causes zero parent recompositions.
