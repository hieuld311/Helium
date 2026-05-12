package com.hieuld.helium.db;

import android.provider.BaseColumns;

public class BookmarksTable implements BaseColumns {
    public static final String TABLE_NAME = "bookmarks";

    public static final String COLUMN_BOOK_ID = "book_id";
    public static final String COLUMN_POSITION = "position";
    public static final String COLUMN_SECTION_TITLE = "section_title";
    public static final String COLUMN_TIMESTAMP = "timestamp";
    public static final String COLUMN_PAGE = "page";
}
