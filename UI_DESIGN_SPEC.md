# Bookzor — Visual Design & UI Specification

Modeled directly after **MP3 Audiobook Player Pro** (Oleg Brailean), adapted for modern Android Jetpack Compose and Material 3 OLED themes.

---

## 🎨 1. Color Palette & Typography

### Themes
- **OLED Dark (Default):**
  - Background: `#000000` (Pure black for battery savings & bedside reading)
  - Surface: `#121212` (Card and bottom sheet surfaces)
  - Surface Variant: `#1E1E1E`
  - Accent / Primary: `#FF5722` (Warm Amber/Terracotta Orange — Oleg Brailean signature)
  - On Primary: `#FFFFFF`
  - Text Primary: `#FFFFFF`
  - Text Secondary: `#B0B0B0`
  - Buffer Bar Cached: `#4CAF50` (Subtle green) or `#FF8A65` (Light terracotta)
  - Buffer Bar Uncached: `#333333`

---

## 📐 2. Screen Anatomy & Layouts

### 2.1. Main Player Screen (Oleg Brailean Deck)

```
┌─────────────────────────────────────────────────────────────┐
│  [⏱ Sleep]         The Way of Kings               [📖 List] │
├─────────────────────────────────────────────────────────────┤
│  OVERALL BOOK PROGRESS STRIP                                │
│  Duration: 45:32:10        Played: 12:45:00 ( 28% ) [◑]     │
│  [━━━━━━━━━━━━━━━━━━━━━░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░] │
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
│  Chapter 14: The Tremors of Arrakis                         │
│  Frank Herbert • Read by Scott Brick                        │
│                                                             │
│  CHAPTER PROGRESS & FINE SCRUBBING SLIDER                   │
│  14:20 ───●───────────────────────────────────────── -33:50 │
│  [▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░] │ (Torrent Buffer)
│                                                             │
│  CONTROL DECK                                               │
│    [🔖]        (15s)          ▶          (30s)       [🎛️ 1.25x]│
│  Bookmark    Jump Back    Play/Pause   Jump Fwd     Speed   │
└─────────────────────────────────────────────────────────────┘
```

#### Key Visual Components:
1. **Header Bar:**
   - Left: **Sleep Timer icon** (`⏱` stopwatch). Tap opens duration picker (15m, 30m, 45m, 60m, End of Chapter, Shake-to-Reset toggle). When active, displays remaining countdown (e.g., `24m`).
   - Center: **Book Title** (single-line marquee if long).
   - Right: **Chapter / File Drawer icon** (`📖` open book). Tap slides open chapter/track sheet.
2. **Dual Progress Section:**
   - **Top Strip (Total Book):**
     - Left text: `Duration: HH:MM:SS`
     - Right text: `Played: HH:MM:SS ( XX% )` + circular progress pie icon `[◑]` showing visual book completion.
     - Thin horizontal bar across entire screen width showing overall progress.
   - **Lower Slider (Current Chapter / File):**
     - Thumb with active timestamp on left (`14:20`) and countdown remaining on right (`-33:50`).
     - **Fine Scrubbing Indicator:** When user touches thumb and drags downward:
       - Offset > 40dp: Badge pops up `"Half Speed (50%)"`
       - Offset > 80dp: Badge pops up `"Quarter Speed (25%)"`
       - Offset > 120dp: Badge pops up `"Fine Speed (10%)"`
     - **Torrent Piece Visualizer:** A thin micro-bar directly below the chapter slider showing green segments for downloaded/cached pieces and gray for pending pieces.
3. **Primary Control Deck:**
   - **Bookmark Icon (`🔖`):** One-tap instant bookmark at current position. Long-press adds text note.
   - **Jump Back Button (`↺ 15s`):** Big circular touch target (min 48dp). Configurable in settings.
   - **Play / Pause Button (`▶` / `⏸`):** Hero button (64dp - 72dp) with haptic feedback on tap.
   - **Jump Forward Button (`↻ 30s`):** Big circular touch target (min 48dp). Configurable in settings.
   - **Speed / Audio FX Pill (`🎛️ 1.25x`):** Displays current speed. Tap opens speed slider (0.5x - 3.0x) + Volume Boost slider (0 to +12dB).

---

### 2.2. Chapter & File Drawer (Slide-Up / Side Sheet)

