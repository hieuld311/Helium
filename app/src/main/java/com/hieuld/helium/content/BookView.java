package com.hieuld.helium.content;

import android.content.Context;
import android.graphics.Bitmap;
import android.widget.FrameLayout;

import androidx.annotation.IntDef;

import com.hieuld.helium.annotations.AnnotationController;
import com.hieuld.helium.annotations.AnnotationRenderer;
import com.hieuld.helium.book.Book;
import com.hieuld.helium.book.SearchResult;
import com.hieuld.helium.book.TocEntry;
import com.hieuld.helium.fonts.Font;
import com.hieuld.helium.themes.Theme;

import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Abstract top-level view that renders a complete EPUB book.
 *
 * <p>Manages spine navigation and delegates rendering to child {@link ContentView} instances
 * (one per spine item). Communicates events back to the host activity via {@link Callbacks}.</p>
 */
public abstract class BookView extends FrameLayout {

    // ── Feature flags ─────────────────────────────────────────────────────────
    public static final int FEATURE_CUSTOM_TEXT_SIZE    = 1;
    public static final int FEATURE_CUSTOM_MARGIN       = 2;
    public static final int FEATURE_FONTS               = 4;
    public static final int FEATURE_CUSTOM_LINE_SPACING = 8;
    public static final int FEATURE_TEXT_ALIGN          = 16;

    // ── Flow modes ────────────────────────────────────────────────────────────
    public static final int FLOW_AUTO      = 0;
    public static final int FLOW_PAGING    = 1;
    public static final int FLOW_SCROLLING = 2;

    // ── Text align values ─────────────────────────────────────────────────────
    public static final int TEXT_ALIGN_DEFAULT = 0;
    public static final int TEXT_ALIGN_START   = 1;
    public static final int TEXT_ALIGN_JUSTIFY = 2;

    // ── Sentinel values ───────────────────────────────────────────────────────
    public static final int PAGE_UNKNOWN       = -1;
    public static final int PAPER_PAGE_UNKNOWN = -1;

    @IntDef({TEXT_ALIGN_DEFAULT, TEXT_ALIGN_START, TEXT_ALIGN_JUSTIFY})
    @Retention(RetentionPolicy.SOURCE)
    public @interface TextAlign {}

    @IntDef({FLOW_AUTO, FLOW_PAGING, FLOW_SCROLLING})
    @Retention(RetentionPolicy.SOURCE)
    public @interface FlowStyle {}

    protected Callbacks mCallbacks;
    private int mFeatures;

    // ── Callbacks ─────────────────────────────────────────────────────────────

    public static abstract class Callbacks {
        public void loadExternalUrl(String url) {}
        public void onFeaturesChanged() {}
        public void onInitDone() {}
        public void onLoadDone() {}
        public void onLoadFailed() {}
        public void onLoadStart(boolean isRefreshing) {}
        public void onPagePress(int x, int y) {}
        public void onPositionTouched() {}
        public void onReadingProgressChanged() {}
        public void onScroll() {}
        public void onTocEntryChanged(TocEntry tocEntry) {}
        public void pushBackStack() {}
        public void showFootNote(String url, String title, int x, int y) {}
        public void showPicture(Bitmap bitmap, int x, int y, int width, int height) {}
    }

    // ── Nested interfaces ─────────────────────────────────────────────────────

    public interface FootNoteProvider {
        String      getFootNoteBaseUrl();
        InputStream getFootNoteResource(String file);
        String      getFootNoteResourceMimeType(String file);
        void        onFootNoteNavigation(String url);
    }

    public interface PictureRestoreCallback {
        void onRestored();
    }

    // ── Constructor ───────────────────────────────────────────────────────────

    public BookView(Context context, long bookId, Book book, Callbacks callbacks) {
        super(context);
        mCallbacks = callbacks;
    }

    // ── Abstract interface ────────────────────────────────────────────────────

    /** Returns the current reading position as a serialised JSON string. */
    public abstract String getPosition();

    /** Returns {@code true} if the given serialised position is currently visible. */
    public abstract boolean isAtPosition(String position);

    /** Restore the reading position from a serialised JSON string. */
    public abstract void setPosition(String position);

    // ── Default implementations ───────────────────────────────────────────────

    public void   advancePage(int direction) {}
    public AnnotationRenderer createAnnotationRenderer(AnnotationController controller) { return null; }
    public String getContextString()          { return null; }
    public int    getFlow()                   { return FLOW_PAGING; }
    public int    getPage()                   { return 0; }
    public int    getPageCount()              { return 0; }
    public int    getPaperPage()              { return PAPER_PAGE_UNKNOWN; }
    public float  getScrollPosition()         { return 0.0f; }
    public String getTitle()                  { return null; }
    public boolean isFixedLayout()            { return false; }
    public void   jumpToAnnotation(long annotationId, String url, String chapterTitle) {}
    public void   jumpToPaperPage(int page)   {}
    public void   jumpToSearchResult(SearchResult result) {}
    public void   jumpToToc(String url)       {}
    public void   release()                   {}
    public void   restorePictureShowcase(PictureRestoreCallback callback) {}
    public void   setFont(Font font)          {}
    public void   setLineSpacing(float spacing) {}
    public void   setMargin(int margin)       {}
    public void   setNonContentBackgroundColor(int color) {}
    public void   setPage(int page, boolean animate) {}
    public void   setPreferredFlow(int flow)  {}
    public void   setScrollPosition(float position, boolean animate) {}
    public void   setTextAlign(@TextAlign int align) {}
    public void   setTextSize(int size)       {}
    public void   setTheme(Theme theme)       {}

    // ── Legacy page helpers (kept for compatibility) ──────────────────────────

    public void setLegacyPage(int page, boolean animate) { setPage(page, animate); }
    public int  getLegacyPage()                          { return getPage(); }
    public int  getLegacyPageCount()                     { return getPageCount(); }

    // ── Feature management ────────────────────────────────────────────────────

    protected void setFeatures(int features)     { mFeatures = features; }
    public int     getFeatures()                 { return mFeatures; }
    public boolean supportsFeature(int feature)  { return (feature & mFeatures) > 0; }
}
