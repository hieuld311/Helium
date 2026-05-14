package com.hieuld.helium.annotations;

import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;

import com.hieuld.helium.content.HtmlContentWebView;
import com.hieuld.helium.util.ThreadUtils;
import com.google.gson.Gson;

import java.util.concurrent.Callable;

/**
 * {@link AnnotationRenderer} implementation backed by a WebView.
 *
 * Bridges between the {@link AnnotationController} (which runs on the main thread) and the
 * JavaScript annotation engine running inside an {@link HtmlContentWebView}.
 *
 * <h3>Threading model</h3>
 * <ul>
 *   <li>Calls from Java → JS are dispatched via {@link HtmlContentWebView#executeJavascript}
 *       and must come from the main thread.</li>
 *   <li>Calls from JS → Java arrive on a WebView internal thread ({@link JsApi} methods
 *       annotated with {@link JavascriptInterface}); they are re-posted to the main thread
 *       through {@link #mMainHandler} before touching controller state.</li>
 * </ul>
 */
public class HtmlAnnotationRenderer extends AnnotationRenderer {

    private static final String JS_NAMESPACE = "HeliumAnnotations";
    private static final String TAG = "HtmlAnnotationRenderer";

    private final AnnotationController mController;
    private final HtmlContentWebView   mWebView;
    private final Handler              mMainHandler;

    public HtmlAnnotationRenderer(AnnotationController controller,
                                  HtmlContentWebView webView) {
        mController  = controller;
        mWebView     = webView;
        mMainHandler = new Handler(Looper.getMainLooper());
        webView.getJsBridge().addObject("liNativeAnnotations", new JsApi());
    }

    // ── AnnotationRenderer ────────────────────────────────────────────────────

    @Override
    public void restoreAnnotation(long annotationId, int type, String range,
                                  int color, boolean hasNote, int restoreFlags) {
        execJs("restoreAnnotation(" + annotationId + ", " + type + ", '"
                + range + "', " + color + ", " + hasNote + ", " + restoreFlags + ")");
    }

    @Override
    public void deleteAnnotation(long annotationId) {
        execJs("deleteAnnotation(" + annotationId + ")");
    }

    @Override
    public void updateAnnotation(long annotationId, int type, int color, boolean hasNote) {
        execJs("updateAnnotation(" + annotationId + ", " + type + ", " + color + ", " + hasNote + ")");
    }

    @Override
    public void setSelectionColor(int color) {
        execJs("setSelectionColor(" + color + ")");
    }

    @Override
    public void clearSelection() {
        execJs("clearSelection()");
    }

    @Override
    public void requestUpdatedBounds(int requestId, long annotationId) {
        execJs("requestUpdatedBounds(" + requestId + ", " + annotationId + ")");
    }

    @Override
    public void hideNoteMarkers() {
        execJs("hideNoteMarkers()");
    }

    @Override
    public void showNoteMarkers() {
        execJs("showNoteMarkers()");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Shorthand: execute a call on the JS annotation namespace. */
    private void execJs(String call) {
        mWebView.executeJavascript(JS_NAMESPACE + "." + call);
    }

    /**
     * Convert WebView-local pixel coordinates to view-relative screen coordinates.
     * The WebView's {@link HtmlContentWebView#getViewXScaled} / {@link HtmlContentWebView#getViewYScaled}
     * account for the current zoom level and scroll offset.
     */
    private Rect toScreenRect(int left, int top, int right, int bottom) {
        return new Rect(
                mWebView.getViewXScaled(left)  + mWebView.getLeft(),
                mWebView.getViewYScaled(top)   + mWebView.getTop(),
                mWebView.getViewXScaled(right) + mWebView.getLeft(),
                mWebView.getViewYScaled(bottom) + mWebView.getTop()
        );
    }

    // ── JS → Java bridge ─────────────────────────────────────────────────────

    /**
     * JavaScript interface exposed to the annotation engine as {@code liNativeAnnotations}.
     *
     * All public methods are called from the WebView JS thread and re-dispatched to the
     * main thread before any controller state is touched.
     */
    class JsApi {

        private JsApi() { }

        // -- Selection lifecycle --

        @JavascriptInterface
        public void onSelectionCreated() {
            mMainHandler.post(mController::onSelectionCreated);
        }

        @JavascriptInterface
        public void onSelectionStarted() {
            mMainHandler.post(mController::onSelectionStarted);
        }

        @JavascriptInterface
        public void onSelectionStopped(int left, int top, int right, int bottom,
                                       final String selectedText) {
            final Rect bounds = toScreenRect(left, top, right, bottom);
            mMainHandler.post(() -> mController.onSelectionStopped(bounds, selectedText));
        }

        @JavascriptInterface
        public void onSelectionDestroyed() {
            mMainHandler.post(mController::onSelectionDestroyed);
        }

        // -- Annotation interaction --

        @JavascriptInterface
        public void onAnnotationSelected(final long annotationId,
                                         int left, int top, int right, int bottom,
                                         final String selectedText) {
            final Rect bounds = toScreenRect(left, top, right, bottom);
            mMainHandler.post(() -> mController.selectAnnotation(annotationId, bounds, selectedText));
        }

        @JavascriptInterface
        public void onBoundsUpdated(final int requestId,
                                    int left, int top, int right, int bottom) {
            final Rect bounds = toScreenRect(left, top, right, bottom);
            mMainHandler.post(() -> mController.onBoundsUpdated(requestId, bounds));
        }

        // -- Annotation insertion --

        /**
         * Called synchronously from JS to check whether the current selection should
         * produce an annotation (returns {@code false} if the user dismissed the toolbar).
         * Blocks the JS thread until the main thread responds.
         */
        @JavascriptInterface
        public boolean shouldInsertAnnotation() {
            Callable<Boolean> query = () -> mController.shouldInsertAnnotation();
            return Boolean.TRUE.equals(ThreadUtils.runOnMainThread(mMainHandler, query));
        }

        /**
         * Called synchronously from JS to commit the annotation to the database.
         *
         * @param range     serialised CFI range string
         * @param text      selected plain text
         * @param color     chosen ARGB color int
         * @return          JSON-serialised {@link AnnotationController.FinalizeResult}
         */
        @JavascriptInterface
        public String insertAnnotation(String range, String text, int color) {
            return new Gson().toJson(mController.finalizeAnnotation(range, text, color));
        }

        /**
         * Called from JS during a database upgrade to back-fill the {@code text} column
         * on an existing annotation. Runs on the main thread via a blocking call.
         */
        @JavascriptInterface
        public void upgradeAnnotation19(final long annotationId, final String text) {
            ThreadUtils.runOnMainThread(mMainHandler,
                    (Callable<Void>) () -> {
                        mController.upgradeAnnotation19(annotationId, text);
                        return null;
                    });
        }
    }
}
