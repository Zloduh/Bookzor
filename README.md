# Bookzor 🎧

A modern, high-performance native Android audiobook player engineered for seamless on-demand BitTorrent streaming, direct catalog discovery, and offline library playback.

---

## ✨ Features

### ⚡ Sequential Torrent Streaming
- **Stream on Demand:** Stream audiobooks directly from magnet links or info hashes without waiting for the full download to complete.
- **Embedded libtorrent Engine:** High-performance C++ `libtorrent4j` sequential piece scheduler prioritizing audio playback buffers and MP4 `moov` metadata atoms.
- **Local Loopback Streaming Server:** Internal HTTP range streaming server feeding audio smoothly into Google ExoPlayer.
- **Selective File Downloads:** Pick individual chapters or parts in multi-file releases to save device storage.

### 🔍 Discovery & Catalog
- **Integrated Catalog Search:** Live search by title, author, and narrator.
- **Mirror Failover Engine:** Automatically discovers and connects to the healthiest active mirror.
- **One-Tap Discovery Chips:** Instant links to explore more works by the current author or voice actor.
- **Direct Magnet & Link Input:** Paste any standard BitTorrent magnet link or HTTP URL to stream instantly.

### 🎛️ Audio Controls & Playback
- **Fine-Grained Scrubbing:** Smooth, responsive timeline scrubber with bookmark tick markers and tap-to-seek.
- **Custom Jump Intervals:** Fully customizable forward and rewind intervals (5s to 90s) persisted across the app.
- **Variable Speed & Loudness Boost:** Pitch-corrected playback speeds (0.5x to 3.0x) and hardware loudness boost up to +12dB.
- **Smart Rewind:** Automatically rewinds 10–30 seconds after long pauses so you never lose your place.
- **Sleep Timer:** Countdown timer with gentle 15-second volume fade-out, End-of-Chapter mode, and optional shake-to-reset.

### 🔖 Bookmarks & Notes
- **One-Tap Bookmarking:** Save timestamps instantly from the player deck or with custom notes.
- **Global Bookmarks Manager:** View, edit, filter, and jump to bookmarks across your entire audio library.

### 📁 Offline Library & Sideloading
- **Public Storage Persistence:** Audio files save directly to device storage (`Download/Audiobooks/`), ensuring books survive app updates or re-installations.
- **Local Scanner:** Automatically scans and indexes sideloaded `.m4b`, `.mp3`, and folder-based audiobooks.
- **Listen Later Shelf:** Curate a dedicated queue of books you plan to listen to next.

### 🔄 In-App Updates & Diagnostics
- **One-Tap Updates:** Check for, download, and install latest releases directly from GitHub.
- **Diagnostic Log Export:** Easily share compressed diagnostic logs for troubleshooting.

---

## 📱 Platform & Requirements

- **Operating System:** Android 8.0 (API level 26) and newer
- **Architectures:** `arm64-v8a`, `armeabi-v7a`, `x86_64`
- **Form Factors:** Optimized for both Android phones and tablets with responsive adaptive layouts

---

## 📥 Download & Releases

Download the latest signed APK directly from the [GitHub Releases](https://github.com/Zloduh/Bookzor/releases) page.

---

## 🏗️ Architecture

Bookzor is built with modern Android development standards using a multi-module architecture:

```
Bookzor/
├── app/                  # Application startup, Navigation, Scanner, Updater, MainActivity
├── core/
│   ├── common/           # UserPreferencesRepository, ThemeManager, Dispatchers, Models
│   ├── database/         # Room Database, DAOs, Entities (Books, Tracks, Bookmarks, PlaybackState)
│   ├── player/           # ExoPlayer, PlaybackService, MediaSession, SleepTimerController
│   ├── scraper/          # Catalog scraper (Jsoup), DomainManager, MagnetBuilder
│   └── torrent/          # libtorrent4j session, SequentialPieceScheduler, TorrentStreamServer
└── feature/
    ├── catalog/          # Discovery catalog, live search, direct magnet dialog
    ├── detail/           # Audiobook details, chapter inspector, file download picker
    ├── library/          # Library bookshelf, Listen Later shelf, Global Bookmarks
    └── player/           # Adaptive player deck, chapter drawer, playback controls
```

---

## 🛠️ Building from Source

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 34

### Build Command
```bash
# Clone the repository
git clone https://github.com/Zloduh/Bookzor.git
cd Bookzor

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease
```
The built APK will be located at `app/build/outputs/apk/release/app-release.apk` (or `debug/app-debug.apk`).

---

## 📄 License

This project is licensed under the Apache License 2.0.
