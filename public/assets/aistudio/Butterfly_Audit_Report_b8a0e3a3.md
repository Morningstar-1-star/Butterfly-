# Butterfly Android App + MediaServer — Static Audit Report

**Audit snapshot:** supplied ZIP archives, checked 21 October 2026  
**Priority:** keep the currently working yt-dlp implementation unchanged. This report does not recommend moving yt-dlp to the server.  
**Scope:** Android Kotlin/Compose app, Gradle configuration, GitHub Actions release workflow, app assets, and the supplied Node/Express media-server source.

## Executive verdict

Butterfly is not mainly suffering because it has “too much Kotlin.” The biggest concrete problems are **unsafe release/security defaults, duplicated provider paths, unbounded/fragile network resolution, and an oversized universal native APK**. Fix those in that order; do not start by deleting dozens of Kotlin files.

| Priority | Finding | Recommendation |
|---|---|---|
| P0 — urgent | Real credential-like values and a ThisVid session-cookie file are committed; TLS checks are bypassed in code | Rotate/revoke exposed values; remove them from the repository/history; remove certificate bypasses |
| P0 — urgent | Release workflow publishes a debug APK when signing secrets are absent | Fail the release job instead; publish debug builds under a separate, clearly labelled artifact path |
| P0 — urgent | Room can destructively wipe a user's database if a migration is missed | Export schema, test every migration, remove destructive fallback only after validating migration coverage |
| P1 — high | App and server disagree about the API; the app guesses routes and falls back to public third-party servers | Use your own server URL and one versioned contract; remove automatic third-party fallback unless explicitly enabled |
| P1 — high | Server accepts untrusted provider names/URLs and has no authentication or rate limiting | Validate provider IDs and URLs; add abuse controls, deadlines, request limits and safe error responses |
| P1 — high | App bundles about **5 MB uncompressed** of Vega provider assets which the Kotlin code does not load | Remove only `assets/vega/providers/**` after a regression test; retain `manifest.json` and `urls.json` |
| P1 — high | Universal APK supports ARM64 and x86_64 with ABI splits disabled | Produce an ARM64-only APK for phone installs; keep an emulator/universal build separate if needed |
| P2 — medium | Large UI/ViewModel files, hundreds of Flow collections, many HTTP client builders and silent catches | Refactor gradually after smoke tests; make provider concurrency bounded and errors observable |
| P2 — medium | Dependencies are old, and native libraries require current-device compatibility checks | Upgrade one family at a time; do not combine dependency updates with provider migration |

**Important limitation:** I inspected the supplied source, but could not run an Android build or test playback on a phone because this environment has no configured Android SDK/device. The server ZIP does not contain `node_modules` or the cloned `vega-providers/dist` tree, so I only verified Node syntax—not live provider execution. A source file being present does **not** prove a feature works on-device.

---

## 1. Confirmed app findings

### 1.1 Secrets and sessions — act first

- `Butterfly--main/.env.example` contains a non-placeholder-looking YouTube API key. The Gradle Secrets plugin is configured to fall back to `.env.example`, but I found no Kotlin references to `BuildConfig.YOUTUBE_API_KEY`; therefore **the key is definitely committed, but I cannot prove from this ZIP alone that it is used or packaged in the APK**. Rotate/restrict it anyway, replace it with a placeholder in the template, and confirm the Google Cloud API restrictions and generated BuildConfig fields.
- `app/src/main/java/com/example/util/AppConfig.kt` includes default TMDB API credentials/read token and keys for Watchmode, SubDL and OpenSubtitles. They are used by app code, so anyone with the APK or public repository can recover them. Rotate vendor keys if they are yours and still active. Keep the Trakt client ID conceptually separate: a client ID is generally an identifier, not equivalent to a private client secret.
- The root `cookies.txt` includes ThisVid session cookies. Treat these as compromised: revoke/log out that session where possible, delete the file, and remove it from Git history if the repository was public. Deleting it from the latest commit alone does not erase old public commits.
- `firebase-applet-config.json` is present at the repository root, while this app source declares no Firebase dependency. It appears unnecessary for the supplied Android app; remove it if no external workflow uses it. A Firebase client config is not automatically a secret by itself, so also verify Firebase-side access rules before deciding the exposure is harmless.
- The claim that the Supabase table RLS policies are enabled **cannot be verified from these ZIPs**. RLS is configured in the remote Supabase project, not proven merely by client code or a key.

