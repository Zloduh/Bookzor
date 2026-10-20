# Bookzor — Product & Technical Requirements Specification

---

## 1. Executive Summary

**Bookzor** is a native Android audiobook player designed to stream audiobooks on-demand directly from magnet links discovered via **AudioBookBay (ABB)**. It pairs sequential BitTorrent streaming technology with the ergonomic audiobook playback features pioneered by *MP3 Audiobook Player Pro* (Oleg Brailean).

---

## 2. Target Platforms & Specifications

- **Target OS:** Android 12 (API 31) through Android 14 (API 34).
- **Minimum SDK:** Android 8.0 (API Level 26).
- **Primary Test Device:** Samsung Galaxy Tab A7 (`SM-T505`), Android 12, `arm64-v8a`.
- **Form Factors:** Android Phones and Tablets (adaptive Compose layouts).

---

## 3. Functional Requirements (FR)

### 3.1. ABB Scraper & Discovery (`core:scraper`)

- **FR-SC-01 (Search):** Search ABB catalog by keyword (Title, Author, Narrator). Support sorting and pagination.
- **FR-SC-02 (Catalog Browsing):** Browse recent releases, popular books, and categories (Fantasy, Sci-Fi, Fiction, etc.).
- **FR-SC-03 (Metadata Extraction):**
  - Title, Author (`span.author`), Narrator (`span.narrator`), Cover Art URL (`img[itemprop=image]`).
  - Audio Format (`span.format`: M4B, MP3) and Abridged status (`span.is_abridged`).
  - Bitrate, Duration string, Creation Date.
  - File Size (GB/MB) and Piece Size (KB).
  - Seeders / Leechers count (where available).
  - Info Hash (`<td>Info Hash:</td><td><hash></td>`).
  - Trackers list (`<td>Tracker:</td><td><tracker_url></td>`).
  - Multi-file structure (list of audio files in the release).
- **FR-SC-04 (Magnet URI Construction):** Dynamically construct standard BitTorrent magnet URIs:
  ```text
  magnet:?xt=urn:btih:<INFO_HASH>&dn=<ENCODED_TITLE>&tr=<TRACKER_1>&tr=<TRACKER_2>...
  ```
  Inject standard public fallback trackers if ABB returns fewer than 3 active trackers.
- **FR-SC-05 (Dynamic Domain & Failover):**
  - ABB domains cycle (`.lu`, `.is`, `.nl`, `.se`, `.fi`, etc.). The app must not hardcode a single domain.
  - Implement a configurable primary domain in Settings, with automatic failover to fallback mirrors if HTTP 5xx, timeout, or DNS resolution fails.
- **FR-SC-06 (Anti-Scraping / Cloudflare Mitigation):**
  - Mimic real Android Chrome User-Agent strings.
  - Handle cookies across requests using persistent `CookieJar`.
  - Provide manual fallback option: User can paste any raw magnet URI or ABB URL directly into the app.

---

### 3.2. BitTorrent Sequential Streaming Engine (`core:torrent`)

- **FR-TO-01 (Embedded Engine):** Use `libtorrent4j` (C++ libtorrent via JNI) embedded inside the Android app.
- **FR-TO-02 (DHT & Peer Exchange):** Enable Mainline DHT, PEX (Peer Exchange), and LSD (Local Service Discovery) for rapid peer discovery without relying solely on ABB trackers.
- **FR-TO-03 (Sequential Piece Priority Engine):**
  - Standard torrents download rarest-first; Bookzor must enforce **sequential downloading**.
  - Priority window: Prioritize the piece at the current playhead offset, followed by the next 10–20 pieces (the "read-ahead buffer").
  - Pieces beyond the buffer window are set to low priority or ignored until the playhead approaches them.
- **FR-TO-04 (M4B / MP4 `moov` Atom Handling):**
  - For single `.m4b` / `.mp4` containers, the `moov` atom (metadata index) may be located at the end of the file.
  - The torrent engine must immediately prioritize the **first piece** AND the **last piece(s)** of the file to extract the `moov` atom before sequential playback starts.
