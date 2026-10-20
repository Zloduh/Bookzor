# Bug Report & Feature Execution Plan

## 🐛 Bug Report: Color Theme Option Not Applying
- **Observed Behavior:** Selecting `Warm Sepia` or `Slate Navy` in Settings/Theme dialog changes the selected checkmark, but the UI remains static black.
- **Root Cause:** Compose screens (`PlayerScreen.kt`, `CatalogScreen.kt`, `BookDetailScreen.kt`, `SettingsScreen.kt`, `LibraryScreen.kt`) had hardcoded colors: `containerColor = Color.Black`, `background(Color(0xFF141414))`, `Color(0xFF1E1E1E)`. They were bypassing `MaterialTheme.colorScheme.background`, `MaterialTheme.colorScheme.surface`, `MaterialTheme.colorScheme.surfaceVariant`, and `MaterialTheme.colorScheme.primary`.
- **Surgical Fix:** Refactor screens to bind directly to `MaterialTheme.colorScheme` tokens so changing `ThemeManager.currentTheme` reactively recolors the entire app across all routes.

---

## 🎯 Feature 1: Filter / Search by Author and Voice Actor (Narrator)
- **User Need:** When browsing a book, easily see everything by that same author or narrated by that voice actor.
- **Implementation:**
  - In `BookDetailScreen.kt`: Make the Author and Voice Actor rows interactive chips:
    - `[ 👤 More by <Author> ]`
    - `[ 🎙️ Narrated by <Voice Actor> ]`
  - Tapping either invokes `onSearchQuery(query)`.
  - In `AppNavigation.kt`: Navigates to `Screen.Catalog` with the prefilled query and automatically executes the search.

---

## 🎯 Feature 2: "To-Read" List (Books for Later)
- **User Need:** A queue for books you want to listen to later, without cluttering the downloaded "My Library" or taking up storage space.
- **Implementation:**
  - **Database:** Add `is_to_read: Boolean` to `BookEntity` and `BookDao.observeToReadBooks()`.
  - **Book Detail CTA:** Add `[ 🔖 To-Read List ]` toggle button.
  - **Side Drawer:** Add `📖 To-Read List` drawer entry in `AppDrawer.kt`.
  - **Dedicated Screen (`ToReadScreen.kt`):**
    - Lists all saved To-Read books with covers, sizes, and formats.
    - One-tap actions per card:
      - `[ ▶ Stream ]` -> Starts playback immediately.
      - `[ 📥 Put in Library ]` -> Queues offline download to disk.
      - `[ ✕ Remove ]` -> Removes from To-Read list.
