# Bookzor — Roadmap, Epics, Stories & Task Plan

This roadmap is designed for **autonomous AI agents** and **junior software engineers**. Each task includes specific files, instructions, inputs/outputs, and verifiable acceptance criteria.

---

## 🗺️ Epic Overview

| Epic # | Name | Description | Key Tech Stack |
| :--- | :--- | :--- | :--- |
| **Epic 1** | **Android Foundation & Tooling** | Project setup, multi-module Gradle configuration, CI build validation | Gradle Kotlin DSL, Jetpack Compose, Hilt / Koin |
| **Epic 2** | **ABB Scraper & Domain Engine** | HTML scraping, magnet construction, domain failover | Jsoup, OkHttp 4, Coroutines |
| **Epic 3** | **BitTorrent Streaming Engine** | libtorrent4j integration, sequential scheduler, HTTP Range server | libtorrent4j, Ktor Embedded Server / NanoHTTPD |
| **Epic 4** | **Audio Engine & Playback Service** | Foreground playback service, lockscreen controls, pitch speed, loudness boost | Media3 ExoPlayer, MediaSessionService, LoudnessEnhancer |
| **Epic 5** | **Oleg Brailean-Inspired Player UI** | Dual progress bars, fine scrubbing slider, gestures, sleep timer with shake-to-reset | Jetpack Compose, Material 3, SensorManager |
| **Epic 6** | **Catalog, Search & Detail Screens** | ABB search UI, category filters, detail screen with torrent inspector | Jetpack Compose, Coil (images), ViewModel |
| **Epic 7** | **Persistence & Offline Library** | Room DB playback state, chapter resume, offline storage management | Room DB, DataStore Preferences |

---

## 📦 Epic 1: Android Foundation & Multi-Module Setup

### Story 1.1: Multi-Module Project Skeleton
**As a** developer,  
**I want** a clean, modular Gradle project structure,  
**So that** business logic, torrent engine, player, and UI modules are decoupled.

#### Tasks:

- [x] **Task 1.1.1: Initialize Gradle Root & Version Catalog**
  - **Path:** `settings.gradle.kts`, `gradle/libs.versions.toml`, `build.gradle.kts`
  - **Details:**
    - Configure Gradle 8.4+, AGP 8.3+, Kotlin 1.9.23+.
    - Set up `libs.versions.toml` with dependencies: Jetpack Compose (BOM 2024.04+), Media3 (1.3.1), Room (2.6.1), OkHttp (4.12.0), Jsoup (1.17.2), libtorrent4j (2.1.0-33 or frostwire libtorrent4j), Coroutines (1.8.0), Coil (2.6.0).
    - Declare modules: `:app`, `:core:common`, `:core:database`, `:core:network`, `:core:scraper`, `:core:torrent`, `:core:player`, `:feature:catalog`, `:feature:detail`, `:feature:player`, `:feature:library`.
  - **Acceptance Criteria:**
    - [ ] `./gradlew tasks` executes without configuration errors.
    - [ ] Version catalog resolves all dependencies.

- [x] **Task 1.1.2: Configure App Module & Tablet Manifest**
  - **Path:** `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
  - **Details:**
    - Set `compileSdk = 34`, `minSdk = 26`, `targetSdk = 34`.
    - Configure NDK ABI filters: `ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }`.
    - Add required permissions: `INTERNET`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`, `WAKE_LOCK`.
    - Configure `hardwareAccelerated = true` and `supports-screens` for tablet compatibility (SM-T505).
  - **Acceptance Criteria:**
    - [ ] `./gradlew assembleDebug` builds `app-debug.apk`.
    - [ ] `adb install app-debug.apk` installs cleanly on the attached Samsung tablet (`SM-T505`).

---

## 🌐 Epic 2: AudioBookBay (ABB) Scraper & Domain Failover

### Story 2.1: ABB HTML Parser
**As an** audiobook listener,  
**I want** to search and view books directly from AudioBookBay without visiting a browser,  
**So that** I can discover books and stream them in-app.

#### Tasks:

