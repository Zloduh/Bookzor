# Bookzor — Technical Architecture Specification (v1.0.2)

---

## 1. High-Level Modular Clean Architecture

Bookzor follows a **Clean Architecture** pattern with feature-based modularization, isolating UI, domain logic, database, scraper, torrent streaming, and player engines.

```
Bookzor/
├── app/                           # Application entry point, AppNavigation graph, Updater, Scanner
├── core/
│   ├── common/                    # Core utilities, Dispatcher providers, ThemeManager
│   ├── database/                  # Room DB (BookzorDatabase), Entities, DAOs, Migrations
│   ├── scraper/                   # ABB HTML parsers (Jsoup), DomainManager, MagnetBuilder
│   ├── torrent/                   # libtorrent4j wrapper, SequentialPieceScheduler, HTTP Range Server
│   └── player/                    # Media3 ExoPlayer, PlaybackService, LoudnessEnhancer
└── feature/
    ├── catalog/                   # ABB search & discovery screens, direct magnet dialog
    ├── detail/                    # Book inspection, selective file download list, torrent metadata
    ├── library/                   # Books library, To-Read shelf, Global Bookmarks manager, search filter
    └── player/                    # Adaptive playback deck, sheets, settings
```

---

## 2. Public Storage & Local Audiobook Scanner

### 2.1. Public Storage Architecture
- **Location:** `/storage/emulated/0/Download/Audiobooks/`
- **Rationale:** Previously, audiobooks stored in app-private sandbox storage (`context.filesDir`) were permanently destroyed whenever the app was uninstalled or cleanly reinstalled. Moving to public Android storage guarantees that downloaded books survive app upgrades, reinstalls, and clear-cache operations.
- **Permissions:** Android 11+ (API 30+) requires `MANAGE_EXTERNAL_STORAGE` (`android.permission.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`) to read and write to the shared `Download/Audiobooks` folder. The app checks this permission on startup (`MainActivity.checkStoragePermission()`) and guides the user to grant access.

### 2.2. Local Audiobook Scanner Pipeline (`LocalAudiobookScanner.kt`)
The scanner runs asynchronously on background dispatchers during app startup and whenever entering the Library screen:

```
Download/Audiobooks/
       │
       ▼
LocalAudiobookScanner.scan()
       │
       ├─► 1. Run Pre-Scan Deduplication (purges existing ghost entries)
       │
       ├─► 2. Traverse files & subdirectories:
       │      │
       │      ├─► For Directories:
       │      │   - Check if folder matches existing ABB Book (by Track file name or normalized title)
       │      │   - If matched: Update `isInLibrary = true`, link local paths, preserve cover art & metadata
       │      │   - If unmatched: Extract ID3/M4B metadata via `MediaMetadataRetriever`, index as manual book
       │      │
       │      └─► For Single Audio Files (.m4b / .mp3):
       │          - Query `audioTrackDao.findTrackByFileName(file.name)`
       │          - If matched: Link file directly to ABB Book entity with thumbnail and full chapter indices
       │          - If unmatched: Extract metadata, create single-file Book entity
       │
       └─► 3. Run Post-Scan Deduplication (guarantees zero duplicate cards)
```

### 2.3. Two-Pass Deduplication Algorithm
To eliminate duplicate book entries (e.g. one created via AudioBookBay scraping and one created by the local disk scanner):
1. **File Path & Name Matching:** Inspects all `AudioTrackEntity` rows. If multiple books point to the exact same audio file on disk, the algorithm selects the primary book (prioritizing entries with `!coverUrl.isNullOrEmpty()` and non-synthetic infoHashes) and deletes the ghost duplicate from Room DB.
2. **Normalized Title Matching:** Cleans book titles by stripping `[MP3]`, `(Unabridged)`, non-alphanumeric punctuation, and extra whitespace. If duplicates share the normalized title, the entry with cover art is preserved and the bare duplicate is deleted.

---

## 3. Torrent-to-HTTP Streaming Pipeline

Android's `Media3 / ExoPlayer` requires a standard URI (`http://...` or `file://...`) supporting standard HTTP byte-range queries. ExoPlayer cannot directly consume raw BitTorrent pieces. Bookzor bridges this with an **embedded local HTTP loopback server**.

### 3.1. Request Flow Diagram