- **FR-TO-05 (Multi-File MP3 Prioritization):**
  - In multi-track releases (e.g., 50 separate MP3s), only File `N` (currently playing) has `HIGH` priority.
  - When File `N` reaches 85% completion, set File `N+1` to `NORMAL` priority to pre-buffer the next track and prevent gaps between chapters.
  - Set all other files to `DO_NOT_DOWNLOAD` (priority 0) to save cellular/WiFi bandwidth.
- **FR-TO-06 (Local HTTP 1.1 Range Loopback Server):**
  - Run an internal HTTP server listening on `127.0.0.1:<random_or_fixed_port>`.
  - Expose stream endpoints: `http://127.0.0.1:8080/stream?hash=<hash>&fileIndex=<index>`.
  - Must fully implement HTTP `Range` requests (`Accept-Ranges: bytes`, `206 Partial Content`, `Content-Range: bytes START-END/TOTAL`).
  - When ExoPlayer requests byte range `[A, B]`, the server maps bytes to piece indexes, requests missing pieces from libtorrent with top priority, blocks until bytes are ready, and pipes them to the HTTP response stream.
- **FR-TO-07 (Smart Persistent Cache):**
  - Streamed torrent pieces are stored in the app's cache/data directory.
  - Downloaded pieces remain on disk so re-listening to previously played chapters requires **zero network transfer**.
  - User can toggle: "Keep downloaded books offline" or "Purge cache after book completion".

---

### 3.3. Audio Engine & Background Service (`core:player`)

- **FR-PL-01 (Media3 Architecture):** Implemented using `androidx.media3:media3-exoplayer` and `androidx.media3:media3-session`.
- **FR-PL-02 (Foreground Service):** Background audio service running as a foreground service with persistent notification, album art, playback controls (Play/Pause, Skip Back 15s, Skip Forward 30s), and book title.
- **FR-PL-03 (Audio Focus & Interruptions):**
  - Handle audio focus changes properly: duck volume during navigation directions, pause on incoming calls, resume when calls end.
  - Auto-pause when headphones or Bluetooth devices disconnect ("becoming noisy").
- **FR-PL-04 (Hardware & Bluetooth Controls):** Full support for headset media buttons, lockscreen media controls, Bluetooth AVRCP, and Android Auto compatibility.
- **FR-PL-05 (Variable Speed Playback with Pitch Correction):**
  - Variable playback speed: 0.5x to 3.0x in 0.05x increments.
  - ExoPlayer's Sonic audio processor preserves natural voice pitch (no chipmunk effect).
- **FR-PL-06 (Loudness Enhancer / Volume Boost):**
  - Integrate Android's native `android.media.audiofx.LoudnessEnhancer`.
  - Allows boosting volume up to +12dB for quiet narrator recordings.

---

### 3.4. Oleg Brailean-Inspired UI & Ergonomics (`feature:player`)

Modeled after *MP3 Audiobook Player Pro* (Oleg Brailean):

- **FR-UI-01 (Dual Progress Trackers):**
  - **Overall Book Tracker:** Displays total elapsed time, total book duration, and overall percentage (e.g. `4h 12m / 18h 35m • 22%`).
  - **Chapter/File Tracker:** Displays elapsed time in the current chapter/file and chapter duration (e.g. `12m 45s / 45m 00s`).
- **FR-UI-02 (Fine Scrubbing Slider):**
  - Scrubbing the slider directly performs standard seek.
  - Dragging the finger vertically downward switches to fine-scrubbing mode:
    - Normal (100% speed)
    - Half speed (50% scrubbing rate)
    - Quarter speed (25% scrubbing rate)
    - Fine scrubbing (10% scrubbing rate)
- **FR-UI-03 (Big Ergonomic Jump Buttons):**
  - Primary controls: Large Jump Back (`-15s`) and Jump Forward (`+30s`) flanking the Play/Pause button.
  - Intervals configurable in settings (5s, 10s, 15s, 30s, 45s, 60s).
