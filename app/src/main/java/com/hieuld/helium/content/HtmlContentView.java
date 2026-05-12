package com.hieuld.helium.content;

import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.hieuld.helium.annotations.AnnotationController;
import com.hieuld.helium.annotations.AnnotationRenderer;
import com.hieuld.helium.annotations.HtmlAnnotationRenderer;
import com.hieuld.helium.book.EPubBook;
import com.hieuld.helium.book.Rendition;
import com.hieuld.helium.fonts.Font;
import com.hieuld.helium.themes.Theme;

/**
 * {@link ContentView} implementation for HTML/XHTML spine items.
 *
 * Delegates all rendering to an internal {@link HtmlContentWebView} and forwards
 * every call to it, with null-safety guards in case {@link #init()} has not been
 * called yet.
 */
public class HtmlContentView extends ContentView {

    private HtmlContentWebView mContentWebView;

    public HtmlContentView(Context context, EPubBook book, ContentClient contentClient) {
        super(context, book, contentClient);
    }

    @Override
    public boolean canOverScroll() { return true; }

    @Override
    public int getSupportedFeatures() {
        return mContentWebView != null ? mContentWebView.getSupportedFeatures() : 0;
    }

    @Override
    public void init() {
        mContentWebView = new HtmlContentWebView(mContext, mContentClient, mBook);
        mContentWebView.clearCache(false);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        lp.gravity = Gravity.CENTER;
        addView(mContentWebView, lp);
    }

    @Override
    public void release() {
        if (mContentWebView != null) mContentWebView.destroy();
    }

    @Override
    public void loadUrl(String url) {
        if (mContentWebView != null) mContentWebView.loadUrl(url);
    }

    @Override public void setPage(int page, boolean animate) {
        if (mContentWebView != null) mContentWebView.setPage(page, animate);
    }
    @Override public int getPage() {
        return mContentWebView != null ? mContentWebView.getPage() : 0;
    }
    @Override public int getPageCount() {
        return mContentWebView != null ? mContentWebView.getPageCount() : 1;
    }
    @Override public int getPaperPage() {
        return mContentWebView != null ? mContentWebView.getPaperPage() : -1;
    }

    @Override public void setReadingProgress(float progress, boolean animate) {
        if (mContentWebView != null) mContentWebView.setReadingProgress(progress, animate);
    }
    @Override public float getReadingProgress() {
        return mContentWebView != null ? mContentWebView.getReadingProgress() : 0.0f;
    }

    @Override public void hideShowcasedPicture() {
        if (mContentWebView != null) mContentWebView.hideShowcasedImage();
    }
    @Override public void restoreShowcasedPicture(BookView.PictureRestoreCallback callback) {
        if (mContentWebView != null) mContentWebView.restoreShowcasedImage(callback);
    }

    @Override public void setTextSize(int size) {
        if (mContentWebView != null) mContentWebView.setTextSize(size);
    }
    @Override public void setMargin(int margin) {
        if (mContentWebView != null) mContentWebView.setMargin(margin);
    }
    @Override public void setFont(Font font) {
        if (mContentWebView != null) mContentWebView.setFont(font);
    }
    @Override public void setTheme(Theme theme) {
        if (mContentWebView != null) mContentWebView.setTheme(theme);
    }
    @Override public void setLineHeight(float height) {
        if (mContentWebView != null) mContentWebView.setLineSpacing(height);
    }
    @Override public void setTextAlign(int align) {
        if (mContentWebView != null) mContentWebView.setTextAlign(align);
    }
    @Override public void setRendition(Rendition rendition) {
        if (mContentWebView != null) mContentWebView.setRendition(rendition);
    }

    @Override public boolean canScrollHorizontally(int direction) {
        return mContentWebView != null && mContentWebView.canScrollHorizontally(direction);
    }
    @Override public boolean canScrollVertically(int direction) {
        return mContentWebView != null && mContentWebView.canScrollVertically(direction);
    }
    @Override public boolean isPositionInView(float position) {
        return mContentWebView == null || mContentWebView.isPositionInView(position);
    }

    @Override public void jumpToAnnotation(long annotationId) {
        if (mContentWebView != null) mContentWebView.jumpToAnnotation(annotationId);
    }
    @Override public void jumpToSearchResult(int start, int end) {
        if (mContentWebView != null) mContentWebView.jumpToSearchResult(start, end);
    }

    @Override public String getTitle() {
        if (mContentWebView == null) return null;
        String title = mContentWebView.getTitle();
        return (title == null || "about:blank".equals(title)) ? null : title;
    }

    @Override
    public AnnotationRenderer createAnnotationRenderer(AnnotationController controller) {
        return new HtmlAnnotationRenderer(controller, mContentWebView);
    }
}
