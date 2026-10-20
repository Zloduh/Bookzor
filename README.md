# Bookzor 🎧

An Android audiobook player that streams books directly from **AudioBookBay (ABB)** magnet links, pairing a sequential BitTorrent streaming engine with the UX and control ergonomics of benchmark players (specifically modeled after the UX principles of *MP3 Audiobook Player Pro* by Oleg Brailean).

---

## 🎯 Core Concept

Instead of forcing users to download gigabytes of audio files before listening, **Bookzor**:
1. Scrapes and searches ABB directly inside the app (no manual magnet copying, no browser ads).
2. Extracts info hashes and trackers to construct valid magnet URIs dynamically.
3. Streams the torrent sequentially via an embedded torrent engine bridged to a local HTTP loopback server (`127.0.0.1:port`).
4. Feeds Android's native `Media3 / ExoPlayer` with standard HTTP `Range` requests, enabling instant playback (cold-start < 15 seconds) and arbitrary scrubbing.
5. Emulates the high-precision audio control deck of *MP3 Audiobook Player Pro*: dual progress bars (book vs chapter), fine scrubbing, volume/loudness enhancer, customizable jump intervals, sleep timer with shake-to-reset, and full offline retention of streamed chunks.

---

## 🏗️ Architecture at a Glance

```
┌─────────────────────────────────────────────────────────────┐
│                       Jetpack Compose UI                    │
│  [Catalog / Search]  [Book Detail]  [Oleg-Style Player Deck] │
└──────────────┬───────────────────────────────┬──────────────┘
               │                               │
               ▼                               ▼
    ┌──────────────────────┐      ┌───────────────────────────┐
    │     Scraper Engine   │      │       Player ViewModel    │
    │  - Jsoup HTML Parser │      │  - MediaControllerCompat  │
    │  - Dynamic Domain    │      │  - Dual Progress State    │
    │  - InfoHash & Tracker│      │  - Fine Scrubbing Logic   │
    └──────────┬───────────┘      └─────────────┬─────────────┘
               │ Magnet URI                     │
               ▼                                ▼
    ┌─────────────────────────────────────────────────────────┐
    │                  Background Audio Service               │
    │           (Media3 MediaSessionService + ExoPlayer)       │
    │  - Lockscreen / Notification / Headset / BT Controls     │
    │  - LoudnessEnhancer Audio Effect                        │
    │  - Persistent Playback State (Room DB)                  │
    └──────────────────────────┬──────────────────────────────┘
                               │ HTTP Range Requests
                               ▼ (http://127.0.0.1:8080/stream/...)
    ┌─────────────────────────────────────────────────────────┐
    │             Local Torrent Streaming Engine              │
    │                  (libtorrent4j C++ / JNI)               │
    │  - DHT, PEX, Tracker communication                      │
    │  - Sequential piece prioritization engine                │
    │  - Embedded HTTP 1.1 Range Server (Ktor / NanoHTTPD)    │
    │  - Smart persistent cache (Streamed audio stays offline)│
    └─────────────────────────────────────────────────────────┘
```

---

## 📚 Project Documentation

The documentation is organized for autonomous AI agents and human developers:

1. **[`ENVIRONMENT_SETUP.md`](./ENVIRONMENT_SETUP.md)**:
   - Development machine setup (Android Studio vs CLI on Linux/CachyOS/Arch, macOS, Windows).
   - JDK 17/21 installation.
   - Physical device debugging setup (specifically for attached Samsung Galaxy Tab A7 / `SM-T505`).
   - Terminal build, install, and logcat commands.

2. **[`REQUIREMENTS.md`](./REQUIREMENTS.md)**:
   - Detailed functional requirements (Scraper, Torrent Engine, Audio Player, UI/UX, Library).
   - Non-functional requirements (Cold start time, battery, memory, storage limits).
   - Edge cases & failure modes (Cloudflare, zero seeds, moov atom at EOF, multi-file MP3s).

3. **[`ARCHITECTURE.md`](./ARCHITECTURE.md)**:
   - Multi-module gradle structure (`core:scraper`, `core:torrent`, `core:player`, `core:database`, `feature:*`).
   - Complete Room Database entity schemas.
   - Torrent-to-HTTP Range streaming pipeline specification.
   - Oleg Brailean UI control deck specification.

4. **[`ROADMAP_AND_EPICS.md`](./ROADMAP_AND_EPICS.md)**:
   - Complete project breakdown: **7 Epics**, broken into discrete user stories and granular, checkable tasks.
   - Step-by-step guidance designed so a junior engineer or autonomous agent can execute tasks without ambiguity.

---

## 🚀 Quick Start for Developers

To verify your environment and attached device:

```bash
# Check connected Android device (Samsung Tablet)
adb devices

# Build debug APK
./gradlew assembleDebug

# Install on connected tablet
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Follow logs
adb logcat -s BookzorTorrent BookzorPlayer BookzorScraper
```