```
┌─────────────────────────────────────────────────────────────┐
│  Chapters & Files                                     [ ✕ ] │
├─────────────────────────────────────────────────────────────┤
│  01 - Prologue                            12:33      [●]    │
│  02 - The Arrival                         18:45      [●]    │
│  03 - The Council Meeting                 24:10      [◑]    │  <-- Currently Playing (50%)
│  04 - Across the Desert                   31:05      [○]    │  <-- Unplayed
│  05 - Nightfall                           19:22      [○]    │
└─────────────────────────────────────────────────────────────┘
```
- Each row shows:
  - Track / Chapter title
  - Duration (`MM:SS` or `HH:MM:SS`)
  - Circular completion glyph:
    - Filled `[●]` = Completed
    - Pie `[◑]` = Partially played with exact fill percentage
    - Hollow `[○]` = Unplayed
  - Tap row jumps directly to that chapter.

---

### 2.3. Search & Catalog Screen (AudioBookBay In-App)

```
┌─────────────────────────────────────────────────────────────┐
│  🔍 Search title, author, or narrator...                   │
├─────────────────────────────────────────────────────────────┤
│  [ All ] [ Sci-Fi ] [ Fantasy ] [ Fiction ] [ History ]     │
├─────────────────────────────────────────────────────────────┤
│  ┌─────┐  Dune (Unabridged)                                 │
│  │     │  Frank Herbert • Narrated by Scott Brick           │
│  │ IMG │  M4B • 1.12 GB • 21h 02m                           │
│  │     │  Seeds: 42 • Peers: 12                             │
│  └─────┘  [ ▶ Stream ]  [ ℹ Details ]                       │
│  ─────────────────────────────────────────────────────────  │
│  ┌─────┐  The Way of Kings                                  │
│  │     │  Brandon Sanderson • Narrated by Michael Kramer    │
│  │ IMG │  MP3 (48 files) • 2.1 GB • 45h 32m                 │
│  │     │  Seeds: 88 • Peers: 25                             │
│  └─────┘  [ ▶ Stream ]  [ ℹ Details ]                       │
└─────────────────────────────────────────────────────────────┘
```

---

## 📱 3. Tablet Adaptive Layout (Samsung Galaxy Tab A7)

On tablet screens (width >= 600dp), the UI switches to a **Split-View Canvas**:
- **Left Column (45% width):** Large Cover Art + Book Metadata + Overall Progress + Chapter Progress & Control Deck.
- **Right Column (55% width):** Persistent Chapter/Track List + Bookmarks Tab + Torrent Swarm Stats (Seeds, Leechers, Download Rate, Cached Chunks).


---

### 2.4. Discovery Chips & Interactive Author / Narrator Links

Modeled for immediate exploration of related works without manual typing:

```
┌─────────────────────────────────────────────────────────────┐
│  [ 👤 More by Brandon Sanderson ]  [ 🎙️ Voice: Michael Kramer ]│
└─────────────────────────────────────────────────────────────┘
```

- **Player Screen Placement:** Positioned directly beneath `OlegBookStrip` on both phone portrait and tablet split-view layouts.
- **Library Card Placement:** Positioned directly below format and offline badges on every `LibraryBookCard`.
- **Interactive Details Modal:** Tapping `ℹ️` on any library card displays a modal with book cover, format, duration, and one-tap action buttons:
  - `[ 👤 More books by <Author> ]`
  - `[ 🎙️ More books read by <Narrator> ]`

---

### 2.5. Real-Time Library Search & Web Fallback

```
┌─────────────────────────────────────────────────────────────┐
│  [ ← ] [ 🔍 Filter title, author, actor... ]          [ ✕ ] │
├─────────────────────────────────────────────────────────────┤
│  ┌───────────────────────────────────────────────────────┐  │
│  │ 🔎 Search AudioBookBay for "<query>"                  │  │
│  │ Find more books online by this author, actor or title │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

- Live interactive filtering as you type across all local audiobooks.
- Prominent orange accent banner at the top of the list allowing instant search of AudioBookBay for that exact query.

---

### 2.6. In-App Update Dialog (Settings Screen)

```
┌─────────────────────────────────────────────────────────────┐
│  Update Available                                           │
├─────────────────────────────────────────────────────────────┤
│  A new version of Bookzor is available.                     │
│  Version: 1.0.2 (Current: 1.0.1)                            │
│                                                             │
│  What's New:                                                │
│  • Added search by Author and Actor from Library & Player   │
│  • Real-time library search bar                             │
│                                                             │
│  Downloading: 42.5 MB / 69.2 MB (61%)                       │
│  [━━━━━━━━━━━━━━━━━━━━━━━━━━░░░░░░░░░░░░░░░]                │
├─────────────────────────────────────────────────────────────┤
│                         [ Cancel ]    [ Download & Install ]│
└─────────────────────────────────────────────────────────────┘
```
