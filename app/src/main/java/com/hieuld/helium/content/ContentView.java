package com.hieuld.helium.content;

import android.content.Context;
import android.widget.FrameLayout;

import com.hieuld.helium.annotations.AnnotationController;
import com.hieuld.helium.annotations.AnnotationRenderer;
import com.hieuld.helium.book.EPubBook;
import com.hieuld.helium.book.Rendition;
import com.hieuld.helium.book.TocEntry;
import com.hieuld.helium.fonts.Font;
import com.hieuld.helium.themes.Theme;

/**
 * Abstract base for all content views displayed inside a spine item panel.
 *
 * <p>Concrete subclasses handle specific content types:
 * <ul>
 *   <li>{@link HtmlContentView} — HTML/XHTML spine items rendered in a {@link android.webkit.WebView}</li>
 *   <li>{@link BitmapContentView} — standalone image spine items</li>
 *   <li>{@link ErrorContentView} — fallback for missing or unsupported content</li>
 * </ul>
 */
public abstract class ContentView extends FrameLayout {

    protected EPubBook mBook;
    protected ContentClient mContentClient;
    protected Context    mContext;

    // ── ContentClient ─────────────────────────────────────────────────────────

    /**
     * Callback interface through which a {@link ContentView} communicates back
     * to the enclosing {@link EPubBookView}.
     */
    public static abstract class ContentClient {
        public void loadExternalUrl(String url) {}
        public void loadUrl(String url, boolean pushBackStack) {}
        public void onInitDone() {}
        public void onLoadDone() {}
        public void onPagePress(int x, int y) {}
        public void onPositionTouched() {}
        public void onReadingProgressChanged(float progress, int paperPage) {}
        public void onScroll() {}
        public void onTocEntryChanged(TocEntry tocEntry) {}
        public void showFootNote(String url, String title, int x, int y) {}
        public void showPicture(String url, int x, int y, int width, int height) {}
    }

    // ── Constructor ───────────────────────────────────────────────────────────

    public ContentView(Context context, EPubBook book, ContentClient contentClient) {
        super(context);
        mContext       = context;
        mBook          = book;
        mContentClient = contentClient;
    }

    // ── Abstract interface ────────────────────────────────────────────────────

    /** Called once after construction; subclasses should inflate their view hierarchy here. */
    public abstract void init();

    /** Load the given spine-relative URL (may include a fragment). */
    public abstract void loadUrl(String url);

    // ── Default implementations (subclasses override as needed) ───────────────

    public boolean canOverScroll()                                                   { return true; }
    public AnnotationRenderer createAnnotationRenderer(AnnotationController controller) { return null; }
    public int    getPage()                                                          { return 0; }
    public int    getPageCount()                                                     { return 1; }
    public int    getPaperPage()                                                     { return -1; }
    public float  getReadingProgress()                                               { return 0.0f; }
    public int    getSupportedFeatures()                                             { return 0; }
    public String getTitle()                                                         { return null; }
    public void   hideShowcasedPicture()                                             {}
    public boolean isPositionInView(float position)                                  { return true; }
    public void   jumpToAnnotation(long annotationId)                                {}
    public void   jumpToSearchResult(int start, int end)                             {}
    public void   release()                                                          {}
    public void   restoreShowcasedPicture(BookView.PictureRestoreCallback callback)  {}
    public void   setFont(Font font)                                                 {}
    public void   setLineHeight(float height)                                        {}
    public void   setMargin(int margin)                                              {}
    public void   setPage(int page, boolean animate)                                 {}
    public void   setReadingProgress(float progress, boolean animate)                {}
    public void   setRendition(Rendition rendition)                                  {}
    public void   setTextAlign(int align)                                            {}
    public void   setTextSize(int size)                                              {}
    public void   setTheme(Theme theme)                                              {}

    /** @return {@code true} if {@code feature} is in the supported feature set. */
    public boolean supportsFeature(int feature) {
        return (getSupportedFeatures() & feature) == feature;
    }
}
