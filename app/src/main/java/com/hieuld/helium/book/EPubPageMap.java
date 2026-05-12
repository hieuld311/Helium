package com.hieuld.helium.book;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.hieuld.helium.
db.EPubPageMapTable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps each EPUB spine item (file path) to its estimated page range within the book.
 *
 * Page counts are estimated by character count (approx. {@link #CHARACTERS_PER_PAGE}
 * non-markup characters per page). Fixed-layout items always count as exactly 1 page.
 *
 * <p>Instances can be persisted to / restored from the SQLite cache via
 * {@link #writeToCache} and {@link #readFromCache}.</p>
 */
public class EPubPageMap implements Serializable {

    private static final int    CHARACTERS_PER_PAGE = 1800;
    private static final String TAG                 = "EPubPageMap";

    /** Per-file page information. */
    public Map<String, Item> items;
    /** Total estimated page count across all spine items. */
    public int totalPageCount;

    // ── Inner types ───────────────────────────────────────────────────────────

    public static class Item implements Serializable {
        /** 0-based page number where this spine item begins. */
        public int pageStart;
        /** Estimated number of pages this spine item occupies (≥ 1). */
        public int pageCount;
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    /**
     * Generate a page map by iterating over all spine items of {@code book}
     * and estimating their page counts from character counts.
     */
    public static EPubPageMap generate(EPubBook book) {
        EPubPageMap map = new EPubPageMap();
        map.items = new HashMap<>();

        String spineItem = null;
        while (true) {
            spineItem = book.nextSpineItem(spineItem);
            if (spineItem == null) break;

            String mimeType   = book.getMimeType(spineItem);
            String lowerPath  = spineItem.toLowerCase();
            int estimatedPages = 1;

            boolean isHtml = "application/xhtml+xml".equalsIgnoreCase(mimeType)
                    || "text/html".equalsIgnoreCase(mimeType)
                    || lowerPath.endsWith(".html")
                    || lowerPath.endsWith(".xhtml")
                    || lowerPath.endsWith(".htm")
                    || lowerPath.endsWith(".xml");

            if (isHtml) {
                InputStream stream = book.getInputStreamForFile(spineItem);
                if (stream == null) {
                    Log.e(TAG, "InputStream for " + spineItem + " is null!");
                    return null;
                }
                if (!book.getItemRendition(spineItem).isFixedLayout()) {
                    try {
                        int charCount = countNonMarkupCharacters(stream);
                        estimatedPages = Math.max(1, (int) Math.ceil(charCount / (float) CHARACTERS_PER_PAGE));
                    } catch (IOException e) {
                        e.printStackTrace();
                        return null;
                    } finally {
                        try { stream.close(); } catch (IOException ignored) { }
                    }
                } else {
                    try { stream.close(); } catch (IOException ignored) { }
                }
            }

            Item item      = new Item();
            item.pageCount = estimatedPages;
            item.pageStart = map.totalPageCount;
            map.totalPageCount += estimatedPages;
            map.items.put(spineItem, item);
        }
        return map;
    }

    // ── Cache I/O ─────────────────────────────────────────────────────────────

    /** Persist a page map to the SQLite cache table, replacing any existing data for {@code bookId}. */
    public static void writeToCache(EPubPageMap pageMap, long bookId, SQLiteDatabase db) {
        db.beginTransaction();
        try {
            db.delete(EPubPageMapTable.TABLE_NAME, "book_id=?", new String[]{String.valueOf(bookId)});
            for (Map.Entry<String, Item> entry : pageMap.items.entrySet()) {
                Item item = entry.getValue();
                ContentValues cv = new ContentValues();
                cv.put("book_id",                              bookId);
                cv.put(EPubPageMapTable.COLUMN_SPINE_FILE,     entry.getKey());
                cv.put(EPubPageMapTable.COLUMN_PAGE_START,     item.pageStart);
                cv.put(EPubPageMapTable.COLUMN_PAGE_COUNT,     item.pageCount);
                db.insertOrThrow(EPubPageMapTable.TABLE_NAME, null, cv);
            }
            db.setTransactionSuccessful();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Restore a page map from the SQLite cache.
     *
     * @return the cached map, or {@code null} if no cached data exists for {@code bookId}.
     */
    public static EPubPageMap readFromCache(long bookId, SQLiteDatabase db) {
        Cursor cursor = db.query(
                EPubPageMapTable.TABLE_NAME,
                new String[]{"_id", EPubPageMapTable.COLUMN_SPINE_FILE,
                        EPubPageMapTable.COLUMN_PAGE_START, EPubPageMapTable.COLUMN_PAGE_COUNT},
                "book_id=?", new String[]{String.valueOf(bookId)},
                null, null, null);

        if (cursor.getCount() == 0) { cursor.close(); return null; }

        EPubPageMap map = new EPubPageMap();
        map.items = new HashMap<>();
        while (cursor.moveToNext()) {
            String spineFile = cursor.getString(1);
            Item item        = new Item();
            item.pageStart   = cursor.getInt(2);
            item.pageCount   = cursor.getInt(3);
            map.totalPageCount += item.pageCount;
            map.items.put(spineFile, item);
        }
        cursor.close();
        return map;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Count the non-markup characters in an HTML/XML stream.
     * Characters inside {@code < >} tags are excluded from the count.
     */
    public static int countNonMarkupCharacters(InputStream stream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
        int count   = 0;
        boolean inTag = false;
        int ch;
        while ((ch = reader.read()) >= 0) {
            if (!inTag && ch == '<')      { inTag = true; }
            else if (inTag && ch == '>')  { inTag = false; }
            else if (!inTag)              { count++; }
        }
        return count;
    }
}
