package com.hieuld.helium.book;

import android.os.Handler;
import android.os.Looper;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeFilter;
import org.jsoup.select.NodeTraversor;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Full-text search implementation for EPUB books.
 *
 * <p>Searching is done on a background thread ({@link SearchThread}). Results are
 * delivered on the main thread via {@link SearchProvider.Callbacks#onSearchFinished}.</p>
 *
 * <h3>Search strategy</h3>
 * <ol>
 *   <li>Search TOC entry titles (produces {@link SearchResult#TYPE_TOC_ENTRY} results).</li>
 *   <li>Search every HTML spine item sequentially (produces {@link SearchResult#TYPE_NORMAL}).</li>
 * </ol>
 * Search stops when {@link SearchProvider#MAXIMUM_RESULTS} is reached or all content
 * has been scanned.
 */
public class EPubSearchProvider implements SearchProvider {

    private static final int    SNIPPET_CONTEXT = 100; // characters of context on each side
    private static final String TAG = "EPubSearchProvider";

    private final EPubBook mBook;
    private final Handler  mMainHandler = new Handler(Looper.getMainLooper());
    private SearchThread   mActiveThread;

    public EPubSearchProvider(EPubBook book) {
        mBook = book;
    }

    // ── SearchProvider ────────────────────────────────────────────────────────

    @Override
    public void startSearch(String query, SearchProvider.Callbacks callbacks) {
        cancelSearch();
        mActiveThread = new SearchThread(query, callbacks);
        mActiveThread.start();
    }

    @Override
    public void cancelSearch() {
        if (mActiveThread != null) {
            mActiveThread.cancel();
            mActiveThread = null;
        }
    }

    // ── SearchThread ──────────────────────────────────────────────────────────

    class SearchThread extends Thread {

        private final String                            mQuery;
        private final Pattern                           mPattern;
        private final WeakReference<SearchProvider.Callbacks> mCallbacksRef;
        private final List<SearchResult>                mResults = new ArrayList<>();
        private volatile boolean                        mCancelled;

        SearchThread(String query, SearchProvider.Callbacks callbacks) {
            mQuery        = query;
            mPattern      = Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            mCallbacksRef = new WeakReference<>(callbacks);
        }

        @Override
        public void run() {
            if (searchTocEntries()) {
                String spineItem = null;
                do {
                    spineItem = mBook.nextSpineItem(spineItem);
                    if (spineItem == null) break;
                } while (searchSpineItem(spineItem));
            }

            if (!mCancelled) {
                mMainHandler.post(() -> {
                    if (!mCancelled) {
                        SearchProvider.Callbacks cb = mCallbacksRef.get();
                        if (cb != null) cb.onSearchFinished(mResults);
                    }
                });
            }
        }

        public void cancel() {
            mCancelled = true;
        }

        // ── TOC search ────────────────────────────────────────────────────────

        /** @return {@code true} to continue searching, {@code false} to stop. */
        private boolean searchTocEntries() {
            for (TocEntry entry : mBook.getTocEntries()) {
                Matcher m = mPattern.matcher(entry.title);
                if (m.find()) {
                    SearchResult result = new SearchResult();
                    result.type             = SearchResult.TYPE_TOC_ENTRY;
                    result.snippet          = entry.title;
                    result.snippetIndexStart = m.start();
                    result.snippetIndexEnd   = m.end();
                    result.position         = entry.url;
                    mResults.add(result);
                }
                if (mResults.size() >= MAXIMUM_RESULTS || mCancelled) return false;
            }
            return true;
        }

        // ── Spine item search ─────────────────────────────────────────────────

        /** @return {@code true} to continue, {@code false} to stop. */
        private boolean searchSpineItem(String spineFile) {
            String mimeType = mBook.getMimeType(spineFile);
            if ("text/html".equals(mimeType) || "application/xhtml+xml".equals(mimeType)) {
                return searchHtmlFile(spineFile);
            }
            return true; // skip non-HTML items
        }

        private boolean searchHtmlFile(String spineFile) {
            InputStream stream = mBook.getInputStreamForFile(spineFile);
            if (stream == null) return true;

            String text = extractPlainText(stream);
            if (text == null) return true;

            // Normalise line endings so multi-line snippets don't break
            String normalized = text.replaceAll("(\r\n)|[\r\n]", " ");
            Matcher m         = mPattern.matcher(normalized);
            int totalLength   = normalized.length();

            while (m.find()) {
                int matchStart = m.start();
                int matchEnd   = m.end();
                int queryLen   = mQuery.length();

                // Build a snippet with SNIPPET_CONTEXT chars on each side
                int snippetStart = Math.max(0, matchStart - SNIPPET_CONTEXT);
                int snippetEnd   = Math.min(totalLength - 1, matchEnd + SNIPPET_CONTEXT);

                // If either side is clamped, try to compensate on the other side
                int slack = (queryLen + SNIPPET_CONTEXT * 2) - (snippetEnd - snippetStart);
                if (slack > 0) {
                    if (snippetStart == 0 && snippetEnd < totalLength - 1) {
                        snippetEnd = Math.min(totalLength - 1, snippetEnd + slack);
                    } else if (snippetEnd == totalLength - 1 && snippetStart > 0) {
                        snippetStart = Math.max(0, snippetStart - slack);
                    }
                }

                SearchResult result = new SearchResult();
                JsonObject pos = new JsonObject();
                pos.add("filename", new JsonPrimitive(spineFile));
                pos.add("start",    new JsonPrimitive(matchStart));
                pos.add("end",      new JsonPrimitive(matchEnd));
                result.position         = pos.toString();
                result.snippet          = normalized.substring(snippetStart, snippetEnd);
                result.snippetIndexStart = matchStart - snippetStart;
                result.snippetIndexEnd   = result.snippetIndexStart + queryLen;
                mResults.add(result);

                if (mResults.size() >= MAXIMUM_RESULTS || mCancelled) return false;
            }
            return true;
        }

        /**
         * Parse an HTML/XHTML stream with Jsoup and concatenate all text nodes,
         * skipping tag markup.
         */
        private String extractPlainText(InputStream stream) {
            try {
                Document doc = Jsoup.parse(stream, "UTF-8", "/");
                StringBuilder sb = new StringBuilder();
                NodeTraversor.filter(new NodeFilter() {
                    @Override
                    public FilterResult head(Node node, int depth) {
                        if (node instanceof TextNode) {
                            sb.append(((TextNode) node).getWholeText());
                        }
                        return FilterResult.CONTINUE;
                    }
                    @Override
                    public FilterResult tail(Node node, int depth) {
                        return FilterResult.CONTINUE;
                    }
                }, doc.body());
                return sb.toString();
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            } finally {
                try { stream.close(); } catch (IOException ignored) { }
            }
        }
    }
}
