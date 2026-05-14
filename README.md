# Helium

Helium is an Android EPUB reader focused on local library management, EPUB rendering, search, annotations, bookmarks, and customizable reading themes.

The project targets Android SDK 26-36 and is implemented primarily in Java with AndroidX/AppCompat components. Runtime sync and backup features have been removed; some legacy database columns or table names may remain for compatibility with the reproduced source structure.

## Key Feature

- Local EPUB library discovery through Android MediaStore.
- Optional folder-based library filtering from settings.
- EPUB metadata parsing, cover extraction, and cached library records.
- Reader screen with paged and scrolled reading modes.
- Chapter navigation, paper page navigation, and reader back stack.
- In-book search with recent search history and optional voice input.
- Text annotations with highlight, underline, notes, and note markers.
- Bookmarks persisted per book and position.
- Custom reader themes, text size, margins, fonts, line spacing, and alignment.
- Light and dark application themes with reader-specific chrome behavior.
- Image preview flow for EPUB embedded images.
- Footnote popup rendering inside the reader.
- Annotation/export support through plain text and HTML formatters.

## Overall Architecture

Helium follows a classic Android Activity/Fragment architecture backed by SQLite storage and a WebView-based EPUB rendering engine.

### Core Components

- `MainActivity`
  - Owns the main library shell, navigation drawer, search UI, and library refresh entry points.
- `BooksFragment` and `BooksAdapter`
  - Render the local book list and connect library data to the main UI.
- `ReaderActivity`
  - Owns the reading session, reader chrome, navigation drawer, search overlay, display settings, bookmarks, annotations, footnotes, and image preview flow.
- `Book`, `EPubBook`, and EPUB parser classes
  - Load EPUB files, parse package metadata, table of contents, rendition properties, page maps, and searchable text.
- `BookView`, `EPubBookView`, `HtmlContentView`, and `HtmlContentWebView`
  - Render EPUB content and expose reading controls such as page changes, position restore, search result jumps, and annotation jumps.
- `JavaScriptBridge`
  - Exposes native Java objects to WebView JavaScript using Android's native `addJavascriptInterface` bridge.
- `AnnotationController`, `HtmlAnnotationRenderer`, and `SelectionToolbarView`
  - Coordinate text selection, highlight/underline creation, annotation note editing, database persistence, and JavaScript-side rendering.
- `LibraryUpdate` and `LibraryManager`
  - Scan MediaStore for EPUB files, apply optional folder filtering, parse new books, update covers, and synchronize the local database with files currently available on device storage.
- `DatabaseOpenHelper` and table classes under `db`
  - Define SQLite tables for books, categories, bookmarks, annotations, themes, search history, scan failures, page maps, and retained legacy compatibility fields.
- `ThemeManager`, `AppThemeManager`, and `Theme`
  - Manage built-in and custom reader themes plus application chrome colors.

### UI Layer

- Activities:
  - `MainActivity` for library browsing.
  - `ReaderActivity` for reading and annotation workflows.
  - `SettingsActivity` and `SettingsHtmlActivity` for application and reader settings.
  - `ThemesActivity` for reader theme management.
  - `PhotoActivity` for EPUB image viewing.
  - `PermissionRequiredActivity` for storage permission handling.
- Fragments:
  - `BooksFragment` for book list presentation.
  - `MainDrawerFragment` and `ReaderDrawerFragment` for navigation drawers.
  - `DisplaySettingsFragment` for reader display controls.
- Custom views:
  - `SearchBarView` for expandable search behavior.
  - `SelectionToolbarView` for annotation actions.
  - `SystemBarsFrame`, `DisplayCutoutFrameLayout`, `ScrimInsetsFrameLayout`, and related widgets for fullscreen and system bar layout behavior.
- Resources:
  - Layout XML files define the main library screen, reader screen, toolbar, preferences, annotation toolbar, search rows, and dialogs.
  - `values/styles.xml` and `values-night/styles.xml` define light/dark theme mappings.
  - `values/colors.xml` centralizes shared color resources.

### Data Flow

1. Library loading:
   - `LibraryUpdate` queries `MediaStore.Files` for `.epub` entries.
   - Optional folder filtering is applied against the selected library folder.
   - New files are hashed and passed to `LibraryManager.scanBook`.
   - Parsed book metadata, folder, cover, hash, and timestamps are stored in SQLite.
   - `BooksFragment` reloads database-backed book records and updates the library UI.

2. Opening a book:
   - `ReaderActivity` receives a book id or external EPUB intent.
   - Book metadata is loaded from SQLite.
   - `Book.create` creates an `EPubBook` instance from the file path.
   - `EPubBookView` creates content views for EPUB spine documents.
   - `HtmlContentWebView` injects EPUB JavaScript, reader settings, theme data, and annotation scripts.

3. Reading position:
   - WebView JavaScript reports paging, anchors, and content size through `HeliumApp`.
   - `ReaderActivity` updates progress, paper page labels, bookmarks, and table-of-contents state.
   - Current position is persisted back to the books table.

4. Search:
   - `ReaderActivity` submits queries to the active `SearchProvider`.
   - `EPubSearchProvider` scans EPUB content and returns matching `SearchResult` objects.
   - Search results jump back into the active book view using stored result positions.
   - Submitted queries are persisted through `SearchHistoryManager`.

5. Annotation:
   - `annotations.js` tracks WebView text selection and calls `liNativeAnnotations`.
   - `HtmlAnnotationRenderer.JsApi` forwards selection events to `AnnotationController`.
   - `SelectionToolbarView` collects highlight color, underline state, and notes.
   - `AnnotationController` writes annotation data to the `highlights` table.
   - `HtmlAnnotationRenderer` calls `HeliumAnnotations` in JavaScript to render, update, or delete annotation spans.

6. Export:
   - Export tasks read persisted reader data from SQLite.
   - Formatter implementations generate plain text or HTML output.
   - Android sharing/file APIs deliver the generated export.

### Design Pattern

- Activity/Fragment controller pattern:
  - Activities coordinate high-level workflows while fragments and adapters manage focused UI sections.
- Adapter pattern:
  - RecyclerView/ListView adapters translate database or model objects into reusable UI rows.
- Manager pattern:
  - `LibraryManager`, `ThemeManager`, `BookmarkManager`, and `SearchHistoryManager` isolate feature-specific persistence and business logic.
- Renderer/controller split:
  - `AnnotationController` owns annotation state and persistence.
  - `AnnotationRenderer` abstracts the rendering target.
  - `HtmlAnnotationRenderer` implements rendering through WebView JavaScript.
- Bridge pattern:
  - `JavaScriptBridge` connects JavaScript reader logic with native Android code.
  - JavaScript handles DOM/range operations while Java owns persistence and Android UI state.
- Background worker pattern:
  - `LibraryUpdate` runs file discovery and book scanning on a background thread, then posts results back to the main thread.
- Table definition pattern:
  - Database table classes centralize table and column names so feature code avoids duplicating schema strings.