```
ExoPlayer
   │
   │  1. GET /stream?hash=06185...&fileIndex=0
   │     Header: Range: bytes=10485760-20971519
   ▼
Local HTTP Loopback Server (127.0.0.1:8080)
   │
   │  2. Calculate byte range -> Map to Torrent Pieces:
   │     Start Piece = 10485760 / 524288 = Piece 20
   │     End Piece   = 20971519 / 524288 = Piece 39
   ▼
PiecePriorityScheduler (libtorrent4j)
   │
   │  3. Escalate Piece Priorities:
   │     Piece 20..24 -> TOP_PRIORITY (7)
   │     Piece 25..39 -> HIGH_PRIORITY (5)
   │     All other pieces -> IGNORE / LOW
   ▼
TorrentStorageEngine
   │
   │  4. Read piece bytes from disk cache or wait on DHT/peer download
   │     Blocks response socket with 15s timeout
   ▼
Local HTTP Loopback Server
   │
   │  5. Return HTTP/1.1 206 Partial Content
   │     Content-Range: bytes 10485760-20971519/1204910240
   │     Data stream: [byte chunk...]
   ▼
ExoPlayer Audio Decoders & Output
```

### 3.2. Handling M4B Audio with Metadata at EOF (`moov` Atom)
- Many `.m4b` audiobooks place the MP4 index (`moov` atom) at the **tail** of the file rather than the head.
- When ExoPlayer opens an `.m4b` stream, it first reads byte `0..1024` (detecting `ftyp` box), and then immediately requests the **last 1MB** of the file to parse chapters, codecs, and durations.
- **Architectural Solution:**
  1. As soon as torrent metadata arrives, if the target file is `.m4b`, the `SequentialPieceScheduler` automatically requests the **first 2 pieces** AND the **last 2 pieces** of that file before signaling the HTTP server that streaming is ready.
  2. This guarantees that ExoPlayer's header + trailer requests resolve immediately without hanging.

---

## 4. In-App Updater Architecture (`AppUpdateManager.kt`)

To enable seamless updates for end users without needing Android Debug Bridge (ADB) or USB cables:

```
AppUpdateManager
       │
       ▼
1. Query GitHub Releases API:
   GET https://api.github.com/repos/Zloduh/Bookzor/releases/latest
       │
       ▼
2. Compare Version:
   Parse tag_name ("v1.0.2") vs Context PackageManager VersionName ("1.0.1")
       │
       ├─► If Newer: Transition StateFlow to UpdateStatus.Available(updateInfo)
       └─► If Same/Older: Transition StateFlow to UpdateStatus.UpToDate
       │
       ▼ (User taps "Update Now" in Settings)
3. Stream APK Download:
   Download binary via OkHttp to cacheDir/updates/Bookzor-<version>.apk
   Stream chunks while emitting UpdateStatus.Downloading(progress, downloadedMb, totalMb)
       │
       ▼
4. Native Package Installation:
   Get content URI via FileProvider (com.bookzor.app.fileprovider)
   Check Settings.canRequestPackageInstalls()
   Launch Intent(Intent.ACTION_VIEW) with setDataAndType(uri, "application/vnd.android.package-archive")
```

---

## 5. In-App Diagnostic Log Collector (`LogCollector.kt`)

For zero-friction bug reporting:
1. **System & App Telemetry:** Collects device model, manufacturer, Android SDK version, total RAM, available RAM, and internal/external disk space.
2. **Logcat Capture:** Executes `logcat -d -t 500` to capture the last 500 lines of system and application logs (filtering tags `BookzorTorrent`, `BookzorPlayer`, `BookzorScanner`, etc.).
3. **Zip Compression:** Packages `diagnostics.txt` and `logcat.txt` into a compressed `.zip` file stored in the app's cache directory (`bookzor_logs_<timestamp>.zip`).
4. **Native Share Sheet:** Generates a secure `content://` URI via `FileProvider` and fires `Intent.ACTION_SEND`, allowing users to share the zipped log file directly via Gmail, Drive, Telegram, QuickShare, or Bluetooth.

---

## 6. Author & Actor/Narrator Discovery Engine

To allow rapid discovery of related audiobooks directly from the playback or library interface:

1. **Player Screen & Top Strip:**
   - In `BookDetailStrip`, Author and Narrator text are interactive: tapping either invokes `onSearchQuery(name)`.
   - A dedicated **Discovery Chips Bar** appears right below the top strip with `👤 More by [Author]`, `🎙️ Voice: [Narrator]`, and `ℹ️ Details`.