- **FR-UI-04 (Torrent Buffer Visualization):**
  - Visual buffer bar beneath the scrubber showing which pieces of the audio file are cached on disk vs still downloading vs not downloaded.
- **FR-UI-05 (Sleep Timer System):**
  - Presets: 15m, 30m, 45m, 60m, or "End of Chapter".
  - **Fade-out:** Audio smoothly ramps down volume over the last 30 seconds before stopping.
  - **"Shake to Reset":** When the timer has 1 minute remaining, the device gives a subtle haptic vibration. Shaking the device resets the sleep timer to the original duration without requiring the user to turn on the screen.
- **FR-UI-06 (Smart Rewind on Resume):**
  - If playback was paused for > 10 minutes: automatically rewind 10 seconds.
  - If paused for > 1 hour: automatically rewind 30 seconds.
  - If paused for > 24 hours: automatically rewind 60 seconds.
- **FR-UI-07 (Bookmarks & Notes):**
  - One-tap bookmark button on the player screen.
  - Ability to attach a custom text note to any timestamp.
  - Quick list of bookmarks with one-tap seeking.
- **FR-UI-08 (Gestures):**
  - Double-tap cover art to Play/Pause.
  - Swipe left/right on cover art to skip forward/backward.
- **FR-UI-09 (OLED Dark Mode):** High contrast true black theme for bedside night listening.

---

### 3.5. Library & State Persistence (`core:database`)

- **FR-DB-01 (Persistent Resume):** App must save the exact playback position (millisecond accuracy), current track/file index, and active speed to Room database on every pause and at 5-second intervals during playback.
- **FR-DB-02 (Offline Library):**
  - "My Books" tab showing all started, downloaded, and bookmarked books.
  - Download status indicator (Streaming / Partially Cached / Fully Downloaded).
  - Storage manager: view disk space consumed per book with one-tap "Clear Cache" or "Delete Book".

---

## 4. Non-Functional Requirements (NFR)

- **NFR-01 (Cold Start Playback Latency):** From tapping "Stream Now" to audio output must be **under 15 seconds** on a torrent with >= 5 seeders.
- **NFR-02 (Memory Consumption):** App RAM footprint must not exceed 180MB during continuous streaming and playback.
- **NFR-03 (Battery & Background Execution):** Torrent networking threads must throttle to idle when audio is paused. Acquire `WAKE_LOCK` only while audio is actively playing in the foreground service.
- **NFR-04 (Network Transition Resilience):** Switching between WiFi and Cellular must not crash the player. Torrent engine must re-announce to trackers/DHT without breaking the active HTTP loopback connection.
- **NFR-05 (Cellular Protection):** Setting to "Stream only on Wi-Fi" with a clear confirmation prompt before using mobile data.

---

## 5. Edge Cases & Guardrails

| Edge Case | Failure Mode | Mitigation / Guardrail |
| :--- | :--- | :--- |
| **0 Seeders / Dead Torrent** | Engine stalls indefinitely waiting for pieces | Display clear UI banner: *"Finding peers (0 connected)... Torrent may be inactive"*. Provide a 30s timeout with button to search alternative releases. |
| **M4B with `moov` at EOF** | ExoPlayer cannot parse audio header, fails immediately | Engine detects `.m4b` extension; requests the last 2MB piece of the file BEFORE feeding byte 0 to the HTTP server. |
| **Cloudflare Challenge on ABB** | HTTP 403 / 503 on search or book page | Show user-friendly error with option to change ABB mirror domain or paste a direct magnet link. |
| **ExoPlayer Range Seek Stall** | User seeks into an uncached piece, socket blocks | Local HTTP server immediately prioritizes seek piece; returns HTTP 206 chunk as soon as piece arrives; UI shows buffering spinner. |
| **Multi-File Track Order** | Files sorted alphabetically like `1.mp3, 10.mp3, 2.mp3` | Implement natural numerical sorting (`01`, `02`, ... `10`) based on file names or ID3 track number metadata. |
