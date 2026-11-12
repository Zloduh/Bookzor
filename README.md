# Bookzor 🎧 (v1.0.3)

An advanced Android audiobook streaming player designed to stream audiobooks directly from **AudioBookBay (ABB)** magnet links and local storage, pairing an embedded sequential BitTorrent engine with the ergonomics of *MP3 Audiobook Player Pro* (Oleg Brailean).

---

## 🛠️ What's New in v1.0.3

- **🔖 Bookmark & Resume Fix:** After closing and reopening the app, the scrubber no longer jumps to the end. Chapter/book durations are now persisted and restored, and playback position is saved periodically and on app close.
- **🎯 Accurate Bookmark Jumps:** Bookmarks open the correct chapter at the exact saved time (no smart-rewind offset), from the Player, Library and Bookmarks screens.
- **✨ Bookmark Polish:** One-tap "Bookmark at X" and quick-add button on the player deck, bookmark count, timeline tick markers, tap-to-seek on the scrubber, and editable bookmark notes with chapter labels.

## 🚀 What's New in v1.0.2 & v1.0.1

- **🔎 Search by Author & Voice Actor (Narrator) in Library & Player:**
  - One-tap discovery chips on both the **Player Screen** and **Library Book Cards**:
    - `👤 More by [Author]`
    - `🎙️ Voice: [Narrator]`
    - `ℹ️ Details` (opens the full book page with synopsis, chapters, and file inspector).
  - Tapping author or narrator anywhere in the app instantly searches AudioBookBay without typing.
  - **Real-Time Library Search Bar:** Type to filter local books in real-time by title, author, or narrator, with a one-tap banner to search AudioBookBay for additional titles.
- **📁 Public Storage & Survives App Reinstalls:**
  - Downloaded audiobooks are saved directly to `/storage/emulated/0/Download/Audiobooks/`.
  - Books and downloads survive app uninstalls, re-installations, and updates.
  - Sideloading support: drop any `.m4b` or `.mp3` files into `Download/Audiobooks` and the app auto-indexes them.
- **🤖 Automatic Local Audiobook Scanner & Deduplication Engine:**
  - Auto-scans `Download/Audiobooks/` on startup and on navigating to the Library screen.
  - Links downloaded single-file/folder torrents back to their AudioBookBay parent entries, preserving cover art thumbnails, chapters, and metadata.
  - Automatically deduplicates ghost database entries referencing identical audio files or titles.
