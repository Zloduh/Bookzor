# Bookzor — Technical Architecture Specification

---

## 1. Modular Clean Architecture

Bookzor follows a **Clean Architecture** pattern with feature-based modularization, separating UI, Domain logic, and Platform/Core infrastructures.

```
bookzor/
├── app/                           # Application entry point, DI graphs, navigation host
├── core/
│   ├── common/                    # Core utilities, Dispatcher providers, Result wrapper
│   ├── database/                  # Room DB, Entity definitions, DAOs, Migrations
│   ├── network/                   # OkHttp singleton, User-Agent interceptor, CookieJar
│   ├── scraper/                   # ABB HTML parsers, DomainManager, MagnetBuilder
│   ├── torrent/                   # libtorrent4j wrapper, PieceScheduler, HTTP Range Server
│   └── player/                    # Media3 ExoPlayer, PlaybackService, LoudnessEnhancer
└── feature/
    ├── catalog/                   # Search & browse screens (Jetpack Compose)
    ├── detail/                    # Book details, synopsis, torrent files inspector
    ├── player/                    # Oleg Brailean-inspired playback deck & gestures
    └── library/                   # Offline cache manager, history, bookmarks
```

---

## 2. Torrent-to-HTTP Streaming Pipeline

Android's `Media3 / ExoPlayer` requires a standard URI (`http://...` or `file://...`) that supports standard byte-range queries. ExoPlayer cannot directly consume raw BitTorrent pieces. Bookzor bridges this with an **embedded local HTTP loopback server**.

### 2.1. Request Flow Diagram

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

### 2.2. Handling M4B Audio with Metadata at EOF (`moov` Atom)

- Many `.m4b` audiobooks place the MP4 index (`moov` atom) at the **tail** of the file rather than the head.
- When ExoPlayer opens an `.m4b` stream, it first reads byte `0..1024` (detecting `ftyp` box), and then immediately requests the **last 1MB** of the file to parse chapters, codecs, and durations.
- **Architectural Solution:**
  1. As soon as the torrent metadata arrives, if the target file is `.m4b`, the `PiecePriorityScheduler` automatically requests the **first 2 pieces** AND the **last 2 pieces** of that file before signaling the HTTP server that streaming is ready.
  2. This guarantees that ExoPlayer's header + trailer requests resolve immediately without hanging.

---

## 3. Database Schema (Room)

```sql
-- Books discovered or played
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
    created_at INTEGER NOT NULL,
    last_accessed_at INTEGER NOT NULL
);

-- Individual audio files in multi-track torrents
CREATE TABLE audio_files (
    id TEXT PRIMARY KEY NOT NULL,  -- info_hash + "_" + file_index
    info_hash TEXT NOT NULL,
    file_index INTEGER NOT NULL,
    file_name TEXT NOT NULL,
    file_size_bytes INTEGER NOT NULL,
    duration_ms INTEGER NOT NULL,
    sort_order INTEGER NOT NULL,
    FOREIGN KEY(info_hash) REFERENCES books(info_hash) ON DELETE CASCADE
);

-- Continuous playback state
CREATE TABLE playback_state (
    info_hash TEXT PRIMARY KEY NOT NULL,
    current_file_index INTEGER NOT NULL,
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
    file_index INTEGER NOT NULL,
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

## 4. Audio Engine & MediaSession Architecture

### 4.1. Media3 `PlaybackService`
- Implements `androidx.media3.session.MediaSessionService`.
- Owns a single `ExoPlayer` instance configured with:
  - Custom `DefaultAudioSink` with `SonicAudioProcessor` for variable pitch-preserved speed (0.5x – 3.0x).
  - `AudioAttributes` with `CONTENT_TYPE_SPEECH` and `USAGE_MEDIA`.
  - Automatic audio focus management (`setHandleAudioBecomingNoisy(true)`).
- Publishes a `MediaSession` allowing seamless control via Android Auto, Lockscreen, Wear OS, and Bluetooth AVRCP.

### 4.2. LoudnessEnhancer Controller
- Instantiates `android.media.audiofx.LoudnessEnhancer(exoPlayer.audioSessionId)`.
- Allows boosting target gain from `0 mB` to `+1200 mB` (+12dB) to normalize low-volume audiobooks.

### 4.3. Sleep Timer & Shake Detection
- `SleepTimerManager` runs a countdown coroutine on the Main dispatcher.
- In the final 30 seconds: initiates a linear volume fade down from `1.0` to `0.0`.
- In the final 60 seconds: enables the Android `SensorManager.SENSOR_DELAY_UI` accelerometer listener.
  - If a shake gesture (acceleration delta > `12.0 m/s²`) is detected: cancels the fade, resets volume to `1.0`, triggers a short haptic feedback tick, and resets the timer to its initial duration.

---

## 5. UI Architecture: Oleg Brailean-Inspired Control Deck

The player screen (`feature:player`) is built entirely with **Jetpack Compose** following Material 3 guidelines and OLED dark aesthetics:

```
┌─────────────────────────────────────────────────────────────┐
│  [Back Arrow]              Bookzor             [Bookmark +] │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│                 ┌─────────────────────────┐                 │
│                 │                         │                 │
│                 │       COVER ART         │                 │
│                 │   (Double-Tap: Pause)   │                 │
│                 │  (Swipe L/R: Skip Ch.)  │                 │
│                 │                         │                 │
│                 └─────────────────────────┘                 │
│                                                             │
│  The Way of Kings                                           │
│  Brandon Sanderson • Read by Michael Kramer & Kate Reading  │
│                                                             │
│  ── OVERALL PROGRESS ─────────────────────────────────────  │
│  [━━━━━━━━━━━━━━━━━━━━━░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░]  │
│  12h 45m                                    45h 32m (28%)   │
│                                                             │
│  ── CHAPTER PROGRESS & TORRENT BUFFER ───────────────────  │
│  [━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━░░░░░░░░░░░░░░░░░░░░░░░░░]  │
│  14m 20s (Chapter 14)                             48m 10s   │
│  [▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░░░░░░░░░░░░░░░░░░░░░░░]   <-- Piece Cache Bar
│                                                             │
│  ── SPEED & SLEEP CONTROLS ───────────────────────────────  │
│  [ ⚡ 1.25x ]     [ 🌙 30m ]     [ 🔊 +6dB ]    [ 📑 Ch. 14 ] │
│                                                             │
│  ── PRIMARY CONTROL DECK ─────────────────────────────────  │
│          ⏮                 ▶ / ⏸                 ⏭         │
│        -15s                                      +30s       │
└─────────────────────────────────────────────────────────────┘
```

### 5.1. Fine Scrubbing Behavior
- When the user holds the chapter slider thumb, dragging left/right scrubs at standard speed.
- If the touch point Y-coordinate drops by `> 40dp` below the slider line:
  - The UI displays a floating indicator: `"Half Speed Scrubbing"`.
  - Further horizontal travel is scaled down by 0.5.
- If the touch point drops by `> 80dp`:
  - The UI displays: `"Quarter Speed Scrubbing"`.
  - Travel is scaled down by 0.25.
- If the touch point drops by `> 120dp`:
  - The UI displays: `"Fine Scrubbing (10%)"`.
  - Travel is scaled down by 0.10.