**Recommended handling:** keep only dummy examples in Git; use GitHub Actions secrets for signing; use server environment variables for server-only vendor credentials. Remember: anything embedded in an Android APK—including a custom `x-api-key`—can be extracted. A static key inside the APK is a speed bump, not authentication against a determined user.

### 1.2 TLS and network security are too permissive

- `app/src/main/res/xml/network_security_config.xml` allows cleartext HTTP in the base config and trusts both system and user-installed CA certificates globally. `AndroidManifest.xml` also sets `usesCleartextTraffic="true"`.
- `app/src/main/java/com/example/extractor/sextb/SextbNetwork.kt` creates a trust-all certificate manager and disables hostname verification.
- `SextbWebViewFallback.kt` and `SupJavWebViewFallback.kt` each contain SSL-error handlers calling `handler?.proceed()` (four call sites total).

These weaken protection against interception and fake certificates. **Keep the WebView fallbacks; remove the SSL bypasses.** A blocked/challenged site should return a clear failure or `WEBVIEW_REQUIRED`, not accept an invalid certificate. Allow HTTP only for the local endpoints that genuinely require it (such as `127.0.0.1`), and explicitly list any unavoidable HTTP provider hosts rather than enabling it globally.

### 1.3 Backup rules may include sensitive app state

`AndroidManifest.xml` has `allowBackup=true`. `backup_rules.xml` and `data_extraction_rules.xml` are essentially empty templates and do not define meaningful exclusions. The app has source-account/session handling, so do not assume credentials are excluded from backups. Explicitly exclude session/token preferences and other sensitive state from cloud/device transfer backups, or disable backup if no data should be restored. Keep ordinary user library/history backup only if it is intentionally supported and tested. Android backup behavior is documented at [Auto Backup](https://developer.android.com/identity/data/autobackup).

### 1.4 Room migration fallback can destroy the library

`app/src/main/java/com/example/db/AppDatabase.kt` is at schema version 11, has migrations 1→11, but sets `exportSchema=false` and calls `fallbackToDestructiveMigration()`. If a future upgrade path is missing or not registered, Room may recreate the database and lose locally stored library/history/download metadata.

**Fix sequence:** turn on schema export; commit the generated schemas; add `MigrationTestHelper` tests from supported old versions to version 11/current; verify every migration; only then remove the destructive fallback. Do not test migrations against the only copy of personal user data.

### 1.5 CI can publish a debug build as a release

`.github/workflows/android.yml` falls back to `:app:assembleDebug` when signing secrets are missing, then uploads that APK to a GitHub Release with a release-style filename. Debug is not minified or resource-shrunk. The workflow's “fixed keystore” message is misleading: the checked-in `debug.keystore` is not present in the supplied ZIP and is ignored by Git, so a hosted runner may generate a different debug signature on later runs. That can prevent an installed build from updating in-place.

- Make the release job **fail** unless the release keystore and required passwords exist.
- If you need unsigned/debug builds for yourself, upload them from a separate workflow/artifact named `debug`; never make them the “latest” release.
- Add `testDebugUnitTest` (and relevant instrumentation/lint checks) before publishing. There are 17 test files, but the release workflow currently does not run tests or lint.
- Gradle caching is explicitly disabled (`cache-disabled: true`), unnecessarily slowing builds.
- `update.json` has a hard-coded 135,000,000-byte fallback. That is a fallback value, not proof that an APK is 135 MB. Always measure the actual artifact size.
- The supplied Gradle wrapper specifies Gradle 9.3.1 with AGP 9.1.1 in the version catalog; do not copy advice about an old Gradle 9.2.1 mismatch into this snapshot without rechecking the exact commit.

### 1.6 R8 is enabled for release already—but the workflow often skips it

`app/build.gradle.kts` sets both `isMinifyEnabled=true` and `isShrinkResources=true` for `release`. So the correct finding is **not** “R8 is disabled.” The problem is that the CI fallback publishes `debug`, and the broad ProGuard rules make optimization less effective on release builds.

`app/proguard-rules.pro` broadly keeps complete application packages such as `com.example.model`, `.db`, `.extractor`, `.resolver`, `.vega`, `.subtitles`, `.downloader`, `.ui.player`, etc., and keeps several entire third-party packages including Media3, OkHttp, Jsoup, libtorrent and Coil. This can stop R8 removing unreachable classes and methods. Do not delete every keep rule at once: reflection, JNI, serialization, Room/Moshi and provider registration need appropriate rules. Reduce package-wide keeps one family at a time, build a minified APK, then exercise playback, downloads, login, database, and dynamic providers. Use `mapping.txt`/`usage.txt` to see what R8 removed and why. Official guide: [R8 optimization](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization/).

### 1.7 Best low-risk APK-size change: remove unused bundled Vega JavaScript

`app/src/main/assets/vega/providers/` contains about 260 files and totals roughly **5.0 MB uncompressed**; `providerContext.js` alone is about 2.66 MB. In the Kotlin source I found reads of `vega/manifest.json` and `vega/urls.json`, but no code loading the JS provider tree or `providerContext.js`. `VegaInAppEngine` performs Kotlin-side HTML scraping; the bundled JS is not being executed by that engine.

**Likely safe candidate:** remove `app/src/main/assets/vega/providers/**`, *after* a quick runtime test confirms the app still starts, lists providers, refreshes provider URLs, searches and extracts via the server. **Keep** `app/src/main/assets/vega/manifest.json` and `app/src/main/assets/vega/urls.json` because the registry reads them. The actual compressed APK reduction must be measured; 5 MB is the uncompressed source-asset size, not a guaranteed 5 MB reduction in the APK.

### 1.8 ABI packaging is likely a large size lever

The app sets `abiFilters` to `x86_64` and `arm64-v8a`, but ABI splits are disabled. It also includes native libtorrent artifacts for both ARM64 and x86_64. That produces a package intended for more than one CPU architecture, even though the intended install target is an Android phone.

- If you only distribute to ARM64 Android phones, build an ARM64-only release.
- If you also want emulator/desktop testing, build that separately rather than making every phone download contain both ABIs.
- Measure the APK before and after; do not promise an exact saving until the artifact is built.
- Keep yt-dlp unchanged, but validate every bundled native library—including the prebuilt yt-dlp and libtorrent libraries—on devices using 16 KB memory pages. Android's official guidance: [16 KB page-size support](https://developer.android.com/guide/practices/page-sizes).

Android docs: [APK ABI splits](https://developer.android.com/build/configure-apk-splits).

### 1.9 Fake/nonfunctional Whisper path

`app/src/main/cpp/WhisperJni.cpp` is a stub: it creates a context and marks it loaded without loading a Whisper model; `fullTranscribe()` returns `0` (“success”) without performing transcription. The UI/diagnostic code checks whether this native binding is available, which can make an unimplemented engine look ready. `WhisperEngine` itself is referenced by nearby Whisper classes but appears not to have an actual application call path for transcription.

**Recommendation:** either label Whisper as unavailable/in-development and remove the false-positive “ready” behavior, or implement it properly with a real native engine. Since the shown C++ stub is tiny, deleting it will not materially reduce the APK by itself. Do not advertise it as functioning subtitle transcription.

### 1.10 Dead-code candidates: do not bulk-delete them yet

A simple source-text scan found several classes with only self-references/no obvious callers. Candidates to inspect in a separate branch include:

- `ui/components/StreamSourcePickerBottomSheet.kt`, `VideoReactionsSection.kt`, `ui/torrent/TorrentStreamOverlay.kt`
- `ui/viewmodels/HomeViewModel.kt`, `PlayerViewModel.kt`, `SearchViewModel.kt`, `ExploreViewModel.kt`, `LibraryViewModel.kt`, `DownloadViewModel.kt`
- `db/CacheRepository.kt`, `metadata/stash/StashCommunityScraperEngine.kt`, `extractor/danmaku/DanmakuSubtitleConverter.kt`
- `effects/ArtCnnEffect.kt`, `FsrcnnxEffect.kt`, `RavuEffect.kt`
- `domain/ResolveMetadataUseCase.kt`, `ResolveStreamsUseCase.kt`, and `core/interfaces/*`
- `subtitles/providers/*` only if the newer subtitle plugin path fully replaces them and tests show no runtime/dynamic references.

**Not safe for blind deletion:** `ProviderRegistry` appears in tests, and `JsChallengeEvaluator` has test references. Static grep also cannot reliably detect reflection, JSON-based registrations, JNI, manifest entry points or dynamic asset loads. Use a deletion branch, delete one feature group, run all tests/builds, and exercise the associated UI.

Repository-only cleanup candidates (`check_braces.py`, `check_level.py`, unused metadata files, `.aistudio`, `kt_player.js`, unused config files) will not reduce APK size unless they are packaged or referenced at runtime. Check file-read references first. Similarly, the unreferenced aliases for Retrofit, CameraX, Accompanist, Location, DataStore and Navigation in `libs.versions.toml` are catalog entries rather than dependencies in `app/build.gradle.kts`; deleting only those aliases will not shrink the APK.

---

## 2. Performance and reliability

### 2.1 Avoid launching every provider at once

The app has provider engines for Vega, Nuvio, TMDBEmbed, VidSrc, Decryptor and other sources. Static source scanning cannot prove each is operational, and scraping sites change frequently. Loading/fanning out lots of independent providers at once increases radio wakeups, TLS handshakes, allocations and time spent waiting on the slowest source.

Recommended behavior:

1. Start a small, bounded group (for example 3–4 providers concurrently; make this tunable).
2. Render partial results as providers return instead of blocking until every provider finishes.
3. Apply a per-provider timeout, bounded retry/backoff and a circuit breaker for repeated failures.
4. Do not automatically retry 403/Cloudflare failures repeatedly; mark the source as browser-required/temporarily unavailable and let the user open its Android WebView fallback where supported.
5. Cancel work when the search screen is left or the query changes; make sure coroutine cancellation is not swallowed by generic catches.

### 2.2 Lifecycle-aware Compose collection

The source has approximately **370 `collectAsState()` call sites** and no `collectAsStateWithLifecycle()` uses. Android's documentation recommends lifecycle-aware Flow collection on Android; the lifecycle-aware API stops collecting while the UI is not started, conserving resources ([official Compose state guide](https://developer.android.com/develop/ui/compose/state)).

Change one screen at a time, prioritizing home/search/player/settings and high-frequency state. Do not mechanically replace every call: flows that intentionally remain active for platform-agnostic UI or background work need separate handling.

### 2.3 Centralize networking without breaking special headers

A text scan finds about **171 `OkHttpClient.Builder()` occurrences**. They are not necessarily 171 simultaneous clients at runtime, but they are a maintainability/thread-pool risk if each makes an independent client. Create a `NetworkManager` with shared connection pools, DNS, cache and normal TLS defaults; derive clients with `.newBuilder()` for timeouts/cookies where needed.

Do **not** force every extractor through identical headers. Bilibili and other signed-CDN sources may require their extraction-time `Referer`, `User-Agent` and cookies to remain exact. Preserve per-source header contracts and add regression tests before altering request-header helpers.

There is also a potential initialization race where image/network code refers to `SecureDnsManager.appDns` before asynchronous DNS initialization completes. Provide a safe default and atomically switch to the configured resolver after initialization. Do not make image loading wait on DNS setup.

### 2.4 Make errors observable and cancellation safe

The scan found about **1,389 catch blocks**, including roughly **168 empty catch bodies**. Some empty catches are legitimate cleanup (`close()`/`destroy()`), but empty catches in extraction/resolution hide real defects. In provider/network code, log a structured failure containing provider ID, stage, error class and elapsed time—never session cookies, bearer tokens or full secret-bearing URLs. Map errors such as `WEBVIEW_REQUIRED`, `TIMEOUT`, `HTTP_403`, `HTTP_404`, `DNS_ERROR`, `TLS_ERROR` and `PARSE_ERROR` into a standard result.

In coroutine code, catch `CancellationException` only to rethrow it (or let it propagate). Treating cancellation as an ordinary source failure can keep obsolete searches/extractions running.

### 2.5 Giant files slow maintenance, not directly the APK

Approximate source sizes in this snapshot:

| File | Size |
|---|---:|
| `ui/MainViewModel.kt` | 7,374 lines / ~382 KB |
| `ui/screens/SettingsScreen.kt` | 3,889 lines / ~259 KB |
| `ui/player/UniversalVideoPlayer.kt` | 3,470 lines / ~187 KB |

Split these by responsibility only after test coverage improves: e.g. playback state/session, source discovery, library/history, downloads, settings sub-screens and network/provider orchestration. Refactoring alone will not yield a large APK shrink; its value is fewer regressions, easier testing and better control over recomposition/work.

Also review two `runBlocking` uses in `MultiSourceProvider.kt` and `TencentProvider.kt`, and two short `Thread.sleep` calls in torrent code. These are potential thread stalls depending on call context. Replace blocking waits with suspend-aware APIs only after confirming the exact execution thread and native libtorrent requirements.

`largeHeap=true` is not a performance fix. Measure heap peaks and leaks, then remove it if the app does not truly need the larger heap. Add image sizing, list paging/lazy composition and stable keys where profiling shows actual waste.

### 2.6 Downloads on current Android versions

The manifest declares a `dataSync` foreground service and target SDK is 36. Android 15+ limits `dataSync` foreground services to a total of six hours per 24-hour period while backgrounded; `mediaProcessing` has a similar limit ([official timeout documentation](https://developer.android.com/develop/background-work/services/fgs/timeout?authuser=19)). This is relevant for long transfers, but **do not blindly convert all download types**: HLS, ordinary file downloads and torrent transfers have different lifecycle/network requirements. Test long downloads across app backgrounding, process death, reboot and timeout, then use the appropriate user-initiated data-transfer job where the platform API and task type fit.

---

## 3. What the supplied MediaServer actually does

### 3.1 It is currently a Vega-only server

The ZIP contains `server.js`, `setup-vega.js`, `package.json` and `providers/index.json`; it does **not** contain implementations for the proposed `/streams`, `/home`, `/config` or `/proxy` endpoints, nor server adapters for Nuvio, TMDBEmbed, VidSrc or Decryptor. Its current routes are:

| Route | Current meaning |
|---|---|
| `GET /` | Basic server status/provider count |
| `GET /health` | Health check |
| `GET /providers` | Enumerates compiled Vega provider modules |
| `GET /search/:provider?q=...` | Vega `posts.getSearchPosts` |
| `GET /meta/:provider?link=...` | Vega metadata; also returns candidates/groups |
| `GET /episodes/:provider?url=...` | Resolves the selected episode group |
| `GET /stream/:provider?link=...` | Runs Vega stream extractor for a valid stream-source link |
| `GET /resolve/:provider?link=...` | Resolves from the original search/post page link via metadata → candidate links → stream extraction |
| `GET /diagnose?q=...` | Runs provider diagnostics sequentially |

Vega's official contract similarly separates `posts`, `meta`, optional `episodes` and `stream` modules; direct links and `episodesLink` listings are different kinds of inputs ([upstream Vega provider interface](https://github.com/Zenda-Cross/vega-providers)).

### 3.2 The app/server API mismatch is real

`VegaProviderClient.kt` defaults to `https://vega.strem.fun` and includes `https://vega-providers.fly.dev` as fallbacks. It tries many guessed endpoint formats for search, metadata and stream resolution. But your server has one canonical path per operation. That means unnecessary network requests and ambiguous failures. The client can also fall back to an unrelated third-party host even when your own configured server fails; this can leak search terms/item URLs and hide the real status of your own service.

**Fix:** configure your own Render URL as the server endpoint; remove automatic public-host fallback or make it an explicit user setting. Use canonical routes only. The `/resolve/:provider` route needs the original provider post page URL; it is not interchangeable with a raw direct stream URL.

The app's normal provider-list path returns the bundled `VegaProviderRegistry` list before consulting server data. Until you change it, adding providers server-side will not necessarily update what the app displays. Add a deliberate server capability/version handshake and let remote provider health/config update the list with a safe local fallback.

### 3.3 Critical server hardening

| Finding | Why it matters | Action |
|---|---|---|
| `loadModule()` constructs a path from the request's provider value and does not first validate the ID against the compiled provider registry | Path traversal/unintended module loading risk | Accept only IDs returned by `getProviders()`, enforce a strict ID regex, resolve the final path and confirm it remains inside the compiled provider directory |
| No authentication or rate limiting | Anyone can automate expensive scraping against the server | Add per-IP throttles, concurrency caps, response/request size limits and access control. Do not consider an APK-embedded static API key secret |
| Link parameters feed extractor logic without a centralized URL policy | Abuse/SSRF risk if a provider follows attacker-controlled URLs or redirects | Require HTTPS/HTTP explicitly, block localhost/private/link-local/cloud metadata destinations, validate redirects and apply provider/domain allowlists wherever possible |
| Endpoints return raw error strings with HTTP 500 | Leaks implementation detail and makes client behavior inconsistent | Return stable JSON error codes; keep internal details in server logs with sensitive URL/token redaction |
| `AbortController` timeouts rely on providers honoring `signal` | A provider can keep work alive after the intended deadline | Enforce an outer wall-clock deadline and abort/cancel work; bound concurrent provider tasks |
| `GET /diagnose` runs through providers sequentially and is public | Expensive to call and may be abused | Restrict to admin use or scheduled CI; cache its results and serve a small status payload |
| One global `Map` is used as `kvStore` by every provider | Key collisions, unbounded memory growth and accidental cross-provider state | Namespace entries by provider; add TTL/size caps; avoid storing user-specific secrets in a global shared map |
| `distPath` is relative to the process working directory | A different launch directory can cause providers to appear missing | Resolve it from `import.meta.url`/the server source directory |
| `setup-vega.js` follows current upstream `main` and installs upstream dependencies without pinning a commit | A future upstream change can silently break production | Pin a tested commit; keep a lockfile for this server; make deployments reproducible and test provider contract compatibility |
| Root package lists `tls-impersonate`, `esbuild`, `terser`, `zod` without imports in `server.js` | Unneeded root install weight/attack surface unless a build path truly uses them | Remove only after confirming no server script imports them; upstream Vega has its own separate dependency install |

`node --check server.js` and `node --check setup-vega.js` passed. That checks JavaScript syntax only—it does not prove the application starts or any provider works. The archive lacks `vega-providers/dist`, `node_modules` and a lockfile, so live routes were not exercised.

### 3.4 `/resolve` is a good concept but needs a hard time budget

The server's resolver first parses metadata, expands episode groups, then tests up to five candidate links **sequentially**, each with a 30-second stream timeout. Episode groups can also be fetched sequentially. If the upstream module ignores the abort signal, actual elapsed time may exceed the nominal timeout.

Use an overall deadline for the whole request; run only two or three candidates concurrently to avoid hammering source sites; return as soon as a valid stream is found and cancel remaining attempts. Keep the concurrency configurable and modest. Preserve required `Referer`/`User-Agent`/cookie headers in each returned source.

### 3.5 Render free plan: do not design around an always-awake server

Render confirms that free web services sleep after 15 minutes with no inbound traffic and can take about a minute to start again; the filesystem is ephemeral, and free services share 750 monthly instance-hours per workspace ([official Render free-service limits](https://render.com/docs/free)). A cron ping every 10 minutes would keep the service awake most of the time and consume nearly all the allowance for one service; it is not a robust free production guarantee.

Better UX: expose `/health`, show `Server sleeping/waking`, retry with exponential backoff for a bounded period, then offer retry/manual fallback. Keep provider state/cache in memory only if loss on restart is harmless. Never store the only copy of user settings or long-lived secrets in Render's local filesystem.

**Do not proxy entire video streams through the free Render instance.** Use it for search, metadata, extraction and resolving direct stream URLs. Send the stream URL plus only the headers the player needs back to Android Media3. A server-side playlist proxy should be added only for a narrowly justified playlist/CORS/header use case, with strict host restrictions and size limits—not as a universal media relay.

---

## 4. Best target architecture for Butterfly

### Keep on the phone

- **yt-dlp, NewPipe integration and all current yt-dlp behavior.** Do not move/rewrite it as part of this project. Treat it as a protected subsystem and run its regression checklist before every release.
- Media3/player UI, audio/subtitle selection, playback session, local files, file permissions, Room library/history, Supabase user sync, WorkManager/download UI and **libtorrent playback**.
- Android WebView fallbacks for sites that need a genuine browser session or challenge solving (including the Hanime/Sextb/SupJav/ThisVid paths you identified). Server responses may say `WEBVIEW_REQUIRED`; the app then launches the appropriate WebView path. Do not send their session cookies to the server by default.
- Local torrent transfer engine. Moving provider metadata/search for torrents could be considered later, but the phone's actual torrent playback/download engine should stay local unless a distinct server-side use case is designed.

### Move to your server progressively

- Vega JS providers already compatible with the server's provider context.
- Nuvio provider logic after implementing and testing the correct JS/runtime capabilities.
- TMDB metadata/cache and the TMDBEmbed extractor (these are different things: TMDB is mainly metadata; TMDBEmbed is a source extractor).
- VidSrc and Decryptor only after confirming runtime compatibility and source-specific headers. The app's existing VidSrc WebView/native fallbacks should remain available where needed.
- Other provider adapters and catalog data only when they are server-safe and do not need device-local storage, private user cookies, JNI or platform APIs.

Moving code to a server can reduce app code/dependencies, but it also introduces cold starts, remote failures, latency and privacy tradeoffs. Do not migrate everything in one change. If server unavailable, show the state explicitly and optionally use remaining on-device providers; do not silently send the query to an unrelated server.

### Build one stable API instead of guessing endpoints

Use a versioned contract, for example `/api/v1`:

- `GET /api/v1/health`
- `GET /api/v1/providers` — provider IDs, capabilities, version, last health status
- `GET /api/v1/config` — non-secret domains/feature flags
- `GET /api/v1/home/:provider` — provider catalog/feed using `posts.getPosts`, instead of fake search terms
- `GET /api/v1/search/:provider?q=...`
- `GET /api/v1/meta/:provider?link=...`
- `GET /api/v1/episodes/:provider?url=...`
- `POST /api/v1/resolve` — provider ID + original post URL + media type/episode index; use a JSON body when URLs may contain long/sensitive tokens
- Later, `GET/POST /api/v1/streams` — a server aggregator that fans out to selected sources, normalizes/deduplicates results, and returns partial results with provider provenance

Keep the response schema predictable, such as `ok`, `provider`, `results/streams`, `errorCode`, `elapsedMs` and an optional expiry timestamp. Preserve stream headers in the response where legitimate and necessary. Never send API credentials/session cookies back in error messages or logs.

**Caching:** start with a short TTL for search results and a longer TTL for metadata; make them configurable and include provider/domain/version in cache keys. Do not assume a resolved stream URL is safe to cache for 3–5 minutes—signed links can expire faster. Prefer caching search/meta and re-resolving stream URLs near playback time.

**Compatibility:** upstream Vega providers can require helpers beyond `axios`, `cheerio`, `commonHeaders`, `openWebView` and the server's global `kvStore`. Compare the provider context and type definitions of the pinned upstream commit before migrating a batch. Start with one representative provider, add contract tests, then scale up.

---

## 5. Exact order I recommend

### Phase A — security and release correctness, no source migration

1. Revoke the ThisVid session cookies; rotate/restrict any active exposed vendor keys; rewrite `.env.example` with placeholders and remove secrets/cookies from Git history.
2. Remove TLS trust-all/hostname-verifier code and all WebView `SSL_ERROR` proceed calls; keep the WebView fallback itself.
3. Restrict cleartext to required local endpoints; define backup exclusions for source sessions/tokens.
4. Change release CI to fail if signing secrets are missing. Keep debug builds separate. Run unit tests and lint before release.
5. Export Room schemas and test migrations before removing destructive fallback.
6. Tag/branch the current working source and record baseline APK size, install/launch time, provider success, yt-dlp playback, seeking and downloads.

### Phase B — easiest size and performance wins

7. Delete the unused `assets/vega/providers/**` tree only after the quick test described above; keep the JSON registry files.
8. Build an ARM64-only phone APK separately from x86_64/emulator artifacts; record actual compressed APK sizes.
9. Tighten broad R8 keep rules one package at a time; retain rules proven necessary by reflection/JNI/serialization tests.
10. Use a shared HTTP client foundation with per-source customizations; bound provider fan-out; add lifecycle-aware collection; log classified failures and preserve cancellation.
11. Make one provider/status diagnostic screen show per-source response time, result count, last error class and last successful time. Keep sensitive URLs/cookies out of diagnostics.

### Phase C — server migration, keep yt-dlp untouched

12. Harden the server (`provider` allowlist/path boundary, URL/redirect safeguards, request throttling, overall deadlines, error codes, memory/cache limits, redacted logs).
13. Pin an upstream Vega commit and use a reproducible dependency lock. Set the app's server URL to your Render URL and remove silent public fallback.
14. Fix the app to call only the canonical endpoints and correctly distinguish original post URLs from direct stream-source links.
15. Implement `/config` and `/providers` health/version support; then `/home/:provider` using real provider catalog posts.
16. Port one non-yt-dlp adapter—suggested order: TMDB metadata/cache, then Nuvio, then TMDBEmbed/VidSrc/Decryptor one at a time. Add server contract fixtures and compare returned results to current Android implementation.
17. Build `/streams` aggregation only after individual adapters are reliable. Use a small concurrency cap and partial-result UI. Move deduplication/ranking only if the single source of truth clearly belongs on the server; source-code savings from moving a ~27 KB helper group alone will be small.
18. Remove the matching Android implementation/dependencies only after production-like server tests show parity and a fallback plan exists. Never remove yt-dlp in this workstream.

### Phase D — maintainability and optional features

19. Split `MainViewModel`, `SettingsScreen` and player orchestration into feature units after tests cover the behaviors.
20. Decide whether Whisper is a real roadmap feature. If not, remove the false UI/diagnostics path; if yes, implement actual inference and test model download/memory.
21. Test long HLS/download/torrent jobs against current Android foreground-service limits; do not simply assume WorkManager or a data-transfer job suits all of them.
22. Upgrade libraries in separate changes, not together with source migration. At the audit date, official Android release pages list Media3 **1.11.1** (10 Sep 2026), Lifecycle **2.11.0** (23 Sep), Room **2.8.5** (9 Sep) and WorkManager **2.12.0** (23 Sep). Butterfly has Media3 1.5.1, Lifecycle 2.8.7, Room 2.7.0 and WorkManager 2.10.0. Review the changelogs and compatibility before upgrading. The Compose BOM is a separate versioning scheme—do not set its version to a Compose UI artifact version like `1.11.4`; use the official [Compose BOM mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping). Treat OkHttp 4→5 as a separate compatibility change.

---

## 6. What I would not do

- **Do not touch yt-dlp or move it to Render** during these changes. Keep its dependency versions and runtime setup fixed; test it independently.
- Do not delete every class with no obvious caller from grep. Reflection, tests, JNI, Room, Moshi and runtime provider registration complicate static dead-code detection.
- Do not remove `manifest.json` or `urls.json` with the unused Vega JS tree.
- Do not tell users “all sources synced” when only URL mirrors were refreshed. The code's `refreshUrlsFromNetwork()` downloads `urls.json`; it does not sync every JavaScript provider script to the app.
- Do not silently proxy full media through free Render or keep a service awake forever with cron pings. Make cold starts visible and retry gracefully.
- Do not upgrade all libraries, refactor large UI files, migrate providers and rewrite player headers in the same pull request. That makes regression diagnosis nearly impossible.
- Do not equate old source code or old version aliases with APK size by themselves. Only code/dependencies/resources actually packaged into the build can affect the final artifact; measure each change.

---

## 7. Regression checklist before each release

**Must pass on the real phone:**

- [ ] Cold install and update install over the previous signed release.
- [ ] Start app offline; server asleep/unreachable; server waking; retry/fallback behavior.
- [ ] yt-dlp: search/extract/play/seek/subtitle/audio switching/quality switching/download where supported—no implementation changes in this project.
- [ ] Bilibili and signed-CDN playback preserve extraction-time headers; test seek and resume.
- [ ] ThisVid/Sextb/SupJav/Hanime WebView fallback still opens, but never bypasses a TLS error.
- [ ] One movie and one series through each migrated provider: search → metadata → episodes → stream → playback.
- [ ] Local file playback, HLS download, background download, torrent streaming/download and app process recreation.
- [ ] Login/session persistence, Supabase sync and local database upgrade with an existing library.
- [ ] Check logcat/diagnostics for hidden provider errors, no secret or cookie leakage, bounded concurrent requests and low-memory behavior.
- [ ] Record release APK size, native ABI contents, cold-start time and home/search time before/after every optimization.

## 8. Source references used for external verification

- [Android network security configuration](https://developer.android.com/privacy-and-security/security-config) — cleartext traffic and CA trust.
- [Android Auto Backup](https://developer.android.com/identity/data/autobackup) — backup exclusions.
- [R8 app optimization](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization/) and [reduce APK size](https://developer.android.com/topic/performance/reduce-apk-size) — shrink/keep rules and resource optimization.
- [APK ABI splits](https://developer.android.com/build/configure-apk-splits) — per-architecture APKs.
- [16 KB memory page size support](https://developer.android.com/guide/practices/page-sizes) — native library compatibility.
- [Compose state collection](https://developer.android.com/develop/ui/compose/state) — `collectAsStateWithLifecycle()` guidance.
- [Foreground service timeout rules](https://developer.android.com/develop/background-work/services/fgs/timeout?authuser=19) — `dataSync`/`mediaProcessing` limits.
- [Render free-service limits](https://render.com/docs/free) — 15-minute idle sleep, cold starts, ephemeral filesystem and monthly hours.
- [Media3 releases](https://developer.android.com/jetpack/androidx/releases/media3), [Lifecycle releases](https://developer.android.com/jetpack/androidx/releases/lifecycle), [Room releases](https://developer.android.com/jetpack/androidx/releases/room), [WorkManager releases](https://developer.android.com/jetpack/androidx/releases/work) — current stable-version checks.
- [Compose BOM mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping) — BOM versioning.
- [TMDB application authentication](https://developer.themoviedb.org/docs/authentication-application) — API key versus read-access token behavior.
- [Vega provider interface](https://github.com/Zenda-Cross/vega-providers) — expected `posts`, `meta`, `episodes`, `stream` module contracts.

**Bottom line:** your best first size win is removing unused bundled Vega JS assets and producing an ARM64-only signed APK. Your best first stability wins are fixing the release workflow, removing TLS bypasses, protecting Room data, and making the app call your own server through one strict API. Then migrate non-yt-dlp providers one at a time and remove their Android duplicates only after parity tests pass.
