package com.hieuld.helium.book;

import com.hieuld.helium.util.UrlUtils;
import com.hieuld.helium.util.Utils;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Parser for EPUB 3 Navigation Documents ({@code nav.xhtml}).
 *
 * Handles two navigation types:
 * <ul>
 *   <li>{@code epub:type="toc"} — Table of Contents ({@link #getTable})</li>
 *   <li>{@code epub:type="page-list"} — Paper page map ({@link #getPageMap})</li>
 * </ul>
 */
public class EPubNavigationDocument {

    private final InputStream                  mInputStream;
    private String                             mBaseUrl;
    private final Map<String, List<TocEntry>>  mTableMap = new HashMap<>();
    private Map<Integer, String>               mPageMap;

    public EPubNavigationDocument(InputStream inputStream) {
        mInputStream = inputStream;
    }

    /** Set the directory prefix prepended to relative href values. */
    public void setBaseUrl(String baseUrl) {
        mBaseUrl = baseUrl;
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

    /** Parse the navigation document. Returns {@code true} on success. */
    public boolean parse() {
        try {
            XmlPullParser parser = android.util.Xml.newPullParser();
            SimplifiedXmlParser sxp = new SimplifiedXmlParser(parser);
            parser.setInput(mInputStream, null);
            parser.setFeature("http://xmlpull.org/v1/doc/features.html#process-namespaces", false);

            String path;
            while ((path = sxp.nextPath()) != null) {
                if (!path.endsWith("/nav")) continue;

                String epubType = parser.getAttributeValue(null, "epub:type");
                if (epubType == null) epubType = "toc"; // default to toc

                switch (epubType) {
                    case "toc":
                        mTableMap.put(epubType, parseTocTable(parser, sxp, 0, null));
                        break;
                    case "page-list":
                        parsePageList(parser, sxp);
                        break;
                    // other nav types (landmarks, etc.) are skipped
                }
            }
            return true;
        } catch (XmlPullParserException | IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Recursively parse an {@code <ol>} list of nav items.
     *
     * @param depth   current nesting level (0 = top-level chapter)
     * @param parent  enclosing {@link TocEntry}, or {@code null} at the root
     */
    private List<TocEntry> parseTocTable(XmlPullParser parser, SimplifiedXmlParser sxp,
                                         int depth, TocEntry parent)
            throws XmlPullParserException, IOException {
        List<TocEntry> entries = new ArrayList<>();
        int rootDepth = sxp.getDepth();

        String path;
        while ((path = sxp.nextPath(rootDepth)) != null) {
            if (path.endsWith("/li/a") || "li/a".equals(path)) {
                // Linked nav item
                String href  = parser.getAttributeValue(null, "href");
                String title = parseInlineTitle(parser);
                if (href != null && title != null) {
                    TocEntry entry = buildEntry(title, href, depth, parent);
                    entries.add(entry);
                }

            } else if (path.endsWith("/li/span") || "li/span".equals(path)) {
                // Unlinked nav item (section header without a direct URL)
                String title = parseInlineTitle(parser);
                if (title != null) {
                    TocEntry entry = new TocEntry();
                    entry.title    = title;
                    entry.path     = mBaseUrl;
                    entry.depth    = depth;
                    entry.parent   = parent;
                    entry.children = new ArrayList<>();
                    entries.add(entry);
                }

            } else if (path.endsWith("/li/ol") || "li/ol".equals(path)) {
                // Nested list
                TocEntry parentEntry = entries.isEmpty() ? null : entries.get(entries.size() - 1);
                boolean parentHasNoUrl = parentEntry != null && parentEntry.url == null;

                List<TocEntry> children = parseTocTable(parser, sxp, depth + 1, parentEntry);
                entries.addAll(children);

                // Promote the first child URL to the unlinked parent span
                if (parentHasNoUrl && !children.isEmpty()) {
                    parentEntry.url = children.get(0).url;
                }
            }
        }

        // Remove entries that still have no URL (malformed nav)
        Iterator<TocEntry> it = entries.iterator();
        while (it.hasNext()) {
            if (it.next().url == null) it.remove();
        }

        if (parent != null) parent.children.addAll(entries);
        return entries;
    }

    private TocEntry buildEntry(String title, String href, int depth, TocEntry parent) throws UnsupportedEncodingException {
        String decoded = UrlUtils.safeDecode(href, "UTF-8");
        if (mBaseUrl != null) decoded = mBaseUrl + decoded;

        TocEntry entry  = new TocEntry();
        entry.title     = title;
        entry.path      = mBaseUrl;
        entry.url       = Utils.normalizePath(decoded);
        entry.depth     = depth;
        entry.parent    = parent;
        entry.children  = new ArrayList<>();
        return entry;
    }

    private void parsePageList(XmlPullParser parser, SimplifiedXmlParser sxp)
            throws XmlPullParserException, IOException {
        int rootDepth = sxp.getDepth();
        mPageMap = new HashMap<>();

        String path;
        while ((path = sxp.nextPath(rootDepth)) != null) {
            if (!path.endsWith("/li/a")) continue;
            String href = parser.getAttributeValue(null, "href");
            try {
                int pageNumber = Integer.parseInt(parser.nextText());
                if (mBaseUrl != null) href = mBaseUrl + href;
                mPageMap.put(pageNumber, Utils.normalizePath(href));
            } catch (NumberFormatException ignored) {
                // non-integer page labels (e.g. "xii") are silently skipped
            }
        }
    }

    /**
     * Reads the text content of the current element, including text within child elements
     * (e.g. {@code <a>Chapter <span>1</span></a>} → {@code "Chapter 1"}).
     */
    public String parseInlineTitle(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        StringBuilder sb = new StringBuilder();
        while (parser.next() != XmlPullParser.END_TAG) {
            int event = parser.getEventType();
            if (event == XmlPullParser.START_TAG) {
                sb.append(parseInlineTitle(parser)); // recurse into child elements
            } else if (event == XmlPullParser.TEXT) {
                sb.append(parser.getText());
            }
        }
        return sb.toString();
    }

    // ── Public accessors ──────────────────────────────────────────────────────

    /**
     * Returns the flat-ordered TOC for {@code tableKey} (e.g. {@code "toc"}).
     * Returns an empty list if the key was not found.
     */
    public List<TocEntry> getTable(String tableKey) {
        List<TocEntry> table = mTableMap.get(tableKey);
        return table != null ? table : new ArrayList<>();
    }

    /**
     * Returns the paper page map: page-number → spine URL.
     * May be {@code null} if the navigation document contained no {@code page-list}.
     */
    public Map<Integer, String> getPageMap() {
        return mPageMap;
    }
}