- [x] **Task 2.1.1: Implement DomainManager with Failover**
  - **Path:** `core/scraper/src/main/java/com/bookzor/core/scraper/DomainManager.kt`
  - **Details:**
    - Maintain an ordered list of ABB mirrors: `listOf("https://audiobookbay.lu", "https://audiobookbay.is", "https://audiobookbay.nl", "https://audiobookbay.se")`.
    - Implement health check method `getActiveDomain(): String` that pings mirror endpoints with a 4-second timeout and switches to the next mirror if a mirror returns 5xx or fails DNS resolution.
    - Persist user custom domain override in DataStore.
  - **Acceptance Criteria:**
    - [ ] Unit test: when primary domain throws `IOException`, fails over to secondary domain.
    - [ ] Returns valid base URL string.

- [x] **Task 2.1.2: Implement Search Parser**
  - **Path:** `core/scraper/src/main/java/com/bookzor/core/scraper/SearchParser.kt`
  - **Details:**
    - Query URL: `https://<active-domain>/?s=<query>` (and `/?s=<query>&page=2`).
    - Use Jsoup to select all `div.post`:
      - Extract title & detail URL: `div.postTitle h2 a` (Text and `href`).
      - Extract cover image: `div.postContent img[src]` (URL).
      - Extract categories: `div.postInfo` text.
    - Return `List<BookSearchResult>`.
  - **Acceptance Criteria:**
    - [ ] Unit test parsing recorded HTML fixture for query `"dune"`.
    - [ ] Extracts at least title, cover URL, and detail page URL for each result card.

- [x] **Task 2.1.3: Implement Detail Page Parser & InfoHash Extractor**
  - **Path:** `core/scraper/src/main/java/com/bookzor/core/scraper/DetailParser.kt`
  - **Details:**
    - Fetch detail page HTML via OkHttp with desktop Chrome User-Agent.
    - Extract Author: `span.author` text.
    - Extract Narrator: `span.narrator` text.
    - Extract Format: `span.format` (M4B or MP3).
    - Extract Total Size: table cell following `File Size:`.
    - Extract Description: `div.desc` HTML/text.
    - Extract Info Hash: `table tr td:matchesOwn(^Info Hash:$) ~ td` (40-character hex string).
    - Extract Trackers: all `table tr td:matchesOwn(^Tracker:$) ~ td` values.
    - Extract File List: parse rows containing file names and sizes.
  - **Acceptance Criteria:**
    - [ ] Correctly extracts InfoHash `061850ead3eb6f1c5c6d8420211b4bbf2d4ee3e2` from Dune detail page fixture.
    - [ ] Extracts at least 5 tracker URLs.

- [x] **Task 2.1.4: Implement Magnet URI Builder**
  - **Path:** `core/scraper/src/main/java/com/bookzor/core/scraper/MagnetBuilder.kt`
  - **Details:**
    - Function: `buildMagnet(infoHash: String, title: String, trackers: List<String>): String`.
    - Format: `magnet:?xt=urn:btih:<infoHash>&dn=<urlEncodedTitle>&tr=<encodedTracker1>&tr=...`
    - Fallback trackers to append if list has < 3 items:
      - `udp://tracker.opentrackr.org:1337/announce`
      - `udp://open.stealth.si:80/announce`
      - `udp://tracker.torrent.eu.org:451/announce`
  - **Acceptance Criteria:**
    - [ ] Produces standard RFC-compliant magnet URI accepted by libtorrent.

---

## ⚡ Epic 3: BitTorrent Sequential Streaming Engine & HTTP Bridge

### Story 3.1: Embedded libtorrent4j & Piece Prioritization
**As an** audio player,  
**I want** to stream audio pieces sequentially rather than downloading the entire file,  
**So that** playback begins in seconds.

#### Tasks:

