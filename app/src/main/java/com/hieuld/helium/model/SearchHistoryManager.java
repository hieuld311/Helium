package com.hieuld.helium.model;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.hieuld.helium.db.SearchHistoryTable;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages search history entries: insert/update on submit, query, and clear.
 */
public class SearchHistoryManager {
    private final SQLiteDatabase mDb;
    private final String mField;

    public SearchHistoryManager(SQLiteDatabase db, String field) {
        this.mDb = db;
        this.mField = field;
    }

    public void submitQuery(String query) {
        try (Cursor cursor = mDb.query(SearchHistoryTable.TABLE_NAME,
                new String[]{"_id"}, "query=? AND field=?",
                new String[]{query, mField}, null, null, null)) {

            if (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                ContentValues values = new ContentValues();
                values.put(SearchHistoryTable.COLUMN_LAST_SEARCH, System.currentTimeMillis());
                mDb.update(SearchHistoryTable.TABLE_NAME, values, "_id=?", new String[]{String.valueOf(id)});
                return;
            }
        }

        ContentValues newValues = new ContentValues();
        newValues.put("query", query);
        newValues.put(SearchHistoryTable.COLUMN_FIELD, mField);
        newValues.put(SearchHistoryTable.COLUMN_LAST_SEARCH, System.currentTimeMillis());
        mDb.insert(SearchHistoryTable.TABLE_NAME, null, newValues);
    }

    public List<String> getQueries() {
        List<String> queries = new ArrayList<>();
        try (Cursor cursor = mDb.query(SearchHistoryTable.TABLE_NAME,
                new String[]{"query"}, "field=?",
                new String[]{mField}, null, null, "last_search DESC")) {

            while (cursor.moveToNext()) {
                queries.add(cursor.getString(0));
            }
        }
        return queries;
    }

    public boolean clearHistory() {
        return mDb.delete(SearchHistoryTable.TABLE_NAME, "field=?", new String[]{mField}) > 0;
    }
}
