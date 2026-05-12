package com.hieuld.helium.content;

import android.annotation.SuppressLint;
import android.util.Log;
import android.webkit.WebView;

/**
 * Wrapper for the JavaScript ↔ Java bridge used in reader WebViews.
 *
 * Since the app targets API 26+, the native {@code addJavascriptInterface} mechanism
 * is always available and secure. The older emulated-prompt bridge is no longer needed.
 *
 * <p>Legacy methods ({@link #onPrompt}, {@link #getSetupJs}, {@link #addToExecQueue},
 * {@link #resetExecQueue}, {@link #isUsingExecQueue}) are retained as no-ops for
 * source compatibility with call sites that have not yet been updated.</p>
 */
public class JavaScriptBridge {

    private static final String TAG = "JavaScriptBridge";

    private final WebView mWebView;

    public JavaScriptBridge(WebView webView) {
        mWebView = webView;
    }

    /**
     * Retained for source compatibility. The {@code forceEmulation} flag is ignored
     * because the emulated-prompt bridge is no longer supported on API 26+.
     */
    public JavaScriptBridge(WebView webView, boolean forceEmulation) {
        mWebView = webView;
        if (forceEmulation) {
            Log.w(TAG, "Emulated bridge is no longer supported on API 26+; using native bridge.");
        }
    }

    /** Attach {@code obj} to the WebView under the given JavaScript name. */
    @SuppressLint("JavascriptInterface")
    public void addObject(String name, Object obj) {
        mWebView.addJavascriptInterface(obj, name);
    }

    /**
     * No-op. The emulated-prompt bridge is obsolete on API 26+.
     * Always returns {@code null} so the WebChromeClient falls through to the default handler.
     */
    public String onPrompt(String message) {
        return null;
    }

    /** No-op. No injected setup script is needed when using the native bridge. */
    public String getSetupJs() {
        return "";
    }

    /** No-op. {@code evaluateJavascript} is always available on API 26+. */
    public void addToExecQueue(String js) {
        Log.w(TAG, "addToExecQueue() is obsolete; use evaluateJavascript() directly.");
    }

    /** No-op. */
    public void resetExecQueue() { }

    /** Always returns {@code false}; {@code evaluateJavascript} is always used. */
    public boolean isUsingExecQueue() {
        return false;
    }
}