- [x] **Task 3.1.1: Integrate libtorrent4j Native Engine**
  - **Path:** `core/torrent/src/main/java/com/bookzor/core/torrent/TorrentEngine.kt`
  - **Details:**
    - Add `com.frostwire:libtorrent4j-android:2.1.0-33` dependency.
    - Initialize `SessionManager` in application context with persistent storage in `context.getExternalFilesDir("torrents")`.
    - Enable DHT, PEX, and configure connection limits (max 50 connections, 200KB/s download rate allocation per stream).
  - **Acceptance Criteria:**
    - [ ] `TorrentEngine.start()` starts session without native library linkage crash on `arm64-v8a`.
    - [ ] Session status logs show DHT nodes connecting.

- [x] **Task 3.1.2: Implement Sequential Piece Priority Scheduler**
  - **Path:** `core/torrent/src/main/java/com/bookzor/core/torrent/PiecePriorityScheduler.kt`
  - **Details:**
    - On magnet add: fetch torrent metadata (`torrent_info`).
    - Parse file list: identify audio files (`.m4b`, `.mp3`).
    - For target audio file, determine `piece_range = (file_offset / piece_size) .. ((file_offset + file_size) / piece_size)`.
    - If format is `.m4b`: immediately mark first 2 pieces and last 2 pieces as `Priority.TOP_PRIORITY` (Priority 7).
    - Implement `setPlaybackOffset(byteOffset: Long)`:
      - Calculates current piece index `P = byteOffset / pieceSize`.
      - Sets pieces `P .. P+4` to `TOP_PRIORITY`.
      - Sets pieces `P+5 .. P+15` to `HIGH_PRIORITY`.
      - Sets pieces outside window to `DO_NOT_DOWNLOAD` (0).
  - **Acceptance Criteria:**
    - [ ] Verified piece priorities dynamically shift as `setPlaybackOffset` changes.
    - [ ] M4B header and trailer pieces are prioritized first on load.

- [x] **Task 3.1.3: Build Local HTTP 1.1 Range Loopback Server**
  - **Path:** `core/torrent/src/main/java/com/bookzor/core/torrent/TorrentStreamServer.kt`
  - **Details:**
    - Embed a lightweight HTTP server (Ktor Netty/CIO or NanoHTTPD) on `127.0.0.1:8080`.
    - Endpoint: `GET /stream?infoHash={hash}&fileIndex={index}`.
    - Inspect request `Range: bytes={start}-{end}` header:
      - Default start = 0, default end = `fileSize - 1`.
      - Status response: `206 Partial Content`.
      - Headers: `Accept-Ranges: bytes`, `Content-Range: bytes {start}-{end}/{fileSize}`, `Content-Type: audio/mp4` or `audio/mpeg`.
    - Input stream implementation: reads bytes from torrent storage. If piece is not yet finished, call `torrentHandle.readPiece()` with coroutine delay/suspension until available (timeout 15s).
  - **Acceptance Criteria:**
    - [ ] `curl -i -r 0-1048575 http://127.0.0.1:8080/stream?...` returns HTTP 206 with exactly 1,048,576 bytes.
    - [ ] Seeking to a byte offset returns the requested byte slice.

---

## 🎵 Epic 4: Media3 Audio Engine & Background Playback Service

### Story 4.1: Background Service & Audio Controls
**As an** audiobook listener,  
**I want** seamless background playback with lockscreen controls, pitch-preserved speed adjustments, and loudness boost,  
**So that** I have uninterrupted listening with high audio clarity.

#### Tasks:

- [x] **Task 4.1.1: Implement PlaybackService (MediaSessionService)**
  - **Path:** `core/player/src/main/java/com/bookzor/core/player/PlaybackService.kt`
  - **Details:**
    - Inherit from `androidx.media3.session.MediaSessionService`.
    - Initialize `ExoPlayer` with `AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).setUsage(C.USAGE_MEDIA).build()`.
    - Set `setHandleAudioBecomingNoisy(true)`.
    - Build `MediaSession` with `MediaSession.Callback` supporting custom commands:
      - `ACTION_SEEK_BACK_15`
      - `ACTION_SEEK_FORWARD_30`
      - `ACTION_CHANGE_SPEED`
    - Configure ongoing foreground notification with large album art, play/pause, seek -15s, and seek +30s buttons.
  - **Acceptance Criteria:**
    - [ ] Audio plays continuously when screen is turned off.
    - [ ] Unplugging headphones automatically pauses playback.
    - [ ] Lock screen displays active book title, cover, and controls.