2. **Library Cards & Details Modal:**
   - Every `LibraryBookCard` includes clickable author/narrator links and discovery chips.
   - A new `ℹ️` action button opens a details modal with one-tap search triggers for both author and narrator.
3. **Real-Time Library Search & Web Fallback:**
   - The Library top bar features a search filter that matches local books in real-time by title, author, or narrator.
   - If a search query is active, an integrated banner offers one-tap search on AudioBookBay: `"Search AudioBookBay for '<query>'"` to find new titles online.

---

## 7. Database Schema (Room)

```sql
-- Books discovered, saved, or played
CREATE TABLE books (
    info_hash TEXT PRIMARY KEY NOT NULL,
    title TEXT NOT NULL,
    author TEXT NOT NULL,
    narrator TEXT,
    cover_url TEXT,
    description TEXT,
    format TEXT NOT NULL,          -- 'M4B' or 'MP3'
    is_abridged INTEGER NOT NULL,  -- 0 = false, 1 = true
    total_size_bytes INTEGER NOT NULL,
    total_duration_ms INTEGER NOT NULL,
    source_url TEXT NOT NULL,
    is_in_library INTEGER NOT NULL DEFAULT 0,
    is_download_requested INTEGER NOT NULL DEFAULT 0,
    is_to_read INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    last_accessed_at INTEGER NOT NULL
);

-- Individual audio tracks
CREATE TABLE audio_tracks (
    id TEXT PRIMARY KEY NOT NULL,  -- info_hash + "_" + track_index
    info_hash TEXT NOT NULL,
    track_index INTEGER NOT NULL,
    file_name TEXT NOT NULL,
    file_size_bytes INTEGER NOT NULL,
    duration_ms INTEGER NOT NULL,
    FOREIGN KEY(info_hash) REFERENCES books(info_hash) ON DELETE CASCADE
);

-- Continuous playback state
CREATE TABLE playback_state (
    info_hash TEXT PRIMARY KEY NOT NULL,
    current_track_index INTEGER NOT NULL,
    position_ms INTEGER NOT NULL,
    total_elapsed_ms INTEGER NOT NULL,
    playback_speed REAL NOT NULL DEFAULT 1.0,
    volume_boost_mb INTEGER NOT NULL DEFAULT 0,
    is_completed INTEGER NOT NULL DEFAULT 0,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY(info_hash) REFERENCES books(info_hash) ON DELETE CASCADE
);

-- User bookmarks with notes
CREATE TABLE bookmarks (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    info_hash TEXT NOT NULL,
    track_index INTEGER NOT NULL,
    position_ms INTEGER NOT NULL,
    total_elapsed_ms INTEGER NOT NULL,
    note TEXT,
    created_at INTEGER NOT NULL,
    FOREIGN KEY(info_hash) REFERENCES books(info_hash) ON DELETE CASCADE
);

-- Recent user search queries
CREATE TABLE recent_searches (
    query TEXT PRIMARY KEY NOT NULL,
    searched_at INTEGER NOT NULL
);
```

---

## 8. Android 12–14 Lifecycle & Foreground Service Hardening

### 8.1. Eliminating `ForegroundServiceDidNotStartInTimeException`
- Android 12+ strictly enforces that if `startForegroundService()` is called, the service must execute `startForeground()` within a strict 5-second window.
- **Architectural Solution:**
  1. Removed premature `ensureServiceStarted()` calls from `MainActivity.onCreate()`.
  2. In `PlaybackService.onStartCommand()`, `startForeground()` is called immediately on the main thread with a default `MediaStyle` notification before any asynchronous audio loading begins.
  3. Proper `foregroundServiceType="mediaPlayback"` is declared in `AndroidManifest.xml`.

### 8.2. Swipe-Away Lifecycle Behavior
- **While Playing:** If the user swipes the app away from "Close all" / recent apps while audio is playing, the `PlaybackService` stays alive as a high-priority foreground service with lockscreen controls. Audio is never interrupted.
- **While Paused:** If audio is paused and the user dismisses the notification or swipes away the app, `PlaybackService.onTaskRemoved()` stops the foreground state, releases ExoPlayer resources, and terminates cleanly to free device memory.
