# Bookzor 🎧

An Android audiobook player that streams audiobooks directly from **AudioBookBay (ABB)** magnet links, pairing an embedded sequential BitTorrent streaming engine with the UX and control ergonomics modeled after *MP3 Audiobook Player Pro* by Oleg Brailean.

---

## 🎯 Core Features

- **ABB Direct Streaming & Search:**
  - In-app search for titles, authors, and narrators (`&tt=1` normalization).
  - Mirror failover (`audiobookbay.lu`, `audiobookbay.is`, etc.) with direct magnet link parsing.
  - Zero browser navigation, captcha bypass, and advertisement suppression.
- **Sequential BitTorrent Streaming:**
  - Embedded `libtorrent4j` (C++ / JNI) engine targeting `arm64-v8a`.
  - Sequential piece prioritization and embedded local HTTP 1.1 Range loopback server (`TorrentStreamServer`).
  - Native `Media3 / ExoPlayer` integration enabling instantaneous scrubbing and sub-15s cold starts.
- **Oleg Brailean Ergonomic Control Deck:**
  - Center cover canvas with double-tap Play/Pause toggle.
  - Fine-scrubbing progress bar with dual jump buttons (independent forward and rewind intervals).
  - Chapter and track side drawer with live progress indicators.
  - In-player **Bookmarks Drawer**: jump to timestamps, view/delete bookmarks, and add bookmarks with custom notes.
  - Playback speed control (0.5x – 3.0x) and hardware-accelerated Loudness Enhancer (+0dB to +12dB).
  - Sleep timer with countdown and shake-to-reset.
- **State Persistence & Rehydration:**
  - Persistent SQLite storage via Room (`BookEntity`, `AudioTrackEntity`, `PlaybackStateEntity`, `BookmarkEntity`).
  - Cold starts immediately restore the exact last-played book, chapter, and position without resetting to track zero.
- **Spotify / Audible Lifecycle Management:**
  - Runs as an Android Foreground Service (`PlaybackService` with `MediaStyle` notification).
  - Swiping the app away from "Close all" while listening **maintains uninterrupted audio playback**.
  - When paused, swiping the app away or dismissing the notification terminates the service and frees system memory.
- **Selective Offline Downloads & Storage Meter:**
  - Selective chapter/file download checkboxes on compilation torrents to prevent filling tablet storage.
  - Reactive storage meter and cache cleaner in Settings that terminates handles and deletes orphaned audio files.
- **Themes & UI Customization:**
  - Three OLED and reader themes: **OLED Dark**, **Warm Sepia**, and **Midnight Blue**.
  - Persistent bottom mini-player across all auxiliary screens.

---

## 🏗️ Architecture & Modules

```
├── app/                  # Main entry point, navigation graph, application setup
├── core/
│   ├── common/           # Dispatchers, resource wrappers, theme management
│   ├── database/         # Room DB, entities (Books, Tracks, Bookmarks, PlaybackState), DAOs
│   ├── player/           # ExoPlayer controller, PlaybackService (MediaSessionService), audio effects
│   ├── scraper/          # ABB HTML parser (Jsoup), domain failover, magnet URI generator
│   └── torrent/          # libtorrent4j session, piece prioritizer, HTTP Range server
└── feature/
    ├── catalog/          # ABB discovery, search screen, direct magnet input dialog
    ├── detail/           # Book inspection, selective file download list, torrent metadata
    ├── library/          # Local library, Listen Later ("To Read") shelf, global bookmarks manager
    └── player/           # Portrait & landscape Oleg Brailean player deck, sheets, settings
```

---

## 📱 Target Platform & Testing

- **Target Device:** Samsung Galaxy Tab A7 (`SM-T505`, Android 12, `arm64-v8a`).
- **SDK Target:** Minimum SDK 26, Target SDK 34.
- **Local APK Path:** `/home/tpredrag/Desktop/Bookzor.apk` (strictly local builds, no cloud releases).

---

## 🚀 Build & Install Commands

```bash
# Check device connection
adb devices

# Build debug APK
./gradlew assembleDebug

# Deploy to connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch MainActivity
adb shell am start -n com.bookzor.app/.MainActivity

# Mirror APK to Desktop
cp app/build/outputs/apk/debug/app-debug.apk /home/tpredrag/Desktop/Bookzor.apk

# Live log inspection
adb logcat -s BookzorTorrent BookzorPlayer BookzorScraper
```

---

## 📚 Technical Documentation

- **[`REQUIREMENTS.md`](./REQUIREMENTS.md):** Functional specifications, non-functional constraints, and edge-case protocols.
- **[`ARCHITECTURE.md`](./ARCHITECTURE.md):** Database schemas, audio pipeline, and inter-module contracts.
- **[`ROADMAP_AND_EPICS.md`](./ROADMAP_AND_EPICS.md):** Epics, user stories, and execution checklists.
- **[`UI_DESIGN_SPEC.md`](./UI_DESIGN_SPEC.md):** Oleg Brailean layout measurements, typography, and color tokens.