- [x] **Task 4.1.2: Variable Speed with Sonic Pitch Preservation**
  - **Path:** `core/player/src/main/java/com/bookzor/core/player/SpeedController.kt`
  - **Details:**
    - Expose `setPlaybackSpeed(speed: Float)`.
    - Range: `0.5f` to `3.0f` in `0.05f` increments.
    - ExoPlayer default `PlaybackParameters(speed, 1.0f)` natively utilizes Sonic to keep pitch constant at 1.0.
  - **Acceptance Criteria:**
    - [ ] Narrator pitch remains natural at 1.5x and 2.0x speeds.

- [x] **Task 4.1.3: Loudness Enhancer Integration**
  - **Path:** `core/player/src/main/java/com/bookzor/core/player/LoudnessController.kt`
  - **Details:**
    - Attach `android.media.audiofx.LoudnessEnhancer(exoPlayer.audioSessionId)`.
    - Expose function `setGainBoost(mB: Int)`: range `0 mB` (+0dB) to `1200 mB` (+12dB).
    - Handle device capability check (catch `UnsupportedOperationException`).
  - **Acceptance Criteria:**
    - [ ] Quiet audiobooks are noticeably boosted without digital clipping.

---

## 🎛️ Epic 5: Oleg Brailean-Inspired Player UI & Controls

### Story 5.1: High-Ergonomics Player Screen
**As a** power audiobook listener,  
**I want** an interface modeled after *MP3 Audiobook Player Pro* with dual progress bars, fine scrubbing, gestures, and a shake-to-reset sleep timer,  
**So that** I have maximum control over my audiobooks.

#### Tasks:

- [x] **Task 5.1.1: Build Dual Progress Trackers**
  - **Path:** `feature/player/src/main/java/com/bookzor/feature/player/ui/DualProgressTracker.kt`
  - **Details:**
    - **Overall Book Tracker:** Displays elapsed total time, remaining total time, and overall percentage across all tracks/chapters.
    - **Chapter Tracker:** High-visibility scrubbing slider with current chapter position and chapter duration.
    - Visual buffer bar beneath chapter slider showing cached torrent piece chunks.
  - **Acceptance Criteria:**
    - [ ] Overall progress reflects combined duration of all files in a multi-file torrent.
    - [ ] Piece cache bar highlights downloaded blocks in real time.

- [x] **Task 5.1.2: Implement Fine Scrubbing Gesture Slider**
  - **Path:** `feature/player/src/main/java/com/bookzor/feature/player/ui/FineScrubbingSlider.kt`
  - **Details:**
    - Implement custom Compose pointer input gesture on the scrubber thumb.
    - Track vertical delta $d_y$:
      - $d_y < 40\text{dp}$: 100% normal scrub speed.
      - $40\text{dp} \le d_y < 80\text{dp}$: 50% half-speed scrubbing.
      - $80\text{dp} \le d_y < 120\text{dp}$: 25% quarter-speed scrubbing.
      - $d_y \ge 120\text{dp}$: 10% fine scrubbing.
    - Display floating badge above thumb indicating active scrubbing rate (`"Fine Scrubbing: 10%"`).
  - **Acceptance Criteria:**
    - [ ] Dragging down slows thumb sensitivity by the precise multiplier.
    - [ ] Releasing thumb seeks audio engine to the target position.

- [x] **Task 5.1.3: Ergonomic Control Deck & Jump Buttons**
  - **Path:** `feature/player/src/main/java/com/bookzor/feature/player/ui/PlayerControlDeck.kt`
  - **Details:**
    - Large circular Play/Pause button (min 72dp touch target).
    - Flanked by prominent Jump Back (`-15s`) and Jump Forward (`+30s`) buttons.
    - Quick access pills: `[⚡ 1.25x]`, `[🌙 Sleep]`, `[🔊 Boost]`, `[📑 Chapters]`.
  - **Acceptance Criteria:**
    - [ ] Single tap on Jump Back instantly rewinds 15s.
    - [ ] Tapping speed pill opens speed bottom sheet slider.

