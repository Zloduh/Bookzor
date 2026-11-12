# Bookzor — Product & Technical Requirements Specification (v1.0.2)

---

## 1. Executive Summary

**Bookzor** is a native Android audiobook player designed to stream audiobooks on-demand directly from magnet links discovered via **AudioBookBay (ABB)** as well as offline audiobooks saved to device storage. It pairs sequential BitTorrent streaming technology with ergonomic audiobook playback controls.

---

## 2. Target Platforms & Specifications

- **Target OS:** Android 12 (API 31) through Android 14 (API 34).
- **Minimum SDK:** Android 8.0 (API Level 26).
- **Target Architectures:** `arm64-v8a`, `armeabi-v7a`, `x86_64`.
- **Form Factors:** Android Phones and Tablets (adaptive Compose layouts).
- **Current App Version:** `1.0.3` (`versionCode = 4`).

---

## 3. Functional Requirements (FR)

### 3.1. AudioBookBay (ABB) Scraper & Discovery (`core:scraper`)

- **FR-SC-01 (Search):** Search ABB catalog by keyword (Title, Author, Narrator). Support sorting and pagination.
- **FR-SC-02 (Catalog Browsing):** Browse recent releases, popular books, and categories.
- **FR-SC-03 (Metadata Extraction):**
  - Title, Author, Narrator, Cover Art URL.
  - Audio Format (M4B, MP3) and Abridged status.
  - File Size (GB/MB) and Total Duration.
  - Info Hash (40-character hex string) and Tracker URLs.
  - Multi-file track list.
- **FR-SC-04 (Magnet URI Construction):** Dynamically construct standard BitTorrent magnet URIs with fallback public trackers.
- **FR-SC-05 (Dynamic Domain & Failover):** Maintain an active mirror list (`.lu`, `.is`, `.nl`, `.se`) with automatic failover upon HTTP 5xx or connection timeout.
- **FR-SC-06 (Anti-Scraping Mitigation):** Use Chrome Android User-Agent and persistent cookie management. Support direct magnet and URL input.

---

### 3.2. BitTorrent Sequential Streaming Engine (`core:torrent`)

- **FR-TO-01 (Embedded Engine):** Use `libtorrent4j` (C++ libtorrent via JNI) compiled for `arm64-v8a`.
- **FR-TO-02 (DHT & Peer Exchange):** Enable Mainline DHT, PEX, and LSD for rapid trackerless peer discovery.
- **FR-TO-03 (Sequential Piece Priority):** Enforce sequential downloading prioritizing playhead pieces and a 15-piece read-ahead buffer.
- **FR-TO-04 (M4B / MP4 `moov` Atom Handling):** Immediately request the first and last pieces of `.m4b` containers to extract metadata indexes prior to sequential streaming.
- **FR-TO-05 (Multi-File MP3 Prioritization):** Prioritize active file `N`, pre-buffer file `N+1` at 85% completion, and suppress download of all other files until needed.
- **FR-TO-06 (Local HTTP 1.1 Range Loopback Server):** Run an internal HTTP server (`127.0.0.1:8080/stream`) serving `206 Partial Content` with accurate `Content-Range` headers to ExoPlayer.

---

### 3.3. Public Storage & Local Scanner Engine (`app:scanner`)

- **FR-ST-01 (Public Audiobook Directory):** Store all downloaded audiobooks in `/storage/emulated/0/Download/Audiobooks/` rather than app-private sandbox storage.
- **FR-ST-02 (Sideloading & Reinstall Persistence):** Audiobooks placed manually into `Download/Audiobooks/` or downloaded via ABB must survive app uninstalls and re-installations.
- **FR-ST-03 (Automatic Local Scanner):** Automatically scan `Download/Audiobooks/` on app launch and Library screen entry. Extract metadata (title, author, duration, embedded cover art) via `MediaMetadataRetriever`.
- **FR-ST-04 (ABB Metadata Linking):** If a scanned audio file or directory matches an existing AudioBookBay book or track, link directly to the existing ABB book entity and retain its thumbnail and chapter metadata.
- **FR-ST-05 (Automatic Deduplication):** Detect and purge duplicate database entries referencing identical audio files or titles, preserving the rich entry with cover art.

---

### 3.4. Author & Actor/Narrator Discovery Engine

- **FR-DI-01 (Player Screen Discovery Chips):** On the Player Screen and top metadata strip, display interactive Author and Narrator text plus dedicated discovery chips:
  - `👤 More by [Author]`
  - `🎙️ Voice: [Narrator]`
  - `ℹ️ Details`
