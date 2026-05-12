package com.hieuld.helium.content;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ActionMode;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.JsPromptResult;
import android.webkit.MimeTypeMap;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.hieuld.helium.SettingsHtmlActivity;
import com.hieuld.helium.annotations.SelectionActionModeCallbackDummy;
import com.hieuld.helium.annotations.SelectionActionModeDummy;
import com.hieuld.helium.book.EPubBook;
import com.hieuld.helium.book.Rendition;
import com.hieuld.helium.book.TocEntry;
import com.hieuld.helium.fonts.Font;
import com.hieuld.helium.themes.Theme;
import com.hieuld.helium.util.Utils;
import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class HtmlContentWebView extends WebView {
    private static final String TAG = "HtmlContentWebView";
    private static final Gson GSON = new Gson();

    private final Context mContext;
    private final EPubBook mBook;
    private final ContentView.ContentClient mContentClient;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final JavaScriptBridge mJsBridge;

    private final List<AnchorPositionItem> mAnchorPositions = new ArrayList<>();
    private final Map<Integer, Integer> mPaperPagePositionMap = new HashMap<>();

    private ObjectAnimator mScrollXAnimator;
    private ObjectAnimator mScrollYAnimator;

    private boolean mBookReady;
    private boolean mDestroyed;
    private boolean mDisplaySettingsInjected;
    private boolean mPagedHorizontally;

    private int mPage;
    private int mPageCount = 1;
    private int mPageSnapThreshold;
    private String mUrl;

    private String mPendingAnchor;
    private long mPendingAnnotation;
    private float mPendingReadingProgress = -1.0f;
    private int mPendingSearchResultStart = -1;
    private int mPendingSearchResultEnd = -1;

    private int mLastTouchX;
    private int mLastTouchY;
    private float mTouchSlop;

    private float mContentScaleX;
    private float mContentScaleY;
    private float mDensityFixedWidth;
    private float mDensityFixedHeight;
    private int mFixedViewportWidth;
    private int mFixedViewportHeight;

    private int mTextSize = 100;
    private int mMargin;
    private float mLineSpacing = 1.3f;
    private int mTextAlign;
    private Font mFont;
    private Theme mTheme;
    private Rendition mRendition;
    private TocEntry mCurrentTocEntry;
    private int mCurrentTocEntryPosition;
    private int mNextTocEntryPosition;
    private BookView.PictureRestoreCallback mPictureRestoreCallback;

    private final Runnable mOnLoadDoneRunnable;

    public HtmlContentWebView(Context context, ContentView.ContentClient contentClient, EPubBook book) {
        super(context);
        this.mContext = context;
        this.mContentClient = contentClient;
        this.mBook = book;

        this.mOnLoadDoneRunnable = () -> {
            mBookReady = true;
            if (mPendingReadingProgress != -1.0f) {
                setReadingProgress(mPendingReadingProgress, false);
            } else {
                setPage(mPage, false);
            }
            if (mPendingAnchor != null) {
                executeJavascript("HeliumJs.jumpToAnchor('" + mPendingAnchor + "')");
            }
            if (mPendingAnnotation != 0) {
                executeJavascript("HeliumJs.jumpToAnnotation(" + mPendingAnnotation + ")");
            }
            if (mPendingSearchResultStart != -1) {
                executeJavascript("HeliumJs.jumpToSearchResult(" + mPendingSearchResultStart + "," + mPendingSearchResultEnd + ")");
            }
            updateCurrentTocEntryMaybe();

            if (mContentClient != null) {
                mContentClient.onLoadDone();
            }
            Log.d(TAG, "Finished loading");
        };

        setWebViewClient(new Client());
        setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage msg) {
                String log = "[CONSOLE] " + msg.message() + " (source: " + msg.sourceId() + ":" + msg.lineNumber() + ")";
                switch (msg.messageLevel()) {
                    case ERROR: Log.e(TAG, log); break;
                    case WARNING: Log.w(TAG, log); break;
                    case DEBUG: Log.d(TAG, log); break;
                    default: Log.i(TAG, log); break;
                }
                return true;
            }

            @Override
            public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
                String response = mJsBridge.onPrompt(message);
                if (response != null) {
                    result.confirm(response);
                    return true;
                }
                return super.onJsPrompt(view, url, message, defaultValue, result);
            }
        });

        this.mScrollXAnimator = ObjectAnimator.ofInt(this, "scrollX", 0, 0);
        this.mScrollXAnimator.setDuration(200L);
        this.mScrollYAnimator = ObjectAnimator.ofInt(this, "scrollY", 0, 0);
        this.mScrollYAnimator.setDuration(200L);

        this.mPageSnapThreshold = Utils.dpToPx(this.mContext, 50);
        this.mTouchSlop = ViewConfiguration.get(this.mContext).getScaledTouchSlop();

        setOverScrollMode(OVER_SCROLL_NEVER);
        setHorizontalScrollBarEnabled(false);
        setVerticalScrollBarEnabled(false);

        WebSettings settings = getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowFileAccess(true);

        this.mJsBridge = new JavaScriptBridge(this);
        this.mJsBridge.addObject("HeliumApp", new JsInterface());
    }

    @Override
    public void loadUrl(String url) {
        String normalizedPath = Utils.normalizePath(url);
        if (normalizedPath.startsWith("#")) {
            normalizedPath = this.mUrl + normalizedPath;
        }

        String anchor = null;
        int hashIndex = normalizedPath.indexOf("#");
        if (hashIndex > -1) {
            anchor = normalizedPath.substring(hashIndex);
            normalizedPath = normalizedPath.substring(0, hashIndex);
        }

        if (normalizedPath.equals(this.mUrl)) {
            if (anchor != null) {
                if (this.mBookReady) {
                    executeJavascript("HeliumJs.jumpToAnchor('" + anchor + "')");
                } else {
                    this.mPendingAnchor = anchor;
                }
            } else {
                setPage(0, false);
            }
            return;
        }

        this.mBookReady = false;
        this.mPage = 0;
        this.mPageCount = 1;
        this.mUrl = normalizedPath;
        this.mPendingAnchor = anchor;
        this.mPendingReadingProgress = -1.0f;
        this.mPendingAnnotation = 0L;
        this.mPendingSearchResultStart = -1;
        this.mPendingSearchResultEnd = -1;
        this.mDisplaySettingsInjected = false;

        this.mHandler.removeCallbacks(this.mOnLoadDoneRunnable);

        super.loadUrl("about:blank");
        this.mBookReady = false;
        setScrollX(0);

        getSettings().setUseWideViewPort(false);
        getSettings().setLoadWithOverviewMode(false);
        updateFixedLayout();
        requestLayout();

        super.loadUrl("file:///" + normalizedPath);
        Log.d(TAG, "Started loading");
    }

    public void setTextSize(int size) {
        this.mTextSize = size;
        if (this.mRendition != null && this.mRendition.layoutStyle == Rendition.LAYOUT_FIXED) {
            return;
        }
        getSettings().setTextZoom(size);
        if (this.mUrl != null && this.mDisplaySettingsInjected) {
            executeJavascript("HeliumJs.reflowIfNecessary()");
        }
    }

    private void updateFixedLayout() {
        if (this.mRendition != null && this.mRendition.layoutStyle == Rendition.LAYOUT_FIXED) {
            getSettings().setTextZoom(100);
        } else {
            getSettings().setTextZoom(this.mTextSize);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        if (this.mRendition == null || this.mRendition.layoutStyle != Rendition.LAYOUT_FIXED || this.mFixedViewportWidth <= 0 || this.mFixedViewportHeight <= 0) {
            return;
        }

        int measuredWidth = getMeasuredWidth();
        int measuredHeight = getMeasuredHeight();
        float ratio = (float) this.mFixedViewportHeight / this.mFixedViewportWidth;

        int targetWidth, targetHeight;
        if (this.mFixedViewportHeight >= this.mFixedViewportWidth) {
            targetHeight = Math.round(measuredWidth * ratio);
            if (targetHeight > measuredHeight) {
                targetWidth = Math.round(measuredHeight / ratio);
                targetHeight = measuredHeight;
            } else {
                targetWidth = measuredWidth;
            }
        } else {
            targetHeight = Math.round(measuredWidth * ratio);
            if (targetHeight > measuredHeight) {
                targetWidth = Math.round(measuredHeight / ratio);
                targetHeight = measuredHeight;
            } else {
                targetWidth = measuredWidth;
            }
        }

        setMeasuredDimension(targetWidth, targetHeight);
        setInitialScale(1);
        setInitialScale(0);
    }

    public void executeJavascript(String script) {
        evaluateJavascript(script, null);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();
        if (action == MotionEvent.ACTION_DOWN) {
            this.mLastTouchX = (int) event.getX();
            this.mLastTouchY = (int) event.getY();
        } else if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) &&
                this.mRendition != null &&
                this.mRendition.layoutStyle == Rendition.LAYOUT_REFLOW &&
                this.mRendition.flowStyle == Rendition.FLOW_PAGED) {

            boolean isAnimating = this.mScrollXAnimator.isRunning() || this.mScrollYAnimator.isRunning();
            int currentScroll = this.mPagedHorizontally ? getScrollX() : getScrollY();
            int targetScroll = Math.round(this.mPage * (this.mPagedHorizontally ? this.mDensityFixedWidth : this.mDensityFixedHeight));

            boolean hasMoved = Math.abs(event.getX() - this.mLastTouchX) > this.mTouchSlop ||
                    Math.abs(event.getY() - this.mLastTouchY) > this.mTouchSlop;

            if ((!isAnimating && currentScroll != targetScroll) || hasMoved) {
                MotionEvent cancelEvent = MotionEvent.obtain(event);
                cancelEvent.setAction(MotionEvent.ACTION_CANCEL);
                super.onTouchEvent(cancelEvent);

                if (!isAnimating) {
                    snapScroll();
                }
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    private void snapScroll() {
        int currentScroll = this.mPagedHorizontally ? getScrollX() : getScrollY();
        int expectedScroll = Math.round(this.mPage * (this.mPagedHorizontally ? this.mDensityFixedWidth : this.mDensityFixedHeight));
        int targetPage = this.mPage;

        if (currentScroll > expectedScroll + this.mPageSnapThreshold) {
            targetPage++;
        } else if (currentScroll < expectedScroll - this.mPageSnapThreshold) {
            targetPage--;
        }

        setPage(Math.max(0, Math.min(targetPage, this.mPageCount - 1)));
    }

    public void setPage(int page, boolean animate) {
        if (!this.mBookReady) {
            this.mPage = page;
            return;
        }

        int targetScroll = Math.round(page * (this.mPagedHorizontally ? this.mDensityFixedWidth : this.mDensityFixedHeight));

        if (animate) {
            if (this.mPagedHorizontally) {
                this.mScrollXAnimator.cancel();
                this.mScrollXAnimator.setIntValues(getScrollX(), targetScroll);
                this.mScrollXAnimator.start();
            } else {
                this.mScrollYAnimator.cancel();
                this.mScrollYAnimator.setIntValues(getScrollY(), targetScroll);
                this.mScrollYAnimator.start();
            }
        } else {
            if (this.mPagedHorizontally) {
                this.mScrollXAnimator.cancel();
                setScrollX(targetScroll);
            } else {
                this.mScrollYAnimator.cancel();
                setScrollY(targetScroll);
            }
        }

        if (this.mPage != page) {
            this.mPage = page;
            float progress = getReadingProgress();
            updateCurrentTocEntryMaybe();
            if (this.mContentClient != null) {
                this.mContentClient.onReadingProgressChanged(progress, getPaperPage());
                this.mContentClient.onPositionTouched();
            }
        }
    }

    public void setPage(int page) {
        setPage(page, true);
    }

    public void setReadingProgress(float progress, boolean animate) {
        if (!this.mBookReady) {
            this.mPendingReadingProgress = progress;
            return;
        }

        if (this.mRendition != null && this.mRendition.flowStyle == Rendition.FLOW_PAGED) {
            setPage(Math.round(progress * (this.mPageCount - 1)), animate);
        } else if (this.mRendition != null && this.mRendition.flowStyle == Rendition.FLOW_SCROLLED) {
            int targetScroll = Math.round((computeVerticalScrollRange() - getHeight()) * progress);
            if (animate) {
                this.mScrollYAnimator.cancel();
                this.mScrollYAnimator.setIntValues(getScrollY(), targetScroll);
                this.mScrollYAnimator.start();
            } else {
                scrollTo(0, targetScroll);
            }
            if (this.mContentClient != null) {
                this.mContentClient.onReadingProgressChanged(progress, getPaperPage());
                this.mContentClient.onPositionTouched();
            }
        }
    }

    public float getReadingProgress() {
        if (!this.mBookReady && this.mPendingReadingProgress > -1.0f) {
            return this.mPendingReadingProgress;
        }

        if (this.mRendition != null && this.mRendition.flowStyle == Rendition.FLOW_PAGED) {
            if (this.mPageCount <= 1) return 0.0f;
            return (float) this.mPage / (this.mPageCount - 1);
        } else {
            int maxScroll = computeVerticalScrollRange() - getHeight();
            if (maxScroll <= 0) return 0.0f;
            return (float) getScrollY() / maxScroll;
        }
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        if (this.mContentClient != null) {
            this.mContentClient.onScroll();
        }

        if (this.mRendition == null || this.mRendition.flowStyle != Rendition.FLOW_SCROLLED || this.mScrollYAnimator.isRunning()) {
            return;
        }

        updateCurrentTocEntryMaybe();
        if (this.mContentClient != null) {
            this.mContentClient.onReadingProgressChanged(getReadingProgress(), getPaperPage());
            this.mContentClient.onPositionTouched();
        }
    }

    public int getPaperPage() {
        if (this.mRendition == null) return -1;
        int currentOffset = (this.mRendition.flowStyle == Rendition.FLOW_PAGED) ? this.mPage : getScrollY();
        int viewportAdjustment = (this.mRendition.flowStyle == Rendition.FLOW_PAGED) ? 0 : getHeight();

        int highestPage = -1;
        for (Map.Entry<Integer, Integer> entry : this.mPaperPagePositionMap.entrySet()) {
            int pageNum = entry.getKey();
            int position = entry.getValue() - viewportAdjustment;

            if (currentOffset >= position && pageNum > highestPage) {
                highestPage = pageNum;
            }
        }
        return highestPage;
    }

    public int getPage() { return this.mPage; }

    public int getPageCount() { return this.mPageCount; }

    public JavaScriptBridge getJsBridge() {
        return this.mJsBridge;
    }

    public void updateCurrentTocEntryMaybe() {
        if (this.mRendition == null) return;
        int currentOffset = (this.mRendition.flowStyle == Rendition.FLOW_PAGED)
                ? this.mPage
                : getScrollY() + (getHeight() / 2);

        if (currentOffset < this.mCurrentTocEntryPosition ||
                (this.mNextTocEntryPosition != -1 && currentOffset >= this.mNextTocEntryPosition)) {

            TocEntry bestEntry = null;
            int bestPosition = -1;

            for (AnchorPositionItem item : this.mAnchorPositions) {
                if (item.position <= currentOffset && bestPosition <= item.position) {
                    bestEntry = item.entry;
                    bestPosition = item.position;
                }
            }

            int nextPosition = -1;
            for (AnchorPositionItem item : this.mAnchorPositions) {
                if ((nextPosition == -1 || item.position < nextPosition) && item.position > bestPosition) {
                    nextPosition = item.position;
                }
            }

            this.mCurrentTocEntryPosition = bestPosition;
            this.mNextTocEntryPosition = nextPosition;

            if (bestEntry == null && this.mBook != null) {
                bestEntry = this.mBook.findTocEntry(this.mUrl);
            }

            if (this.mCurrentTocEntry != bestEntry && this.mContentClient != null) {
                this.mCurrentTocEntry = bestEntry;
                this.mContentClient.onTocEntryChanged(bestEntry);
            }
        }
    }

    public void hideShowcasedImage() {
        executeJavascript("HeliumJs.hideShowcasedImage()");
    }

    public void restoreShowcasedImage(BookView.PictureRestoreCallback callback) {
        this.mPictureRestoreCallback = callback;
        String js = callback != null ?
                "HeliumJs.restoreShowcasedImage();HeliumApp.onRestoreShowcasedImageDone();" :
                "HeliumJs.restoreShowcasedImage()";
        executeJavascript(js);
    }

    public int getViewXScaled(float pageX) { return Math.round(pageX / this.mContentScaleX); }

    public int getViewYScaled(float pageY) { return Math.round(pageY / this.mContentScaleY); }

    private InputStream prepareContentStream(InputStream is) {
        if (is == null) return null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }

            String html = sb.toString();
            int insertIndex = html.indexOf("<head>");
            if (insertIndex > -1) {
                insertIndex += 6;
            } else {
                insertIndex = html.indexOf("<head");
                if (insertIndex > -1) insertIndex = html.indexOf(">", insertIndex);
                insertIndex = insertIndex > -1 ? insertIndex + 1 : 0;
            }

            StringBuilder scripts = new StringBuilder();
            scripts.append("<script type='text/javascript' src='file:///android_asset/js/lib/rangy-core.js' defer=''></script>")
                    .append("<script type='text/javascript' src='file:///android_asset/js/lib/rangy-classapplier.js' defer=''></script>")
                    .append("<script type='text/javascript' src='file:///android_asset/js/lib/rangy-highlighter.js' defer=''></script>")
                    .append("<script type='text/javascript' src='file:///android_asset/js/lib/rangy-textrange.js' defer=''></script>")
                    .append("<script type='text/javascript' src='file:///android_asset/js/lib/webfontloader.js'></script>")
                    .append("<script type='text/javascript' src='file:///android_asset/js/epub.js'></script>")
                    .append("<script type='text/javascript' src='file:///android_asset/js/annotations.js' defer=''></script>")
                    .append("<script type='text/javascript' src='file:///android_asset/js/themes.js'></script>");

            if (this.mRendition != null && this.mRendition.layoutStyle == Rendition.LAYOUT_REFLOW) {
                scripts.append("<link href='file:///android_asset/css/default.css' rel='stylesheet' type='text/css' data-exclude-from-footnote='true'></link>");
                if (this.mRendition.flowStyle == Rendition.FLOW_SCROLLED) {
                    scripts.append("<link href='file:///android_asset/css/scrolled.css' rel='stylesheet' type='text/css'></link>");
                }
            }

            List<String> tocAnchors = new ArrayList<>();
            if (this.mBook != null) {
                for (TocEntry entry : this.mBook.getTocEntries()) {
                    if (entry.url.startsWith(this.mUrl + "#")) {
                        tocAnchors.add(entry.url.substring(entry.url.indexOf("#")));
                    }
                }
            }

            Map<String, String> paperMap = new HashMap<>();
            if (this.mBook != null) {
                Map<Integer, String> bookPaperMap = this.mBook.getPaperPageMap();
                if (bookPaperMap != null) {
                    for (Map.Entry<Integer, String> entry : bookPaperMap.entrySet()) {
                        if (entry.getValue().startsWith(this.mUrl + "#")) {
                            paperMap.put(String.valueOf(entry.getKey()), entry.getValue().substring(entry.getValue().indexOf("#")));
                        }
                    }
                }
            }

            scripts.append("<script type='text/javascript'>")
                    .append("HeliumJs.setPageProperties({")
                    .append("layoutStyle: ").append(this.mRendition != null ? this.mRendition.layoutStyle : Rendition.LAYOUT_REFLOW).append(",")
                    .append("flowStyle: ").append(this.mRendition != null ? this.mRendition.flowStyle : Rendition.FLOW_PAGED).append(",")
                    .append("tocAnchorList: ").append(new JSONArray(tocAnchors)).append(",")
                    .append("paperPageToAnchorMap: ").append(new JSONObject(paperMap)).append(",")
                    .append("apiLevel: ").append(Build.VERSION.SDK_INT)
                    .append("});");

            if (this.mRendition != null && this.mRendition.layoutStyle == Rendition.LAYOUT_REFLOW) {
                scripts.append("document.addEventListener('DOMContentLoaded', function() {")
                        .append("HeliumJs.setMargin(").append(this.mMargin).append(");")
                        .append("HeliumJs.setFont(").append(GSON.toJson(this.mFont)).append(");")
                        .append(getThemeSetCode(this.mTheme))
                        .append("HeliumJs.setLineSpacing(").append(this.mLineSpacing).append(");")
                        .append("HeliumJs.setTextAlign(").append(this.mTextAlign).append(");")
                        .append("});");
            }
            scripts.append("</script><style type='text/css' id='__HeliumThemeStyle'></style>");

            this.mDisplaySettingsInjected = true;
            String finalHtml = html.substring(0, insertIndex) + scripts.toString() + html.substring(insertIndex);
            return new ByteArrayInputStream(finalHtml.getBytes("UTF-8"));

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public WebResourceResponse getResponseForUrl(String url) {
        Uri uri = Uri.parse(url);
        if (uri.getScheme() != null && !SettingsHtmlActivity.EXTRA_FILE.equals(uri.getScheme())) {
            return null;
        }

        String path = uri.getPath();
        if (path != null && path.startsWith("/")) path = path.substring(1);

        InputStream is = this.mBook.getInputStreamForFile(path);
        String mimeType = this.mBook.getMimeType(path);

        if (mimeType == null) {
            String ext = Utils.getExtensionFromUrl(url);
            if (ext != null) mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        }

        if (path != null && path.equals(this.mUrl)) {
            is = prepareContentStream(is);
        }
        return new WebResourceResponse(mimeType, "UTF-8", is);
    }

    public void setRendition(Rendition rendition) {
        this.mRendition = rendition;
    }

    @Override
    public ActionMode startActionMode(ActionMode.Callback callback, int type) {
        super.startActionMode(new SelectionActionModeCallbackDummy(callback), type);
        return new SelectionActionModeDummy();
    }

    @Override
    public ActionMode startActionMode(ActionMode.Callback callback) {
        super.startActionMode(new SelectionActionModeCallbackDummy(callback));
        return new SelectionActionModeDummy();
    }

    @Override
    public void destroy() {
        if (this.mScrollXAnimator != null && this.mScrollXAnimator.isRunning()) this.mScrollXAnimator.cancel();
        if (this.mScrollYAnimator != null && this.mScrollYAnimator.isRunning()) this.mScrollYAnimator.cancel();

        this.mDestroyed = true;
        if (this.mHandler != null) {
            this.mHandler.removeCallbacksAndMessages(null);
        }
        super.destroy();
    }

    public void setMargin(int margin) {
        this.mMargin = margin;
        if (this.mUrl != null && this.mDisplaySettingsInjected) {
            executeJavascript("HeliumJs.setMargin(" + margin + ");");
        }
    }

    public void setFont(Font font) {
        this.mFont = font;
        if (this.mUrl != null && this.mDisplaySettingsInjected) {
            executeJavascript("HeliumJs.setFont(" + GSON.toJson(font) + ");");
        }
    }

    public void setTheme(Theme theme) {
        if (this.mRendition != null && this.mRendition.isFixedLayout()) return;
        this.mTheme = theme;
        if (this.mUrl != null && this.mDisplaySettingsInjected) {
            executeJavascript(getThemeSetCode(theme));
        }
    }

    public void setLineSpacing(float spacing) {
        this.mLineSpacing = spacing;
        if (this.mUrl != null && this.mDisplaySettingsInjected) {
            executeJavascript("HeliumJs.setLineSpacing(" + spacing + ");");
        }
    }

    public void setTextAlign(int align) {
        this.mTextAlign = align;
        if (this.mUrl != null && this.mDisplaySettingsInjected) {
            executeJavascript("HeliumJs.setTextAlign(" + align + ");");
        }
    }

    private String getThemeSetCode(Theme theme) {
        return theme == null ? "HeliumThemes.set(null);" : "HeliumThemes.set(" + GSON.toJson(theme) + ");";
    }

    public int getSupportedFeatures() {
        return (this.mRendition != null && this.mRendition.isFixedLayout()) ? 0 : 31;
    }

    public boolean isPositionInView(float progress) {
        if (!this.mBookReady || this.mRendition == null) return false;
        if (this.mRendition.layoutStyle != Rendition.LAYOUT_REFLOW) return true;

        if (this.mRendition.flowStyle == Rendition.FLOW_PAGED) {
            return Math.round(progress * (this.mPageCount - 1)) == this.mPage;
        } else {
            int viewport = getHeight();
            float targetPixel = (computeVerticalScrollRange() - viewport) * progress;
            float currentScroll = getScrollY();
            return currentScroll <= targetPixel && currentScroll + viewport > targetPixel;
        }
    }

    public void jumpToAnnotation(long annotationId) {
        if (this.mBookReady) {
            executeJavascript("HeliumJs.jumpToAnnotation(" + annotationId + ")");
        } else {
            this.mPendingAnnotation = annotationId;
        }
    }

    public void jumpToSearchResult(int start, int end) {
        if (this.mBookReady) {
            executeJavascript("HeliumJs.jumpToSearchResult(" + start + "," + end + ")");
        } else {
            this.mPendingSearchResultStart = start;
            this.mPendingSearchResultEnd = end;
        }
    }

    public void handleLinkNavigation(String url) {
        Uri uri = Uri.parse(url);
        if (SettingsHtmlActivity.EXTRA_FILE.equals(uri.getScheme())) {
            String path = uri.getPath();
            if (path == null || path.length() <= 1) return;

            String fragment = uri.getFragment();
            String fullPath = path.substring(1) + (fragment != null ? "#" + fragment : "");
            if (this.mContentClient != null) {
                this.mContentClient.loadUrl(fullPath, true);
            }
        } else {
            if (this.mContentClient != null) {
                this.mContentClient.loadExternalUrl(url);
            }
        }
    }

    private class Client extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            WebResourceResponse response = getResponseForUrl(url);
            return response != null ? response : super.shouldInterceptRequest(view, url);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (url.equals(mUrl)) return false;
            handleLinkNavigation(url);
            return true;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            if (!request.isForMainFrame()) {
                return super.shouldOverrideUrlLoading(view, request.getUrl().toString());
            }
            return super.shouldOverrideUrlLoading(view, request);
        }
    }

    private class JsInterface {
        @JavascriptInterface
        public void setFixedViewport(final int width, final int height) {
            mHandler.post(() -> {
                mFixedViewportWidth = width;
                mFixedViewportHeight = height;
                requestLayout();
                WebSettings settings = getSettings();
                settings.setUseWideViewPort(true);
                settings.setLoadWithOverviewMode(true);
            });
        }

        @JavascriptInterface
        public void onBookReady() {
            mHandler.post(() -> {
                if (mDestroyed) return;
                if (mContentClient != null) {
                    mContentClient.onInitDone();
                }
                Log.d(TAG, "Init done.");

                if (Build.VERSION.SDK_INT >= 23) {
                    postVisualStateCallback(0L, new VisualStateCallback() {
                        @Override
                        public void onComplete(long requestId) {
                            if (!mDestroyed && mOnLoadDoneRunnable != null) {
                                mOnLoadDoneRunnable.run();
                            }
                        }
                    });
                } else {
                    if (!mDestroyed && mOnLoadDoneRunnable != null) {
                        mOnLoadDoneRunnable.run();
                    }
                }
            });
        }

        @JavascriptInterface
        public void onPagingSetup(float width, float height, final int pages, final boolean horizontal) {
            mHandler.post(() -> {
                if (mDestroyed) return;
                mPageCount = pages;
                if (mPage >= mPageCount) mPage = mPageCount - 1;

                mPagedHorizontally = horizontal;
                setPage(mPage);

                if (mContentClient != null) {
                    mContentClient.onReadingProgressChanged(getReadingProgress(), getPaperPage());
                }
                Log.d(TAG, "Paging setup.");
            });
        }

        @JavascriptInterface
        public void setPage(final int page) {
            mHandler.post(() -> {
                if (!mDestroyed) HtmlContentWebView.this.setPage(page, false);
            });
        }

        @JavascriptInterface
        public void openFootNote(final String url, final String title, final int x, final int y) {
            mHandler.post(() -> {
                if (mContentClient != null) {
                    mContentClient.showFootNote(url, title, getLeft() + getViewXScaled(x), getTop() + getViewYScaled(y));
                }
            });
        }

        @JavascriptInterface
        public void onTouchUp() {
            mHandler.post(() -> {
                if (mContentClient != null) {
                    mContentClient.onPagePress(getLeft() + mLastTouchX, getTop() + mLastTouchY);
                }
            });
        }

        @JavascriptInterface
        public void openLink(final String url) {
            mHandler.post(() -> handleLinkNavigation(url));
        }

        @JavascriptInterface
        public void openImage(final String url, final float x, final float y, final float w, final float h) {
            mHandler.post(() -> {
                String path = Uri.parse(url).getPath();
                if (path != null && !path.isEmpty()) {
                    mPictureRestoreCallback = null;
                    if (mContentClient != null) {
                        mContentClient.showPicture(path.substring(1), getLeft() + getViewXScaled(x), getTop() + getViewYScaled(y), getViewXScaled(w), getViewYScaled(h));
                    }
                } else {
                    restoreShowcasedImage(null);
                }
            });
        }

        @JavascriptInterface
        public void setAnchorPositions(final String json) {
            mHandler.post(() -> {
                mAnchorPositions.clear();
                if (json == null) return;
                try {
                    JSONArray array = new JSONArray(json);
                    for (int i = 0; i < array.length(); i++) {
                        JSONArray item = array.getJSONArray(i);
                        String href = item.getString(0);
                        TocEntry entry = mBook != null ? mBook.findTocEntry(mUrl + href) : null;

                        int position = (mRendition != null && mRendition.flowStyle == Rendition.FLOW_PAGED)
                                ? item.getInt(1)
                                : getViewYScaled((float) item.getDouble(1));

                        if (entry != null) {
                            mAnchorPositions.add(new AnchorPositionItem(entry, position));
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            });
        }

        @JavascriptInterface
        public void notifySize(int w, int h) {
            final float scaleX = (float) w / getWidth();
            final float scaleY = (float) h / getHeight();
            final float density = getResources().getDisplayMetrics().density;

            mHandler.post(() -> {
                mContentScaleX = scaleX;
                mContentScaleY = scaleY;
                mDensityFixedWidth = w * density;
                mDensityFixedHeight = h * density;
            });
        }

        @JavascriptInterface
        public void setPaperPageMap(final String json) {
            mHandler.post(() -> {
                mPaperPagePositionMap.clear();
                try {
                    JSONObject obj = new JSONObject(json);
                    Iterator<String> keys = obj.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        int position = obj.getInt(key);
                        if (mRendition != null && mRendition.flowStyle == Rendition.FLOW_SCROLLED) {
                            position = getViewYScaled(position);
                        }
                        mPaperPagePositionMap.put(Integer.parseInt(key), position);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            });
        }

        @JavascriptInterface
        public void onRestoreShowcasedImageDone() {
            mHandler.post(() -> {
                final BookView.PictureRestoreCallback callback = mPictureRestoreCallback;
                if (callback != null) {
                    postVisualStateCallback(1L, new VisualStateCallback() {
                        @Override
                        public void onComplete(long requestId) {
                            if (mPictureRestoreCallback == callback) {
                                mPictureRestoreCallback.onRestored();
                            }
                        }
                    });
                }
            });
        }
    }

    private static class AnchorPositionItem {
        public TocEntry entry;
        public int position;
        public AnchorPositionItem(TocEntry entry, int position) {
            this.entry = entry;
            this.position = position;
        }
    }
}