- [x] **Task 5.1.4: Sleep Timer with Shake-to-Reset & Fade Out**
  - **Path:** `feature/player/src/main/java/com/bookzor/feature/player/SleepTimerManager.kt`
  - **Details:**
    - Timer options: 15m, 30m, 45m, 60m, End of Chapter.
    - In final 30 seconds: smoothly interpolate ExoPlayer volume from 1.0f to 0.0f.
    - In final 60 seconds: register `SensorEventListener` for `Sensor.TYPE_ACCELEROMETER`.
    - Detect shake: acceleration magnitude $\sqrt{x^2 + y^2 + z^2} > 13.0\text{ m/s}^2$.
    - On shake: trigger device haptic tick (`VibrationEffect.createOneShot`), restore volume to 1.0f, and reset timer.
  - **Acceptance Criteria:**
    - [ ] Audio fades out smoothly at end of timer.
    - [ ] Shaking device within last 60 seconds resets timer back to initial duration.

- [x] **Task 5.1.5: Cover Art Gestures**
  - **Path:** `feature/player/src/main/java/com/bookzor/feature/player/ui/CoverArtGestures.kt`
  - **Details:**
    - Double tap: Toggle Play/Pause.
    - Swipe left: Jump to next chapter/file.
    - Swipe right: Jump to previous chapter/file.
  - **Acceptance Criteria:**
    - [ ] Double-tapping cover art reliably toggles playback state.
    - [ ] Swiping triggers smooth chapter transition.

---

## 🔍 Epic 6: Catalog, Search, Book Detail & Torrent Inspector

### Story 6.1: Discovery & Inspection
**As an** audiobook listener,  
**I want** to browse ABB releases, search by narrator/author, inspect torrent files, and initiate streaming with one tap,  
**So that** I know exactly what I am listening to before starting.

#### Tasks:

- [x] **Task 6.1.1: Catalog & Search Screen**
  - **Path:** `feature/catalog/src/main/java/com/bookzor/feature/catalog/ui/CatalogScreen.kt`
  - **Details:**
    - Search bar with instant suggestions from `recent_searches` table.
    - Grid/List of book cards with cover art, title, author, format tag (`M4B`/`MP3`), file size, and abridged badge.
    - Category filter chips: Sci-Fi, Fantasy, Fiction, History, Non-Fiction.
  - **Acceptance Criteria:**
    - [ ] Searching "Frank Herbert" populates results with covers and titles.
    - [ ] Infinite scroll pagination triggers next page load.

- [x] **Task 6.1.2: Book Detail & Torrent Inspector Screen**
  - **Path:** `feature/detail/src/main/java/com/bookzor/feature/detail/ui/BookDetailScreen.kt`
  - **Details:**
    - High-res cover art header, full book synopsis.
    - Metadata badge row: Author, Narrator, Audio Length, Format, Bitrate, Swarm Health (Seeds/Peers).
    - **Torrent File Inspector Drawer:** Displays all audio files inside the torrent with sizes.
    - Primary CTA buttons: `[ ▶ Stream Now ]` and `[ 💾 Download Offline ]`.
  - **Acceptance Criteria:**
    - [ ] Tapping "Stream Now" navigates to Player screen and triggers `TorrentEngine` streaming pipeline.

---

## 💾 Epic 7: Persistence, Offline Cache & Library Management

### Story 7.1: Playback State & Storage Management
**As an** audiobook listener,  
**I want** my exact playback progress saved automatically, and all streamed audio cached offline,  
**So that** I never lose my place and don't re-download data when re-listening.

#### Tasks:

- [x] **Task 7.1.1: Room Database Implementation**
  - **Path:** `core/database/src/main/java/com/bookzor/core/database/`
  - **Details:**
    - Define Room Database `BookzorDatabase` with entities: `BookEntity`, `AudioFileEntity`, `PlaybackStateEntity`, `BookmarkEntity`, `RecentSearchEntity`.
    - Provide DAOs with Coroutine Flow returns for reactive UI updates.
  - **Acceptance Criteria:**
    - [ ] Room tests pass verifying CRUD operations on `PlaybackStateDao`.

