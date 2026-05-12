package com.hieuld.helium.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.hieuld.helium.content.EPubBookView;
import com.hieuld.helium.themes.ThemeManager;

import java.util.UUID;

public class DatabaseOpenHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "lithium.db";
    public static final int DATABASE_VERSION = 1;
    private static final String SQL_CREATE_INDEX_BOOK_CATEGORY_LINKS_BOOK_ID = "CREATE INDEX index_catlinks_book_id ON book_category_links (book_id);";
    private static final String SQL_CREATE_INDEX_BOOK_CATEGORY_LINKS_CATEGORY_ID = "CREATE INDEX index_catlinks_category_id ON book_category_links (category_id);";
    private static final String SQL_CREATE_INDEX_HIGHLIGHTS_BOOK_ID = "CREATE INDEX index_anno_book_id ON highlights (book_id);";
    private static final String SQL_CREATE_INDEX_HIGHLIGHTS_URL = "CREATE INDEX index_anno_url ON highlights (url);";
    private static final String SQL_CREATE_INDEX_SYNC_DELETIONS_SYNC_ID = "CREATE INDEX index_sync_deletions_sync_id ON sync_deletions (sync_id);";
    private static final String SQL_CREATE_TABLE_BOOKMARKS = "CREATE TABLE bookmarks (_id integer primary key,_sync_id text,book_id integer,position text,section_title text,page integer,timestamp integer);";
    private static final String SQL_CREATE_TABLE_BOOKS = "CREATE TABLE books (_id integer primary key,file_path text,folder text,title text,creator text,cover text,current_position text,current_position_timestamp integer default 0,cover_bg integer default -2,hidden integer default 0,added_date integer,group_added_date integer,last_open_date integer default 0,hash text default null,copied integer default 0,library_version integer default 3);";
    private static final String SQL_CREATE_TABLE_BOOK_CATEGORY_LINKS = "CREATE TABLE book_category_links (_id integer primary key,_sync_id text,book_id integer,category_id integer);";
    private static final String SQL_CREATE_TABLE_CATEGORIES = "CREATE TABLE categories (_id integer primary key,_sync_id text,name text,created_date integer,modified_date integer);";
    private static final String SQL_CREATE_TABLE_EPUB_PAGE_MAP = "CREATE TABLE epub_page_map (_id integer primary key,book_id integer,spine_file text,page_start integer,page_count integer);";
    private static final String SQL_CREATE_TABLE_HIGHLIGHTS = "CREATE TABLE highlights (_id integer primary key,_sync_id text,book_id integer,url text,range text,type integer,type_timestamp integer,selected_text text,section_title text,color integer,color_timestamp integer,note text default null,note_timestamp integer,created_date integer,edited_date integer default 0);";
    private static final String SQL_CREATE_TABLE_LIBRARY_FOLDERS = "CREATE TABLE library_folders (_id integer primary key,folder text);";
    private static final String SQL_CREATE_TABLE_SCAN_FAILURES = "CREATE TABLE scan_failures (_id integer primary key,file_path text,last_attempt_ver integer);";
    private static final String SQL_CREATE_TABLE_SEARCH_HISTORY = "CREATE TABLE search_history (_id integer primary key,query text,field text,last_search integer);";
    private static final String SQL_CREATE_TABLE_SYNC_DELETIONS = "CREATE TABLE sync_deletions (_id integer primary key,table_name text,sync_id text,device_id text,timestamp integer);";
    private static final String SQL_CREATE_TABLE_SYNC_DEVICES = "CREATE TABLE sync_devices (_id integer primary key,device_id text,model text,added_date integer,last_timestamp integer);";
    private static final String SQL_CREATE_TABLE_THEMES = "CREATE TABLE themes (_id integer primary key,_sync_id text,name text,created_date integer,modified_date integer,builtin integer default 0,hidden integer default 0,position integer,bg_color integer,bg_color_timestamp integer,text_color integer,text_color_timestamp integer,link_color integer,link_color_timestamp integer,use_dark_chrome integer,use_dark_chrome_timestamp integer);";
    private static final String SQL_CREATE_TRIGGER_DELETE_CATEGORY = "CREATE TRIGGER delete_category_clear_books AFTER DELETE ON categories FOR EACH ROW BEGIN DELETE FROM book_category_links WHERE category_id = OLD._id; END";

    public DatabaseOpenHelper(Context context) {
        super(context, DATABASE_NAME, (SQLiteDatabase.CursorFactory) null, 34);
    }

    public DatabaseOpenHelper(Context context, String str) {
        super(context, str, (SQLiteDatabase.CursorFactory) null, 34);
    }

    @Override // android.database.sqlite.SQLiteOpenHelper
    public void onCreate(SQLiteDatabase sQLiteDatabase) {
        sQLiteDatabase.execSQL(SQL_CREATE_TABLE_BOOKS);
        sQLiteDatabase.execSQL(SQL_CREATE_TABLE_HIGHLIGHTS);
        sQLiteDatabase.execSQL(SQL_CREATE_INDEX_HIGHLIGHTS_BOOK_ID);
        sQLiteDatabase.execSQL(SQL_CREATE_INDEX_HIGHLIGHTS_URL);
        sQLiteDatabase.execSQL(SQL_CREATE_TABLE_CATEGORIES);
        sQLiteDatabase.execSQL(SQL_CREATE_TABLE_BOOK_CATEGORY_LINKS);
        sQLiteDatabase.execSQL(SQL_CREATE_INDEX_BOOK_CATEGORY_LINKS_BOOK_ID);
        sQLiteDatabase.execSQL(SQL_CREATE_INDEX_BOOK_CATEGORY_LINKS_CATEGORY_ID);
        sQLiteDatabase.execSQL(SQL_CREATE_TRIGGER_DELETE_CATEGORY);
        sQLiteDatabase.execSQL(SQL_CREATE_TABLE_BOOKMARKS);
        sQLiteDatabase.execSQL(SQL_CREATE_TABLE_EPUB_PAGE_MAP);
        sQLiteDatabase.execSQL(SQL_CREATE_TABLE_SCAN_FAILURES);
        sQLiteDatabase.execSQL(SQL_CREATE_TABLE_THEMES);
        sQLiteDatabase.execSQL(SQL_CREATE_TABLE_SEARCH_HISTORY);
        sQLiteDatabase.execSQL(SQL_CREATE_TABLE_SYNC_DEVICES);
        sQLiteDatabase.execSQL(SQL_CREATE_TABLE_SYNC_DELETIONS);
        sQLiteDatabase.execSQL(SQL_CREATE_INDEX_SYNC_DELETIONS_SYNC_ID);
        sQLiteDatabase.execSQL(SQL_CREATE_TABLE_LIBRARY_FOLDERS);

        ThemeManager.createBuiltinThemes(sQLiteDatabase, false);

        addSyncIdIndex(sQLiteDatabase, AnnotationsTable.TABLE_NAME);
        addSyncIdIndex(sQLiteDatabase, BookmarksTable.TABLE_NAME);
        addSyncIdIndex(sQLiteDatabase, CategoriesTable.TABLE_NAME);
        addSyncIdIndex(sQLiteDatabase, BookCategoryLinksTable.TABLE_NAME);
    }

    @Override // android.database.sqlite.SQLiteOpenHelper
    public void onUpgrade(SQLiteDatabase sQLiteDatabase, int i, int i2) {
        if (i < 4) {
            sQLiteDatabase.execSQL(SQL_CREATE_TABLE_HIGHLIGHTS);
            sQLiteDatabase.execSQL(SQL_CREATE_INDEX_HIGHLIGHTS_BOOK_ID);
            sQLiteDatabase.execSQL(SQL_CREATE_INDEX_HIGHLIGHTS_URL);
        }
        if (i < 6) {
            sQLiteDatabase.execSQL("ALTER TABLE books ADD COLUMN added_date integer");
            sQLiteDatabase.execSQL("ALTER TABLE books ADD COLUMN last_open_date integer default 0");
        }
        if (i < 8) {
            sQLiteDatabase.execSQL(SQL_CREATE_TABLE_CATEGORIES);
            sQLiteDatabase.execSQL(SQL_CREATE_TABLE_BOOK_CATEGORY_LINKS);
            sQLiteDatabase.execSQL(SQL_CREATE_INDEX_BOOK_CATEGORY_LINKS_BOOK_ID);
            sQLiteDatabase.execSQL(SQL_CREATE_INDEX_BOOK_CATEGORY_LINKS_CATEGORY_ID);
        }
        if (i < 9) {
            sQLiteDatabase.execSQL("ALTER TABLE books ADD COLUMN current_progress integer default 0");
        }
        if (i < 10) {
            sQLiteDatabase.execSQL(SQL_CREATE_TRIGGER_DELETE_CATEGORY);
        }
        if (i < 11) {
            sQLiteDatabase.execSQL("ALTER TABLE books ADD COLUMN hash text default null");
        }
        if (i < 12) {
            sQLiteDatabase.execSQL("ALTER TABLE books ADD COLUMN copied integer default 0");
        }
        if (i < 13) {
            sQLiteDatabase.execSQL("UPDATE highlights SET type=1,color=16219260 WHERE color=-1");
        }
        if (i < 14) {
            sQLiteDatabase.execSQL("ALTER TABLE books ADD COLUMN folder text default null");
            Cursor cursorRawQuery = sQLiteDatabase.rawQuery("SELECT _id,file_path FROM books", null);
            while (cursorRawQuery.moveToNext()) {
                long j = cursorRawQuery.getLong(0);
                String string = cursorRawQuery.getString(1);
                String strSubstring = string.substring(0, string.lastIndexOf("/"));
                ContentValues contentValues = new ContentValues();
                contentValues.put("folder", strSubstring);
                sQLiteDatabase.update(BooksTable.TABLE_NAME, contentValues, "_id=?", new String[]{String.valueOf(j)});
            }
            cursorRawQuery.close();
        }
        if (i < 15) {
            sQLiteDatabase.execSQL("ALTER TABLE books ADD COLUMN current_position text");
            Cursor cursorQuery = sQLiteDatabase.query(BooksTable.TABLE_NAME, new String[]{"_id", BooksTable.Deprecated.COLUMN_CURRENT_FILE, BooksTable.Deprecated.COLUMN_CURRENT_PROGRESS}, null, null, null, null, null);
            while (cursorQuery.moveToNext()) {
                long j2 = cursorQuery.getLong(0);
                String string2 = cursorQuery.getString(1);
                float f = cursorQuery.getFloat(2);
                if (string2 != null) {
                    ContentValues contentValues2 = new ContentValues();
                    contentValues2.put(BooksTable.COLUMN_CURRENT_POSITION, EPubBookView.convertOldPosition(string2, f));
                    sQLiteDatabase.update(BooksTable.TABLE_NAME, contentValues2, "_id=?", new String[]{String.valueOf(j2)});
                }
            }
            cursorQuery.close();
        }
        if (i < 16) {
            sQLiteDatabase.execSQL(SQL_CREATE_TABLE_BOOKMARKS);
        }
        if (i < 17) {
            sQLiteDatabase.execSQL(SQL_CREATE_TABLE_EPUB_PAGE_MAP);
        }
        if (i < 18 && i >= 16) {
            sQLiteDatabase.execSQL("ALTER TABLE bookmarks ADD COLUMN page integer default -1");
        }
        if (i < 19 && i >= 4) {
            sQLiteDatabase.execSQL("ALTER TABLE highlights ADD COLUMN selected_text text default null");
            sQLiteDatabase.execSQL("ALTER TABLE highlights ADD COLUMN section_title text default null");
        }
        if (i < 20) {
            String[] strArr2 = new String[]{"_id", BooksTable.COLUMN_TITLE, "file_path"};
            Cursor cursorQuery2 = sQLiteDatabase.query(BooksTable.TABLE_NAME, strArr2, null, null, null, null, null);
            while (cursorQuery2.moveToNext()) {
                long j3 = cursorQuery2.getLong(0);
                if (cursorQuery2.getString(1).trim().isEmpty()) {
                    String string3 = cursorQuery2.getString(2);
                    String strSubstring2 = string3.substring(string3.lastIndexOf("/") + 1);
                    String strSubstring3 = strSubstring2.substring(0, strSubstring2.lastIndexOf(".epub"));
                    ContentValues contentValues3 = new ContentValues();
                    contentValues3.put(BooksTable.COLUMN_TITLE, strSubstring3);
                    sQLiteDatabase.update(BooksTable.TABLE_NAME, contentValues3, "_id=?", new String[]{String.valueOf(j3)});
                }
            }
            cursorQuery2.close();
        }
        if (i < 21) {
            sQLiteDatabase.execSQL(SQL_CREATE_TABLE_SCAN_FAILURES);
        }
        if (i < 22) {
            sQLiteDatabase.execSQL("ALTER TABLE books ADD COLUMN library_version integer default 3");
            ContentValues contentValues4 = new ContentValues();
            contentValues4.put(BooksTable.COLUMN_LIBRARY_VERSION, 0);
            sQLiteDatabase.update(BooksTable.TABLE_NAME, contentValues4, null, null);
        }
        if (i < 23) {
            sQLiteDatabase.execSQL(SQL_CREATE_TABLE_THEMES);
            ThemeManager.createBuiltinThemes(sQLiteDatabase, false);
        }
        if (i < 24) {
            sQLiteDatabase.execSQL(SQL_CREATE_TABLE_SEARCH_HISTORY);
        }
        if (i < 25) {
            sQLiteDatabase.execSQL("ALTER TABLE books ADD COLUMN group_added_date integer default 0");
            sQLiteDatabase.execSQL("UPDATE books SET group_added_date = added_date WHERE group_added_date = 0");
        }
        if (i < 26) {
            sQLiteDatabase.execSQL(SQL_CREATE_TABLE_SYNC_DEVICES);
            sQLiteDatabase.execSQL(SQL_CREATE_TABLE_SYNC_DELETIONS);
            sQLiteDatabase.execSQL(SQL_CREATE_INDEX_SYNC_DELETIONS_SYNC_ID);
        }
        if (i < 27 && i >= 4) {
            sQLiteDatabase.beginTransaction();
            try {
                makeTableSyncable(sQLiteDatabase, AnnotationsTable.TABLE_NAME);
                sQLiteDatabase.execSQL("ALTER TABLE highlights ADD COLUMN type_timestamp integer default 0");
                sQLiteDatabase.execSQL("ALTER TABLE highlights ADD COLUMN color_timestamp integer default 0");
                sQLiteDatabase.execSQL("ALTER TABLE highlights ADD COLUMN note_timestamp integer default 0");
                sQLiteDatabase.setTransactionSuccessful();
            } finally {
                sQLiteDatabase.endTransaction();
            }
        }
        if (i < 28 && i >= 16) {
            makeTableSyncable(sQLiteDatabase, BookmarksTable.TABLE_NAME);
        }
        if (i < 29 && i >= 8) {
            sQLiteDatabase.beginTransaction();
            try {
                makeTableSyncable(sQLiteDatabase, CategoriesTable.TABLE_NAME);
                makeTableSyncable(sQLiteDatabase, BookCategoryLinksTable.TABLE_NAME);
                sQLiteDatabase.execSQL("ALTER TABLE categories ADD COLUMN modified_date integer default 0");
                sQLiteDatabase.setTransactionSuccessful();
            } finally {
                sQLiteDatabase.endTransaction();
            }
        }
        if (i < 30 && i >= 23) {
            sQLiteDatabase.beginTransaction();
            try {
                sQLiteDatabase.execSQL("ALTER TABLE themes ADD COLUMN _sync_id text");
                sQLiteDatabase.execSQL("ALTER TABLE themes ADD COLUMN bg_color_timestamp integer default 0");
                sQLiteDatabase.execSQL("ALTER TABLE themes ADD COLUMN text_color_timestamp integer default 0");
                sQLiteDatabase.execSQL("ALTER TABLE themes ADD COLUMN link_color_timestamp integer default 0");
                sQLiteDatabase.execSQL("ALTER TABLE themes ADD COLUMN use_dark_chrome_timestamp integer default 0");
                addSyncIdIndex(sQLiteDatabase, ThemesTable.TABLE_NAME);

                Cursor cursorRawQuery2 = sQLiteDatabase.rawQuery("SELECT _id,builtin FROM themes ORDER BY _id ASC", null);
                ContentValues contentValues5 = new ContentValues();

                boolean isNightAmoledMissing = true;
                boolean isNightMissing = true;
                boolean isSepiaMissing = true;
                int builtinCount = 0;

                while (cursorRawQuery2.moveToNext()) {
                    long j4 = cursorRawQuery2.getLong(0);
                    String strNewSyncId;

                    if (cursorRawQuery2.getInt(1) == 1) { // 1 means it is a builtin theme
                        builtinCount++;
                        if (builtinCount == 1) {
                            strNewSyncId = ThemesTable.NIGHT_AMOLED_BUILTIN_ID;
                            isNightAmoledMissing = false;
                        } else if (builtinCount == 2) {
                            strNewSyncId = ThemesTable.NIGHT_BUILTIN_ID;
                            isNightMissing = false;
                        } else {
                            strNewSyncId = ThemesTable.SEPIA_BUILTIN_ID;
                            isSepiaMissing = false;
                        }
                    } else {
                        strNewSyncId = UUID.randomUUID().toString();
                    }

                    contentValues5.put(SyncBaseColumns._SYNC_ID, strNewSyncId);
                    sQLiteDatabase.update(ThemesTable.TABLE_NAME, contentValues5, "_id=?", new String[]{String.valueOf(j4)});
                    contentValues5.clear();
                }
                cursorRawQuery2.close();

                ThemeManager.addBuiltinThemesAsHidden(sQLiteDatabase, isNightAmoledMissing, isNightMissing, isSepiaMissing);
                sQLiteDatabase.setTransactionSuccessful();
            } finally {
                sQLiteDatabase.endTransaction();
            }
        }
        if (i < 31) {
            sQLiteDatabase.beginTransaction();
            try {
                sQLiteDatabase.execSQL("ALTER TABLE books ADD COLUMN current_position_timestamp integer default 0");
                sQLiteDatabase.execSQL("UPDATE books SET current_position_timestamp = last_open_date");
                sQLiteDatabase.setTransactionSuccessful();
            } finally {
                sQLiteDatabase.endTransaction();
            }
        }
        if (i < 32) {
            sQLiteDatabase.execSQL("UPDATE sync_devices SET last_timestamp=0");
        }
        if (i < 33) {
            ThemeManager.removeDuplicatedBuiltinThemes(sQLiteDatabase);
        }
        if (i < 34) {
            sQLiteDatabase.execSQL(SQL_CREATE_TABLE_LIBRARY_FOLDERS);
        }
    }

    public static void makeTableSyncable(SQLiteDatabase sQLiteDatabase, String str) {
        sQLiteDatabase.beginTransaction();
        try {
            sQLiteDatabase.execSQL("ALTER TABLE " + str + " ADD COLUMN _sync_id text");
            addSyncIdIndex(sQLiteDatabase, str);
            Cursor cursorRawQuery = sQLiteDatabase.rawQuery("SELECT _id FROM " + str, null);
            ContentValues contentValues = new ContentValues();
            while (cursorRawQuery.moveToNext()) {
                long j = cursorRawQuery.getLong(0);
                contentValues.put(SyncBaseColumns._SYNC_ID, UUID.randomUUID().toString());
                sQLiteDatabase.update(str, contentValues, "_id=?", new String[]{String.valueOf(j)});
                contentValues.clear();
            }
            cursorRawQuery.close();
            sQLiteDatabase.setTransactionSuccessful();
        } finally {
            sQLiteDatabase.endTransaction();
        }
    }

    private static void addSyncIdIndex(SQLiteDatabase sQLiteDatabase, String str) {
        sQLiteDatabase.execSQL("CREATE INDEX index_" + str + "_sync_id ON " + str + " (_sync_id)");
    }
}