- **FR-DI-02 (Library Screen Discovery Chips):** Each book card in the Library displays clickable Author and Narrator lines and quick chips to search AudioBookBay.
- **FR-DI-03 (Book Details & Discovery Dialog):** An `ℹ️` button on library cards opens a dialog showing full metadata and one-tap buttons to search other books by that author or narrator.
- **FR-DI-04 (Real-Time Library Search & Filter):** A search icon in the Library top bar enables real-time filtering of local books by title, author, or narrator, with a one-tap banner to execute the query online on AudioBookBay.

---

### 3.5. In-App Auto-Updater (`app:updater`)

- **FR-UP-01 (GitHub Releases Check):** Query `https://api.github.com/repos/Zloduh/bookzor-releases/releases/latest` on demand in Settings.
- **FR-UP-02 (Semantic Version Comparison):** Dynamically compare the latest release tag (e.g. `v1.0.2`) against the installed package version name.
- **FR-UP-03 (In-App APK Download):** Stream the APK download to app cache with a real-time progress bar, downloaded MB, and total MB display.
- **FR-UP-04 (Native Package Installation):** Launch the Android system package installer via `FileProvider` (`content://`) with `ACTION_VIEW` and `REQUEST_INSTALL_PACKAGES` permission checking.

---

### 3.6. In-App Diagnostic Log Collector (`app:diagnostics`)

- **FR-DG-01 (Diagnostic Packaging):** Package device specs, Android version, memory metrics, storage stats, and the last 500 lines of logcat into a `.zip` file (`bookzor_logs_<timestamp>.zip`).
- **FR-DG-02 (Native Share Sheet):** Expose the `.zip` archive via `FileProvider` and open the Android native share sheet to allow sending logs via Drive, Gmail, Telegram, QuickShare, etc.

---

### 3.7. Audio Engine & Background Lifecycle (`core:player`)

- **FR-PL-01 (Media3 Architecture):** Built on `androidx.media3:media3-exoplayer` and `androidx.media3:media3-session`.
- **FR-PL-02 (Foreground Service Hardening):** Run `PlaybackService` as an Android Foreground Service. Immediately call `startForeground()` with a `MediaStyle` notification in `onStartCommand()` to eliminate `ForegroundServiceDidNotStartInTimeException` crashes.
- **FR-PL-03 (Swipe-Away Lifecycle):**
  - While audio is playing, swiping the app from recent apps leaves the playback service running without audio interruption.
  - While paused, dismissing the notification or swiping away the app stops the foreground service and releases resources.
- **FR-PL-04 (Variable Speed Playback):** 0.5x to 3.0x speed with pitch preservation.
- **FR-PL-05 (Loudness Enhancer):** Boost audio volume up to +12dB (+1200 mB) via hardware `LoudnessEnhancer`.
- **FR-PL-06 (Sleep Timer & Shake-to-Reset):** 15m/30m/45m/60m presets with 30s volume fade-out and shake gesture reset.

---

### 3.8. Player UI & Controls (`feature:player`)

- **FR-UI-01 (Dual Progress Trackers):** Total book tracker (duration, elapsed, %) and chapter/track tracker.
- **FR-UI-02 (Fine Scrubbing):** Vertical drag thresholds (100%, 50%, 25%, 10% scrub rate).
- **FR-UI-03 (Ergonomic Jump Buttons):** Large Jump Back (-15s) and Jump Forward (+30s) buttons with configurable intervals.
- **FR-UI-04 (Torrent Buffer HUD):** Live visual display of cached pieces vs downloading pieces.
- **FR-UI-05 (Bookmarks with Notes):** Add timestamped bookmarks with optional text notes; jump or delete with a tap.
- **FR-UI-06 (Smart Rewind on Resume):** Auto-rewind 10s to 60s depending on pause duration.

---

## 4. Non-Functional Requirements (NFR)

- **NFR-01 (Cold Start Latency):** Sub-15s cold start to audio output on torrents with >= 5 seeders.
- **NFR-02 (Memory Footprint):** RAM usage below 180MB during continuous streaming.
- **NFR-03 (Storage Permanence):** Audiobooks must not be deleted when clearing application data or reinstalling the app.
- **NFR-04 (Zero Wire Updates):** App updates and diagnostic logs must not require a USB connection or ADB.
