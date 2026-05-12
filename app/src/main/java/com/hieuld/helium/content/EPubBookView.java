package com.hieuld.helium.content;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.view.MotionEvent;

import com.hieuld.helium.R;
import com.hieuld.helium.annotations.AnnotationController;
import com.hieuld.helium.annotations.AnnotationRenderer;
import com.hieuld.helium.book.Book;
import com.hieuld.helium.book.EPubBook;
import com.hieuld.helium.book.EPubPageMap;
import com.hieuld.helium.book.Rendition;
import com.hieuld.helium.book.SearchResult;
import com.hieuld.helium.book.TocEntry;
import com.hieuld.helium.db.DatabaseProvider;
import com.hieuld.helium.fonts.Font;
import com.hieuld.helium.themes.Theme;
import com.hieuld.helium.util.AsyncHelper;
import com.hieuld.helium.util.Utils;
import com.hieuld.helium.widget.OverScrollView;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;

public class EPubBookView extends BookView implements OverScrollView.Delegate, BookView.FootNoteProvider {
    private static final String TAG = "EPubBookView";
    private AnnotationController mAnnotationController;
    private AsyncHelper mAsync;
    private EPubBook mBook;
    private ContentView.ContentClient mContentClient;
    private ContentView mContentView;
    private Context mContext;
    private int mCvLastTouchX;
    private int mCvLastTouchY;
    private int mFlow;
    private Font mFont;
    private boolean mInitialContentViewLoaded;
    private boolean mIsLoading;
    private float mLineHeight;
    private boolean mLoadAborted;
    private long mLoadStartTime;
    private int mMargin;
    private OverScrollView mOverScrollView;
    private EPubPageMap mPageMap;
    private boolean mRefreshing;
    private Rendition mRendition;
    private int mTextAlign;
    private int mTextSize;
    private Theme mTheme;
    private String mUrl;

    public EPubBookView(Context context, long j, Book book, BookView.Callbacks callbacks) {
        super(context, j, book, callbacks);
        this.mAsync = new AsyncHelper();
        this.mContentClient = new ContentClientImpl();
        this.mContext = context;
        this.mBook = (EPubBook) book;
        setFeatures(31);
        this.mOverScrollView = new OverScrollView(context, null);
        this.mOverScrollView.setDelegate(this);
        addView(this.mOverScrollView);
        loadPageMap(j, book);
    }

    private void loadPageMap(long j, Book book) {
        this.mAsync.run(new PageMapLoadTask(DatabaseProvider.getDatabase(this.mContext), j, book), ePubPageMap -> {
            if (ePubPageMap == null) {
                this.mLoadAborted = true;
                this.mCallbacks.onLoadFailed();
            } else {
                this.mPageMap = (EPubPageMap) ePubPageMap;
                if (this.mInitialContentViewLoaded) {
                    doLoadDone();
                }
            }
        });
    }

    public void loadUrl(String str, boolean z) {
        this.mOverScrollView.cancel();
        String strNormalizePath = Utils.normalizePath(str);
        String strStripHashFromUrl = Utils.stripHashFromUrl(strNormalizePath);
        if (this.mContentView != null) {
            if (z) {
                this.mCallbacks.pushBackStack();
            }
            if (strStripHashFromUrl.equals(this.mUrl) && !this.mRefreshing) {
                this.mContentView.loadUrl(strNormalizePath);
                return;
            }
        }
        this.mRendition = createRendition(strStripHashFromUrl, null);
        if (this.mContentView != null) {
            this.mOverScrollView.removeView(this.mContentView);
            this.mContentView.release();
        }
        if (this.mBook.containsFile(strStripHashFromUrl)) {
            String mimeType = this.mBook.getMimeType(strStripHashFromUrl);
            if (mimeType != null) {
                this.mContentView = createViewForMimeType(mimeType);
            } else {
                String extensionFromUrl = Utils.getExtensionFromUrl(strStripHashFromUrl);
                if (extensionFromUrl == null) {
                    extensionFromUrl = "html";
                }
                this.mContentView = createViewForMimeType(guessMimeFromExtension(extensionFromUrl));
            }
            if (this.mContentView == null) {
                this.mContentView = new ErrorContentView(this.mContext, this.mBook, this.mContentClient, R.layout.error_unsupported_content_view);
            }
        } else {
            this.mContentView = new ErrorContentView(this.mContext, this.mBook, this.mContentClient, R.layout.error_missing_content_view);
        }

        this.mContentView.setOnTouchListener((view, motionEvent) -> {
            if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                this.mCvLastTouchX = (int) motionEvent.getX();
                this.mCvLastTouchY = (int) motionEvent.getY();
            }
            return false;
        });

