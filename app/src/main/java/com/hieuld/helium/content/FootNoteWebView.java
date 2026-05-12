package com.hieuld.helium.content;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.JsPromptResult;
import android.webkit.MimeTypeMap;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.hieuld.helium.SettingsHtmlActivity;

/**
 * WebView that displays pop-up footnote content for an EPUB spine item.
 *
 * <p>Click events on {@code <a>} links are intercepted by a small injected JavaScript snippet
 * and routed back to the host via {@link Listener#onFootNoteUrlClicked}.</p>
 */
public class FootNoteWebView extends WebView {

    private static final String TAG = "FootNoteWebView";

    /** JavaScript injected at load time to capture anchor clicks. */
    private static final String LINKS_SCRIPT =
            "window.onload = function() {" +
            "   var elems = document.getElementsByTagName('a');" +
            "   for (var i = 0; i < elems.length; ++i) {" +
            "       elems[i].addEventListener('click', function(e) {" +
            "           e.stopPropagation();" +
            "           if (this.href.indexOf('javascript:') === 0) return;" +
            "           e.preventDefault();" +
            "           LithiumFootNote.openLink(this.href);" +
            "       });" +
            "   }" +
            "};";

    private final Handler         mHandler = new Handler(Looper.getMainLooper());
    private final JavaScriptBridge mBridge;
    private final Listener         mListener;
    private BookView.FootNoteProvider mProvider;

    public interface Listener {
        void onFootNoteUrlClicked(String url);
    }

    public FootNoteWebView(Context context, Listener listener) {
        super(context);
        mListener = listener;
        mBridge   = new JavaScriptBridge(this);
        mBridge.addObject("LithiumFootNote", new BridgeInterface());

        setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                if (mProvider == null) return super.shouldInterceptRequest(view, url);

                Uri uri = Uri.parse(url);
                if (!SettingsHtmlActivity.EXTRA_FILE.equals(uri.getScheme())) {
                    return super.shouldInterceptRequest(view, url);
                }

                String path = uri.getPath();
                if (path != null && path.startsWith("/")) path = path.substring(1);

                String mimeType = mProvider.getFootNoteResourceMimeType(path);
                if (mimeType == null) {
                    String ext = MimeTypeMap.getFileExtensionFromUrl(path);
                    if (ext != null) mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
                }
                return new WebResourceResponse(mimeType, "UTF-8", mProvider.getFootNoteResource(path));
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                mListener.onFootNoteUrlClicked(url);
                return true;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) {
                    return super.shouldOverrideUrlLoading(view, request.getUrl().toString());
                }
                return super.shouldOverrideUrlLoading(view, request);
            }
        });

        setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage msg) {
                String log = "[CONSOLE] " + msg.message() +
                        " (source: " + msg.sourceId() + ":" + msg.lineNumber() + ")";
                switch (msg.messageLevel()) {
                    case ERROR:   Log.e(TAG, log); break;
                    case WARNING: Log.w(TAG, log); break;
                    case DEBUG:   Log.d(TAG, log); break;
                    default:      Log.i(TAG, log); break;
                }
                return true;
            }

            @Override
            public boolean onJsPrompt(WebView view, String url, String message,
                                      String defaultValue, JsPromptResult result) {
                String response = mBridge.onPrompt(message);
                if (response != null) { result.confirm(response); return true; }
                return super.onJsPrompt(view, url, message, defaultValue, result);
            }
        });

        getSettings().setJavaScriptEnabled(true);
        getSettings().setAllowFileAccess(true);
    }

    // ── BridgeInterface ───────────────────────────────────────────────────────

    private class BridgeInterface {
        @JavascriptInterface
        public void openLink(final String url) {
            mHandler.post(() -> mListener.onFootNoteUrlClicked(url));
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void setProvider(BookView.FootNoteProvider provider) {
        mProvider = provider;
    }

    /** Inject {@code htmlContent} (HTML fragment) into a full page and load it. */
    public void load(String htmlContent) {
        String baseUrl = mProvider != null ? mProvider.getFootNoteBaseUrl() : null;
        String fullHtml = "<html><head></head><body>" +
                "<script type='text/javascript'>" + LINKS_SCRIPT + mBridge.getSetupJs() + "</script>" +
                htmlContent +
                "</body></html>";
        loadDataWithBaseURL(baseUrl, fullHtml, "text/html", "utf-8", null);
    }
}