- [x] **Task 7.1.2: Auto-Save State & Smart Rewind on Resume**
  - **Path:** `core/player/src/main/java/com/bookzor/core/player/PlaybackProgressTracker.kt`
  - **Details:**
    - Save position every 5 seconds during active playback and immediately upon pause.
    - Implement Smart Rewind:
      - If paused > 10m: rewind 10 seconds.
      - If paused > 1h: rewind 30 seconds.
      - If paused > 24h: rewind 60 seconds.
  - **Acceptance Criteria:**
    - [ ] Force killing the app and restarting resumes within 5 seconds of last position.
    - [ ] Smart rewind subtracts appropriate seconds when resuming after an extended pause.

- [x] **Task 7.1.3: Offline Storage & Cache Manager UI**
  - **Path:** `feature/library/src/main/java/com/bookzor/feature/library/ui/LibraryScreen.kt`
  - **Details:**
    - "My Books" screen displaying books categorized by: In Progress, Completed, Downloaded.
    - Cache management bottom sheet: shows disk usage per book.
    - "Clear Cache" button to delete torrent piece files without deleting reading history.
  - **Acceptance Criteria:**
    - [ ] Streamed books show accurate cached percentage.
    - [ ] Clearing cache frees disk space while preserving bookmark and resume positions.


---

## 📁 Epic 8: Public Storage Architecture & Reinstall Resilience

### Story 8.1: Migrate Audiobook Storage to Public Android Directory
**As an** audiobook listener,  
**I want** my audiobooks saved to `/storage/emulated/0/Download/Audiobooks/`,  
**So that** my downloaded books survive app updates, reinstalls, and clear-cache actions, and allow sideloading via USB.

#### Tasks:

- [x] **Task 8.1.1: Switch Storage Directory in TorrentEngine & Scanner**
  - **Path:** `core/torrent/src/main/java/com/bookzor/core/torrent/TorrentEngine.kt`, `app/src/main/java/com/bookzor/app/MainActivity.kt`
  - **Details:**
    - Switch `storageDir` from app-private internal sandbox to `File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Audiobooks")`.
    - Auto-create directory with proper file read/write permissions.
- [x] **Task 8.1.2: Add Android 11+ MANAGE_EXTERNAL_STORAGE Support**
  - **Path:** `app/src/main/AndroidManifest.xml`, `MainActivity.kt`
  - **Details:**
    - Declare `android.permission.MANAGE_EXTERNAL_STORAGE`.
    - Implement `checkStoragePermission()` to verify `Environment.isExternalStorageManager()` and launch `Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` intent when ungranted.

---

## 🤖 Epic 9: Local Audiobook Auto-Scanner & Deduplication

### Story 9.1: Auto-Scan and Deduplicate Books
**As an** audiobook listener,  
**I want** the app to automatically detect audiobooks placed on my device, match them to AudioBookBay parent entries, and eliminate duplicate cards,  
**So that** I see a single book entry with rich cover thumbnails and chapter progress.

#### Tasks:

- [x] **Task 9.1.1: Build LocalAudiobookScanner**
  - **Path:** `app/src/main/java/com/bookzor/app/scanner/LocalAudiobookScanner.kt`
  - **Details:**
    - Scan directory recursively for audio files (`.m4b`, `.mp3`, `.m4a`, etc.).
    - Extract ID3/M4B tags (title, artist, duration, embedded cover art) via `MediaMetadataRetriever`.
    - Query `AudioTrackDao.findTrackByFileName` to detect if the file belongs to an existing AudioBookBay book.
    - If matched, set `isInLibrary = true`, register track paths, and retain the original cover art and metadata.
- [x] **Task 9.1.2: Implement Two-Pass Deduplication Engine**
  - **Path:** `app/src/main/java/com/bookzor/app/scanner/LocalAudiobookScanner.kt`
  - **Details:**
    - Detect multiple book entities in Room DB referencing the exact same audio file on disk or having matching normalized titles.
    - Keep the primary book with cover art (`!coverUrl.isNullOrEmpty()`) and delete the bare ghost duplicate.
    - Run deduplication before and after scanning.