        this.mContentView.setOnClickListener(view -> this.mCallbacks.onPagePress(this.mCvLastTouchX, this.mCvLastTouchY));

        this.mContentView.setSoundEffectsEnabled(false);
        this.mOverScrollView.addView(this.mContentView);
        this.mContentView.init();
        this.mContentView.setRendition(this.mRendition);
        setFeatures(this.mContentView.getSupportedFeatures());

        if (this.mContentView.supportsFeature(FEATURE_CUSTOM_TEXT_SIZE)) {
            this.mContentView.setTextSize(this.mTextSize);
        }
        this.mContentView.setMargin(this.mMargin);
        if (!this.mRendition.isFixedLayout()) {
            this.mContentView.setTheme(this.mTheme);
        }

        TocEntry tocEntryFindTocEntry = this.mBook.findTocEntry(strNormalizePath);
        if (tocEntryFindTocEntry == null) {
            tocEntryFindTocEntry = this.mBook.findTocEntry(strStripHashFromUrl);
        }
        this.mCallbacks.onTocEntryChanged(tocEntryFindTocEntry);
        this.mUrl = strStripHashFromUrl;
        this.mLoadStartTime = System.currentTimeMillis();
        this.mIsLoading = true;
        Log.d(TAG, "Begin loading " + strStripHashFromUrl + " (" + strNormalizePath + ")");
        this.mCallbacks.onLoadStart(this.mRefreshing);
        this.mCallbacks.onFeaturesChanged();
        this.mRefreshing = false;
        this.mContentView.loadUrl(strNormalizePath);
    }

    private ContentView createViewForMimeType(String str) {
        if (str == null) {
            return null;
        }
        if (str.startsWith("image/")) {
            return new BitmapContentView(this.mContext, this.mBook, this.mContentClient);
        } else if (str.equals("application/xhtml+xml") || str.equals("text/html") || str.equals("application/xml") || str.equals("text/xml")) {
            return new HtmlContentView(this.mContext, this.mBook, this.mContentClient);
        }
        return null;
    }

    public static String guessMimeFromExtension(String str) {
        if (str == null) return null;
        switch (str.toLowerCase()) {
            case "bmp":
                return "image/bmp";
            case "htm":
            case "xml":
            case "html":
            case "xhtml":
                return "application/xhtml+xml";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            default:
                return null;
        }
    }

    private Rendition createRendition(String str, Rendition rendition) {
        Rendition rendition2 = new Rendition(Rendition.LAYOUT_REFLOW, Rendition.FLOW_PAGED);
        if (rendition == null) {
            rendition = this.mBook.getItemRendition(str);
        }
        rendition2.extend(rendition);
        int i = this.mFlow;
        if (i == 1) {
            rendition2.extend(Rendition.withFlowStyle(Rendition.FLOW_PAGED));
        } else if (i == 2) {
            rendition2.extend(Rendition.withFlowStyle(Rendition.FLOW_SCROLLED));
        }
        return rendition2;
    }

    @Override
    public String getPosition() {
        PositionObject positionObject = new PositionObject();
        positionObject.url = this.mUrl;
        positionObject.position = this.mContentView.getReadingProgress();
        return new Gson().toJson(positionObject);
    }

    @Override
    public void setPosition(String str) {
        if (str == null) {
            loadUrl(this.mBook.nextSpineItem(null), false);
            return;
        }
        PositionObject positionObject = new Gson().fromJson(str, PositionObject.class);
        loadUrl(positionObject.url, false);
        this.mContentView.setReadingProgress(positionObject.position, false);
        this.mCallbacks.onReadingProgressChanged();
    }

    @Override
    public boolean isAtPosition(String str) {
        if (this.mContentView == null) {
            return false;
        }
        PositionObject positionObject = new Gson().fromJson(str, PositionObject.class);
        return positionObject.url.equals(this.mUrl) && this.mContentView.isPositionInView(positionObject.position);
    }

    @Override
    public String getContextString() {
        return this.mUrl;
    }

    @Override
    public void restorePictureShowcase(BookView.PictureRestoreCallback pictureRestoreCallback) {
        this.mContentView.restoreShowcasedPicture(pictureRestoreCallback);
    }

    @Override
    public AnnotationRenderer createAnnotationRenderer(AnnotationController annotationController) {
        this.mAnnotationController = annotationController;
        return this.mContentView.createAnnotationRenderer(annotationController);
    }

    @Override
    public void setTextSize(int i) {
        this.mTextSize = i;
        if (this.mContentView != null) {
            this.mContentView.setTextSize(i);
        }
    }

    @Override
    public void setMargin(int i) {
        this.mMargin = i;
        if (this.mContentView != null) {
            this.mContentView.setMargin(i);
        }
    }

    @Override
    public void setFont(Font font) {
        this.mFont = font;
        if (this.mContentView != null) {
            this.mContentView.setFont(font);
        }
    }

    @Override
    public void setTheme(Theme theme) {
        this.mTheme = theme;
        if (this.mContentView != null) {
            this.mContentView.setTheme(theme);
        }
    }

    @Override
    public void setLineSpacing(float f) {
        this.mLineHeight = f;
        if (this.mContentView != null) {
            this.mContentView.setLineHeight(f);
        }
    }

    @Override
    public void setTextAlign(int i) {
        this.mTextAlign = i;
        if (this.mContentView != null) {
            this.mContentView.setTextAlign(i);
        }
    }

    @Override
    public boolean canOverScroll(int i) {
        if (this.mContentView == null || this.mIsLoading || !this.mContentView.canOverScroll() || this.mAnnotationController.isBusy()) {
            return false;
        }
        return i == -1 ? this.mBook.previousSpineItem(this.mUrl) != null : this.mBook.nextSpineItem(this.mUrl) != null;
    }

    @Override
    public void onOverScroll(int i) {
        String strNextSpineItem;
        float f;
        if (i == -1) {
            strNextSpineItem = this.mBook.previousSpineItem(this.mUrl);
            f = 1.0f;
        } else {
            strNextSpineItem = this.mBook.nextSpineItem(this.mUrl);
            f = 0.0f;
        }
        loadUrl(strNextSpineItem, false);
        this.mContentView.setReadingProgress(f, false);
        this.mCallbacks.onReadingProgressChanged();
        this.mCallbacks.onPositionTouched();
    }

    @Override
    public boolean isFixedLayout() {
        return this.mRendition != null && this.mRendition.isFixedLayout();
    }

    @Override
    public int getPage() {
        if (this.mPageMap == null) {
            return 0;
        }
        EPubPageMap.Item item = this.mPageMap.items.get(this.mUrl);
        if (item == null) {
            return -1;
        }
        return item.pageStart + Math.round((item.pageCount - 1) * this.mContentView.getReadingProgress());
    }

    @Override
    public int getPageCount() {
        if (this.mPageMap == null) {
            return 1;
        }
        return this.mPageMap.totalPageCount;
    }

    @Override
    public void setPage(int i, boolean z) {
        float f;
        String next;
        Iterator<String> it = this.mPageMap.items.keySet().iterator();
        while (true) {
            f = 0.0f;
            if (!it.hasNext()) {
                next = null;
                break;
            }
            next = it.next();
            EPubPageMap.Item item = this.mPageMap.items.get(next);
            if (i >= item.pageStart && i - item.pageStart < item.pageCount) {
                if (item.pageCount > 0) {
                    f = (float)(i - item.pageStart) / (item.pageCount - 1);
                }
                break;
            }
        }
        if (next != null) {
            loadUrl(next, false);
            this.mContentView.setReadingProgress(f, false);
        }
    }

    @Override
    public int getLegacyPage() {
        if (this.mIsLoading) {
            return -1;
        }
        return this.mContentView.getPage();
    }

    @Override
    public int getLegacyPageCount() {
        return this.mContentView.getPageCount();
    }

    @Override
    public void setLegacyPage(int i, boolean z) {
        this.mContentView.setPage(i, z);
    }

    @Override
    public void advancePage(int i) {
        if (this.mContentView == null || this.mIsLoading) {
            return;
        }
        int page = this.mContentView.getPage();
        if (page != (i > 0 ? this.mContentView.getPageCount() - 1 : 0)) {
            this.mContentView.setPage(page + i, true);
            return;
        }
        if (this.mContentView.canOverScroll()) {
            if (i == -1 && this.mBook.previousSpineItem(this.mUrl) != null) {
                this.mOverScrollView.doOverScroll(-1);
            } else if (i == 1 && this.mBook.nextSpineItem(this.mUrl) != null) {
                this.mOverScrollView.doOverScroll(1);
            }
        }
    }

    @Override
    public int getPaperPage() {
        return this.mContentView.getPaperPage();
    }

    @Override
    public int getFlow() {
        return this.mRendition.flowStyle != Rendition.FLOW_SCROLLED ? FLOW_PAGING : FLOW_SCROLLING;
    }

    @Override
    public void setPreferredFlow(int i) {
        this.mFlow = i;
        if (this.mContentView != null) {
            String str = this.mUrl;
            float readingProgress = this.mContentView.getReadingProgress();
            this.mRefreshing = true;
            loadUrl(str, false);
            this.mContentView.setReadingProgress(readingProgress, false);
        }
    }

    @Override
    public void setScrollPosition(float f, boolean z) {
        this.mContentView.setReadingProgress(f, z);
    }

    @Override
    public float getScrollPosition() {
        return this.mContentView.getReadingProgress();
    }

    @Override
    public void setNonContentBackgroundColor(int i) {
        this.mOverScrollView.setBackgroundColor(i);
    }

    @Override
    public void jumpToToc(String str) {
        loadUrl(str, true);
        this.mCallbacks.onReadingProgressChanged();
        this.mCallbacks.onPositionTouched();
    }

    @Override
    public void jumpToPaperPage(int i) {
        Map<Integer, String> paperPageMap = this.mBook.getPaperPageMap();
        if (!paperPageMap.containsKey(i)) {
            Log.e(TAG, "jumpToPaperPage called with invalid page number: " + i);
        } else {
            loadUrl(paperPageMap.get(i), true);
            this.mCallbacks.onReadingProgressChanged();
            this.mCallbacks.onPositionTouched();
        }
    }

    @Override
    public void jumpToAnnotation(long j, String str, String str2) {
        if (!str.equals(this.mUrl)) {
            loadUrl(str, false);
        }
        this.mContentView.jumpToAnnotation(j);
    }

    @Override
    public void jumpToSearchResult(SearchResult searchResult) {
        if (searchResult.type == 1) {
            loadUrl(searchResult.position, false);
            return;
        }
        JsonObject asJsonObject = new JsonParser().parse(searchResult.position).getAsJsonObject();
        String asString = asJsonObject.get("filename").getAsString();
        int asInt = asJsonObject.get("start").getAsInt();
        int asInt2 = asJsonObject.get("end").getAsInt();
        if (!asString.equals(this.mUrl)) {
            loadUrl(asString, false);
        }
        this.mContentView.jumpToSearchResult(asInt, asInt2);
    }

    @Override
    public void release() {
        this.mOverScrollView.cancel();
        if (this.mContentView != null) {
            this.mOverScrollView.removeAllViews();
            this.mContentView.release();
        }
        this.mAsync.release();
    }

    @Override
    public String getTitle() {
        return this.mContentView.getTitle();
    }

    public static String convertOldPosition(String str, float f) {
        PositionObject positionObject = new PositionObject();
        positionObject.url = str;
        positionObject.position = f;
        return new Gson().toJson(positionObject);
    }

    @Override
    public String getFootNoteBaseUrl() {
        return "file:///" + this.mUrl;
    }

    @Override
    public InputStream getFootNoteResource(String str) {
        return this.mBook.getInputStreamForFile(str);
    }

    @Override
    public String getFootNoteResourceMimeType(String str) {
        return this.mBook.getMimeType(str);
    }

    @Override
    public void onFootNoteNavigation(String str) {
        if (str.startsWith("file:///")) {
            str = str.substring(8);
        }
        loadUrl(str, true);
    }

    public void doLoadDone() {
        this.mIsLoading = false;
        this.mOverScrollView.setVerticalRestricted(getFlow() == FLOW_SCROLLING);
        this.mCallbacks.onLoadDone();
    }

    private class ContentClientImpl extends ContentView.ContentClient {
        @Override
        public void showFootNote(String str, String str2, int i, int i2) {
            EPubBookView.this.mCallbacks.showFootNote(str, str2, i, i2);
        }

        @Override
        public void showPicture(final String str, final int i, final int i2, final int i3, final int i4) {
            InputStream inputStreamForFile = EPubBookView.this.mBook.getInputStreamForFile(str);
            if (inputStreamForFile == null) {
                return;
            }
            EPubBookView.this.mAsync.run(new PictureLoadTask(inputStreamForFile), bitmap -> {
                if (bitmap != null) {
                    EPubBookView.this.mContentView.hideShowcasedPicture();
                    EPubBookView.this.mCallbacks.showPicture((Bitmap) bitmap, i, i2, i3, i4);
                } else {
                    Log.e(EPubBookView.TAG, "Failed to decode photo " + str);
                }
            });
        }

        @Override
        public void loadUrl(String str, boolean z) {
            EPubBookView.this.loadUrl(str, z);
        }

        @Override
        public void loadExternalUrl(String str) {
            EPubBookView.this.mCallbacks.loadExternalUrl(str);
        }

        @Override
        public void onInitDone() {
            EPubBookView.this.mCallbacks.onInitDone();
        }

        @Override
        public void onLoadDone() {
            if (EPubBookView.this.mLoadAborted) {
                return;
            }
            EPubBookView.this.mInitialContentViewLoaded = true;
            if (EPubBookView.this.mPageMap != null) {
                EPubBookView.this.doLoadDone();
            }
        }

        @Override
        public void onReadingProgressChanged(float f, int i) {
            EPubBookView.this.mCallbacks.onReadingProgressChanged();
        }

        @Override
        public void onPositionTouched() {
            EPubBookView.this.mCallbacks.onPositionTouched();
        }

        @Override
        public void onTocEntryChanged(TocEntry tocEntry) {
            EPubBookView.this.mCallbacks.onTocEntryChanged(tocEntry);
        }

        @Override
        public void onPagePress(int i, int i2) {
            EPubBookView.this.mCallbacks.onPagePress(i, i2);
        }

        @Override
        public void onScroll() {
            EPubBookView.this.mCallbacks.onScroll();
        }
    }

    private static class PositionObject {
        float position;
        String url;

        PositionObject() {
        }
    }

    private static class PictureLoadTask extends AsyncHelper.Task<Bitmap> {
        private InputStream mIs;

        public PictureLoadTask(InputStream inputStream) {
            this.mIs = inputStream;
        }

        @Override
        public Bitmap run() {
            try {
                return BitmapFactory.decodeStream(this.mIs);
            } finally {
                try {
                    this.mIs.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static class PageMapLoadTask extends AsyncHelper.Task<EPubPageMap> {
        private Book mBook;
        private long mBookId;
        private SQLiteDatabase mDatabase;

        public PageMapLoadTask(SQLiteDatabase sQLiteDatabase, long j, Book book) {
            this.mDatabase = sQLiteDatabase;
            this.mBookId = j;
            this.mBook = book;
        }

        @Override
        public EPubPageMap run() {
            EPubPageMap fromCache = EPubPageMap.readFromCache(this.mBookId, this.mDatabase);
            if (fromCache == null) {
                fromCache = EPubPageMap.generate((EPubBook) this.mBook);
                if (fromCache == null) {
                    return null;
                }
                EPubPageMap.writeToCache(fromCache, this.mBookId, this.mDatabase);
            }
            return fromCache;
        }
    }
}