- **🔄 In-App Auto-Updater:**
  - Checks public GitHub releases ([`Zloduh/bookzor-releases`](https://github.com/Zloduh/bookzor-releases)) automatically.
  - In-app download with a live percentage and MB progress indicator.
  - Prompts the native Android package installer via `FileProvider` without needing a computer, ADB, or USB cables.
- **📋 In-App Log Collector & One-Tap Share:**
  - Packages device specs, memory status, storage metrics, and the last 500 lines of logcat into a compressed `.zip` archive.
  - Opens Android's native share sheet (Gmail, Google Drive, Telegram, QuickShare, etc.) for zero-friction debugging.
- **🛡️ Android 12–14 Foreground Service Watchdog Hardening:**
  - Resolved `ForegroundServiceDidNotStartInTimeException` ANRs.
  - Immediate `startForeground()` with `MediaStyle` notification on `PlaybackService` initialization.

---

## 🎯 Core Features

### 1. AudioBookBay (ABB) Direct Streaming & Discovery
- **Live In-App Search:** Search titles, authors, and narrators with normalized queries.
- **Mirror Failover Engine:** Dynamically switches between active ABB mirrors (`.lu`, `.is`, `.nl`, `.se`, etc.).
- **Zero Captchas / Ad-Free:** Directly parses book metadata, info hashes, and trackers into standard magnet links.
- **Selective File Downloads:** Choose specific chapters/parts in multi-file torrents to conserve device storage.

### 2. Sequential BitTorrent Engine
- **Embedded C++ libtorrent4j:** Targets `arm64-v8a` with hardware-accelerated crypto.
- **Sequential Piece Scheduler:** Prioritizes playback window pieces, read-ahead buffer, and `moov` MP4 index atoms located at the EOF of `.m4b` files.
- **Local HTTP 1.1 Range Loopback Server:** Streams directly into `ExoPlayer` via `127.0.0.1:8080/stream` supporting standard HTTP `206 Partial Content` requests.

### 3. Oleg Brailean Ergonomic Control Deck
- **Fine-Scrubbing Progress Deck:** Holds playhead position with vertical drag sensitivity (100%, 50%, 25%, 10% scrub rate).
- **Dual Jump Buttons:** Independent forward (+30s) and rewind (-15s) intervals, customizable in settings.
- **Speed & Volume Boost:** Variable playback speed (0.5x – 3.0x with pitch correction) and hardware Loudness Enhancer (+0dB to +12dB).
- **Sleep Timer & Shake-to-Reset:** Smooth volume fade-out over final 30 seconds; shake gesture resets timer.
- **Bookmarks & Annotations:** Save timestamps with custom notes; jump or delete with a tap.

### 4. Continuous Lifecycle & Background Playback
- **Persistent MediaSessionService:** Runs as a dedicated foreground service with lockscreen and notification media controls.
- **Smart App Swipe-Away Handling:** Audio continues playing when the app is swiped away from recent apps; automatically terminates when paused.
- **State Persistence:** Room database (`BookzorDatabase`) records millisecond playback positions and auto-resumes cold starts exactly where you left off.

---

## 🏗️ Multi-Module Architecture

```
Bookzor/
├── app/                  # Application class, Navigation graph, MainActivity, Updater, Scanner
├── core/
│   ├── common/           # Dispatchers, ThemeManager, Result wrapper
│   ├── database/         # Room DB, Entities (Books, Tracks, Bookmarks, PlaybackState), DAOs
│   ├── player/           # ExoPlayer, PlaybackService, MediaSession, LoudnessEnhancer
│   ├── scraper/          # ABB HTML parser (Jsoup), DomainManager, MagnetBuilder
│   └── torrent/          # libtorrent4j session, SequentialPieceScheduler, TorrentStreamServer
└── feature/
    ├── catalog/          # ABB Discovery, live search, direct magnet dialog
    ├── detail/           # Book inspection, torrent file selector, author/narrator chips
    ├── library/          # Books screen, To-Read shelf, Global Bookmarks, search filter
    └── player/           # Portrait & tablet landscape Oleg Brailean decks, bottom sheets
```

---

## 📱 Hardware & Environment

- **Target Device:** Samsung Galaxy Tab A7 (`SM-T505`, Android 12, `arm64-v8a`).
- **SDK Targets:** Min SDK 26, Target SDK 34, Compile SDK 34.
- **JDK:** OpenJDK 17 (`/usr/lib/jvm/java-17-openjdk`).
- **Storage Path:** `/storage/emulated/0/Download/Audiobooks/`.
- **Public Releases Repo:** [`https://github.com/Zloduh/bookzor-releases`](https://github.com/Zloduh/bookzor-releases).
- **Source Code Repo:** [`https://github.com/Zloduh/Bookzor`](https://github.com/Zloduh/Bookzor).

---

## 🛠️ Build & Installation

### Option 1: In-App Update (Recommended)
1. Open Bookzor on the device.
2. Tap `☰` -> **Settings** -> **Check for Updates**.
3. Tap **Download & Install**.

### Option 2: Build from Source
```bash
# Build debug APK
GRADLE_USER_HOME=/run/media/tpredrag/Data/Projekti/Bookzor/.gradle ./gradlew assembleDebug

# Deploy via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch MainActivity
adb shell am start -n com.bookzor.app/.MainActivity
```

---

## 📚 Technical Documentation

- **[`REQUIREMENTS.md`](./REQUIREMENTS.md):** Full functional and non-functional requirements specification.
- **[`ARCHITECTURE.md`](./ARCHITECTURE.md):** Deep-dive on streaming pipeline, storage, updater, and database schema.
- **[`ROADMAP_AND_EPICS.md`](./ROADMAP_AND_EPICS.md):** Completed epics, feature stories, and verification checklists.
- **[`ENVIRONMENT_SETUP.md`](./ENVIRONMENT_SETUP.md):** Developer setup instructions for Arch/CachyOS, Ubuntu, and macOS.
- **[`UI_DESIGN_SPEC.md`](./UI_DESIGN_SPEC.md):** Oleg Brailean design tokens, layout dimensions, and Compose guidelines.