---

## 🔄 Epic 10: In-App Auto-Updater & Wireless Deployments

### Story 10.1: Self-Updating APK via GitHub Releases
**As a** tablet user without developer tools or USB cables,  
**I want** the app to check for updates and update itself in-app,  
**So that** I always have the latest bug fixes and features effortlessly.

#### Tasks:

- [x] **Task 10.1.1: Build AppUpdateManager**
  - **Path:** `app/src/main/java/com/bookzor/app/updater/AppUpdateManager.kt`
  - **Details:**
    - Query GitHub API for latest release on `Zloduh/bookzor-releases`.
    - Compare version names semantically (`isVersionNewer`).
    - Stream APK download to cache directory with progress reporting.
- [x] **Task 10.1.2: Android Package Installer Integration**
  - **Path:** `app/src/main/java/com/bookzor/app/updater/AppUpdateManager.kt`, `app/src/main/res/xml/file_paths.xml`
  - **Details:**
    - Expose cached APK via `FileProvider`.
    - Request `canRequestPackageInstalls()` permission on Android 8.0+.
    - Launch `Intent.ACTION_VIEW` package installer intent.
- [x] **Task 10.1.3: Update UI in Settings**
  - **Path:** `feature/player/src/main/java/com/bookzor/feature/player/ui/SettingsScreen.kt`
  - **Details:**
    - Add "Check for Updates" tile showing current version name.
    - Show update modal with changelog, download progress bar (MB and %), and "Install Update" action.

---

## 📋 Epic 11: In-App Diagnostics & Log Collector

### Story 11.1: One-Tap Diagnostic Log Export
**As a** user experiencing an issue,  
**I want** to export diagnostic logs and crash traces as a zip file,  
**So that** I can share it with the developer via any installed app (Email, Drive, Telegram).

#### Tasks:

- [x] **Task 11.1.1: Implement LogCollector**
  - **Path:** `app/src/main/java/com/bookzor/app/diagnostics/LogCollector.kt`
  - **Details:**
    - Capture device specs, memory statistics, storage availability, and the last 500 lines of logcat.
    - Compress into `bookzor_logs_<timestamp>.zip`.
    - Trigger Android native share sheet using `Intent.ACTION_SEND` and `FileProvider`.
- [x] **Task 11.1.2: Add "Export Logs" Tile in Settings**
  - **Path:** `feature/player/src/main/java/com/bookzor/feature/player/ui/SettingsScreen.kt`
  - **Details:**
    - "Export Diagnostic Logs" button that triggers `LogCollector.collectAndShare(context)`.

---

## 🔎 Epic 12: Author & Actor/Narrator Discovery Search

### Story 12.1: Discover Related Books by Author and Narrator
**As an** audiobook listener,  
**I want** to tap an author or narrator's name anywhere in the library or player,  
**So that** I can instantly find all their audiobooks on AudioBookBay.

#### Tasks:

- [x] **Task 12.1.1: Player Screen Discovery Chips & Interactive Metadata**
  - **Path:** `feature/player/src/main/java/com/bookzor/feature/player/ui/PlayerScreen.kt`
  - **Details:**
    - Make author and narrator text in `OlegBookStrip` clickable to search AudioBookBay.
    - Add `BookAuthorNarratorChips` bar below the top strip with `👤 More by [Author]`, `🎙️ Voice: [Narrator]`, and `ℹ️ Details`.
- [x] **Task 12.1.2: Library Screen Discovery & Real-Time Filter**
  - **Path:** `feature/library/src/main/java/com/bookzor/feature/library/ui/LibraryScreen.kt`
  - **Details:**
    - Add clickable author and narrator links and quick chips on each `LibraryBookCard`.
    - Add `ℹ️ Details` modal with one-tap online search buttons for both author and narrator.
    - Add real-time search bar in the Library top bar to filter local books with one-tap fallback to AudioBookBay online search.
