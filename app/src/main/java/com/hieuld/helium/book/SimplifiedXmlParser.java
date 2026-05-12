package com.hieuld.helium.book;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Thin wrapper around {@link XmlPullParser} that tracks the current element path
 * as a slash-joined string (e.g. {@code "package/manifest/item"}).
 *
 * <p>Usage pattern:
 * <pre>{@code
 * SimplifiedXmlParser sxp = new SimplifiedXmlParser(parser);
 * String path;
 * while ((path = sxp.nextPath()) != null) {
 *     if ("package/metadata/title".equals(path)) {
 *         title = parser.nextText();
 *     }
 * }
 * }</pre>
 */
public class SimplifiedXmlParser {

    private final XmlPullParser  mParser;
    private final List<String>   mTagStack = new ArrayList<>();

    public SimplifiedXmlParser(XmlPullParser parser) {
        mParser = parser;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Advances to the next START_TAG and returns the full path from document root,
     * or {@code null} if the document end has been reached.
     */
    public String nextPath() throws XmlPullParserException, IOException {
        popIfEndTag();
        while (mParser.next() != XmlPullParser.START_TAG) {
            if (mParser.getEventType() == XmlPullParser.END_DOCUMENT) return null;
            if (mParser.getEventType() == XmlPullParser.END_TAG) popStack();
        }
        mTagStack.add(localName());
        return joinPath(mTagStack);
    }

    /**
     * Advances to the next START_TAG <em>within</em> the subtree rooted at depth
     * {@code rootDepth}, and returns the path relative to that subtree root.
     * Returns {@code null} when the subtree has been fully consumed (END_TAG at rootDepth).
     */
    public String nextPath(int rootDepth) throws XmlPullParserException, IOException {
        popIfEndTag();
        while (mParser.next() != XmlPullParser.START_TAG) {
            if (mParser.getEventType() == XmlPullParser.END_DOCUMENT) return null;
            if (mParser.getEventType() == XmlPullParser.END_TAG) {
                if (mTagStack.size() == rootDepth) return null; // exited the subtree
                popStack();
            }
        }
        mTagStack.add(localName());
        if (mTagStack.size() <= rootDepth) return null;
        return joinPath(mTagStack.subList(rootDepth, mTagStack.size()));
    }

    /** Current nesting depth (number of open tags on the stack). */
    public int getDepth() {
        return mTagStack.size();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Strip any namespace prefix (e.g. {@code "opf:package"} → {@code "package"}). */
    private String localName() {
        String name = mParser.getName();
        int colon = name.indexOf(':');
        return colon > -1 ? name.substring(colon + 1) : name;
    }

    /** Pop the top of the stack if the parser is currently positioned at an END_TAG. */
    private void popIfEndTag() throws XmlPullParserException {
        if (mParser.getEventType() == XmlPullParser.END_TAG) popStack();
    }

    private void popStack() {
        if (!mTagStack.isEmpty()) mTagStack.remove(mTagStack.size() - 1);
    }

    private static String joinPath(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append('/');
            sb.append(parts.get(i));
        }
        return sb.toString();
    }
}
