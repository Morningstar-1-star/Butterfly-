# Butterfly — Quick Action Plan

**Do not change yt-dlp in this workstream.** Create a branch/tag first.

## First 5 fixes
1. Rotate/revoke exposed vendor credentials and the ThisVid session cookies; remove real values from `.env.example` and Git history. Confirm which keys are active before rotation.
2. Remove trust-all TLS code and every WebView `SSL_ERROR` call to `proceed()`. Keep the WebView fallbacks; allow cleartext only where required for localhost.
3. Make GitHub Releases fail if signing secrets are missing. Never publish a debug APK as the release. Run tests before publishing.
4. Export/test Room migrations; remove `fallbackToDestructiveMigration()` only after migration coverage is proven.
5. Explicitly exclude source login sessions/tokens from backup or disable backup if you do not need restore.

## Safest size/performance changes
6. Delete `app/src/main/assets/vega/providers/**` after a smoke test; retain `vega/manifest.json` and `vega/urls.json`. The JS tree is ~5 MB uncompressed and no Kotlin use was found.
7. Build an `arm64-v8a` phone APK separately from x86_64/emulator builds. Measure the actual output—do not rely on the 135 MB fallback value in `update.json`.
8. Trim ProGuard keep rules gradually; R8/resource shrinking are already enabled for **release** builds, but the CI debug fallback avoids those optimizations.
9. Limit provider concurrency; render partial results; migrate Compose Flow collectors to `collectAsStateWithLifecycle()` where appropriate; log structured errors and rethrow coroutine cancellation.

## Server migration (yt-dlp stays on device)
10. Set Butterfly to your own server URL and remove silent fallback to public Vega servers.
11. Harden `server.js`: validate provider IDs/path containment, validate URL/redirect targets, add rate limits/auth, hard request deadlines, bounded concurrency, memory/TTL limits and standard error codes.
12. Use canonical routes only: `/search/:provider`, `/meta/:provider?link=`, `/episodes/:provider?url=`, `/stream/:provider?link=`, `/resolve/:provider?link=`. `/resolve` expects the original provider post page URL.
13. Pin the Vega provider commit and add a lockfile; make sure the server `providerContext` supports the APIs required by each upstream provider.
14. Port one adapter at a time: TMDB metadata/cache → Nuvio → TMDBEmbed/VidSrc/Decryptor; add tests before deleting its Android duplicate. Keep Android WebView fallbacks for browser/challenge-dependent sources.
15. Add `/home` and `/config`, then add a bounded `/streams` aggregator only after each adapter works. Do not proxy full video through free Render; return playable URLs and required headers.

## Required regression tests
- yt-dlp search/play/seek/subtitles/audio/quality/download unchanged.
- Bilibili signed CDN headers preserved; test seek/resume.
- Existing signed APK update install, Room migration with existing library, Supabase sync.
- Movie + series paths for each migrated provider; server asleep/waking; offline behavior.
- WebView fallback still works without SSL bypass; HLS downloads and torrent playback/transfer survive backgrounding.

For evidence, details, exact file paths and source links, see [the full audit report](Butterfly_Audit_Report.md).
