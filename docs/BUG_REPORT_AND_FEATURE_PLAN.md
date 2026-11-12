# Bookzor — Bug Reports & Feature Execution History (v1.0.2)

---

## 🐛 Resolved Bug Reports

### Bug 1: Android 12–14 `ForegroundServiceDidNotStartInTimeException` Crash
- **Observed Behavior:** On Android 12+, launching the app or starting audio playback intermittently triggered an ANR crash with `android.app.ForegroundServiceDidNotStartInTimeException: Context.startForegroundService() did not then call Service.startForeground()`.
- **Root Cause:**
  1. `MainActivity.onCreate()` prematurely called `ensureServiceStarted()` before ExoPlayer or audio tracks were ready.
  2. `PlaybackService.onStartCommand()` initiated asynchronous media loading before invoking `startForeground()`, exceeding Android's 5-second watchdog budget.
- **Surgical Resolution:**
  1. Removed premature foreground service trigger from `MainActivity.onCreate()`.
  2. In `PlaybackService.onStartCommand()`, immediately invoked `startForeground(NOTIFICATION_ID, notification)` on the main thread using a valid `MediaStyle` notification.
  3. Proper `foregroundServiceType="mediaPlayback"` declared in `AndroidManifest.xml`.

---

### Bug 2: Downloaded Audiobooks Lost Upon App Reinstall / Update
- **Observed Behavior:** If the user downloaded an audiobook, and the app was subsequently updated or reinstalled, the downloaded audiobook was deleted.
- **Root Cause:** Torrents were downloaded to internal app sandbox storage (`context.filesDir/audiobooks`), which Android permanently purges whenever an app package is uninstalled or fresh installed.
- **Surgical Resolution:**
  1. Migrated `TorrentEngine` storage directory to `/storage/emulated/0/Download/Audiobooks/`.
  2. Configured `MANAGE_EXTERNAL_STORAGE` permission check on startup.
  3. Audiobooks now permanently persist across updates, uninstalls, and allow direct USB sideloading.

---

### Bug 3: Duplicate Book Entries in Library After Download
- **Observed Behavior:** Downloading an audiobook from AudioBookBay resulted in two separate book cards appearing in the Library: one with a thumbnail cover art (from ABB scraping) and one without a thumbnail (from the local disk scanner), playing separately and with different parsed metadata.
- **Root Cause:**
  - Single-file torrents downloaded directly into `Download/Audiobooks/filename.m4b`.
  - `LocalAudiobookScanner` was generating a synthetic infoHash (`file_filename.m4b`) and creating a second `BookEntity` in the database without checking if the file belonged to an existing AudioBookBay book.
- **Surgical Resolution:**
  1. Added `findTrackByFileName(fileName)` to `AudioTrackDao` and normalized title matching.
  2. When the scanner encounters an audio file, it matches it against existing ABB tracks and updates the primary ABB book (`isInLibrary = true`), keeping cover art, chapters, and metadata intact.
  3. Added a two-pass `deduplicateBooks()` engine that purges any bare ghost copies while retaining the primary book card.

---

## 🚀 Implemented Features (v1.0.0 – v1.0.2)

### Feature 1: In-App Auto-Updater (`AppUpdateManager.kt`)
- **User Need:** Update the tablet wirelessly without needing a PC, ADB commands, or USB cables.
- **Implementation:**
  - Queries `https://api.github.com/repos/Zloduh/bookzor-releases/releases/latest`.
  - Compares versions dynamically (`isVersionNewer`).
  - Downloads APK with live progress indicator (MB and %).
  - Triggers the native Android package installer using `FileProvider`.

---

### Feature 2: In-App Diagnostic Log Collector & One-Tap Share (`LogCollector.kt`)
- **User Need:** Export device diagnostics and error logs easily from the tablet.
- **Implementation:**
  - Gathers device specs, memory usage, storage metrics, and the last 500 lines of logcat.
  - Compresses them into `bookzor_logs_<timestamp>.zip`.
  - Opens Android's native share sheet (Gmail, Drive, Telegram, QuickShare) for one-tap sharing.

---

### Feature 3: Search by Author & Voice Actor (Narrator) in Player & Library
- **User Need:** When listening to or viewing a book, easily find other books by that same author or voice actor.
- **Implementation:**
  - **Player Screen:**
    - Clickable Author and Narrator in `BookDetailStrip`.
    - Dedicated discovery chips row below the top strip:
      - `[ 👤 More by <Author> ]`
      - `[ 🎙️ Voice: <Narrator> ]`
      - `[ ℹ️ Details ]` (opens the full ABB book detail page).
  - **Library Screen:**
    - Clickable Author and Narrator lines on every book card.
    - Quick chips: `[ 👤 <Author> ]` and `[ 🎙️ <Narrator> ]`.
    - `[ ℹ️ ]` Details action button opening a modal with direct AudioBookBay search buttons.
  - **Real-Time Library Search Bar:**
    - Live search filter in the Library top bar matching title, author, or actor.
    - One-tap banner to execute the query online on AudioBookBay: `"Search AudioBookBay for '<query>'"` to discover new books.

---

### Feature 4: "To-Read" List (Listen Later)
- **User Need:** Save interesting books found on AudioBookBay for later without immediately downloading audio files.
- **Implementation:**
  - `is_to_read` column in `BookEntity`.
  - `[ 🔖 To-Read List ]` toggle in Book Detail.
  - Dedicated `ToReadScreen.kt` in side navigation drawer.
  - Actions: Stream immediately, Put in Library (download offline), or Remove.
