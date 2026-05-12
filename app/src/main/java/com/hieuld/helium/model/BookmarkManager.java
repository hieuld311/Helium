package com.hieuld.helium.model;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.hieuld.helium.db.BookmarksTable;

/**
 * Manages CRUD operations for user bookmarks stored in SQLite.
 */
public class BookmarkManager {
    public static final long NOT_FOUND = -1;
    private final SQLiteDatabase mDb;

    public interface BookmarkMatcher {
        boolean matches(String position);
    }

    public BookmarkManager(SQLiteDatabase db) {
        this.mDb = db;
    }

    public long findBookmark(long bookId, BookmarkMatcher matcher) {
        try (Cursor cursor = mDb.query(BookmarksTable.TABLE_NAME,
                new String[]{"_id", "position"}, "book_id=?",
                new String[]{String.valueOf(bookId)}, null, null, null)) {

            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                String position = cursor.getString(1);
                if (matcher.matches(position)) {
                    return id;
                }
            }
        }
        return NOT_FOUND;
    }

    public long insertBookmark(long bookId, String position, String sectionTitle, int page) {
        ContentValues values = new ContentValues();
        values.put("book_id", bookId);
        values.put("position", position);
        values.put("section_title", sectionTitle);
        values.put(BookmarksTable.COLUMN_PAGE, page);
        values.put("timestamp", System.currentTimeMillis());
        return mDb.insert(BookmarksTable.TABLE_NAME, null, values);
    }

    public void deleteBookmark(long bookId, BookmarkMatcher matcher) {
        try (Cursor cursor = mDb.query(BookmarksTable.TABLE_NAME,
                new String[]{"_id", "position"}, "book_id=?",
                new String[]{String.valueOf(bookId)}, null, null, null)) {

            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                String position = cursor.getString(1);
                if (matcher.matches(position)) {
                    deleteBookmark(id);
                }
            }
        }
    }

    public void deleteBookmark(long bookmarkId) {
        mDb.delete(BookmarksTable.TABLE_NAME, "_id=?", new String[]{String.valueOf(bookmarkId)});
    }
}
