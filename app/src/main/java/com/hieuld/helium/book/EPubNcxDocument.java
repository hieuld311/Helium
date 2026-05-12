package com.hieuld.helium.book;

import android.util.Xml;

import com.hieuld.helium.util.UrlUtils;
import com.hieuld.helium.util.Utils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser for EPUB 2 NCX navigation documents ({@code .ncx} files).
 *
 * Produces a flat-ordered list of {@link TocEntry} objects that mirrors the
 * depth-first traversal order of the NCX {@code <navMap>}.
 */
public class EPubNcxDocument {

    private final InputStream                  mInputStream;
    private String                             mBaseUrl;
    private final Map<String, List<TocEntry>>  mTableMap = new HashMap<>();

    public EPubNcxDocument(InputStream inputStream) {
        mInputStream = inputStream;
    }

    /** Set the directory prefix prepended to relative href values. */
    public void setBaseUrl(String baseUrl) {
        mBaseUrl = baseUrl;
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

    /** Parse the NCX stream. Returns {@code true} on success, {@code false} on error. */
    public boolean parse() {
        try {
            XmlPullParser parser = Xml.newPullParser();
            SimplifiedXmlParser sxp = new SimplifiedXmlParser(parser);
            parser.setInput(mInputStream, null);
            parser.setFeature("http://xmlpull.org/v1/doc/features.html#process-namespaces", false);

            String path;
            while ((path = sxp.nextPath()) != null) {
                if ("ncx/navMap".equals(path)) {
                    parseTocTable(parser, sxp, "toc");
                }
            }
            return true;
        } catch (IOException | XmlPullParserException e) {
            e.printStackTrace();
            return false;
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void parseTocTable(XmlPullParser parser, SimplifiedXmlParser sxp, String tableKey)
            throws XmlPullParserException, IOException {
        List<TocEntry> entries = new ArrayList<>();
        int rootDepth = sxp.getDepth();

        String path;
        while ((path = sxp.nextPath(rootDepth)) != null) {
            if ("navPoint".equals(path)) {
                parseNavPoint(parser, sxp, entries, 0, null);
            }
        }
        mTableMap.put(tableKey, entries);
    }

    private void parseNavPoint(XmlPullParser parser, SimplifiedXmlParser sxp,
                               List<TocEntry> collector, int depth, TocEntry parent)
            throws XmlPullParserException, IOException {
        int rootDepth = sxp.getDepth();
        TocEntry entry = new TocEntry();
        entry.depth    = depth;
        entry.parent   = parent;
        entry.children = new ArrayList<>();

        // flat list of descendants (for adding to the parent collector in DFS order)
        List<TocEntry> descendants = new ArrayList<>();

        String path;
        while ((path = sxp.nextPath(rootDepth)) != null) {
            switch (path) {
                case "navLabel/text":
                    entry.title = parser.nextText();
                    break;

                case "content": {
                    String src = parser.getAttributeValue(null, "src");
                    if (src != null) {
                        String decoded = UrlUtils.safeDecode(src, "UTF-8");
                        entry.path = mBaseUrl;
                        if (mBaseUrl != null) decoded = mBaseUrl + decoded;
                        entry.url = Utils.normalizePath(decoded);
                    }
                    break;
                }

                case "navPoint": {
                    List<TocEntry> childList = new ArrayList<>();
                    parseNavPoint(parser, sxp, childList, depth + 1, entry);
                    entry.children.addAll(childList);
                    descendants.addAll(childList);
                    break;
                }
            }
        }

        if (entry.title == null || entry.url == null) return;
        collector.add(entry);
        collector.addAll(descendants);
    }

    // ── Public accessors ──────────────────────────────────────────────────────

    /**
     * Returns the parsed TOC for the given table key (e.g. {@code "toc"}).
     * Returns an empty list if the key was not found.
     */
    public List<TocEntry> getTable(String tableKey) {
        List<TocEntry> table = mTableMap.get(tableKey);
        return table != null ? table : new ArrayList<>();
    }
}
