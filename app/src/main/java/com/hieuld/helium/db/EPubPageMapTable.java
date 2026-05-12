package com.hieuld.helium.db;

import android.provider.BaseColumns;

public class EPubPageMapTable implements BaseColumns {
    public static final String COLUMN_BOOK_ID = "book_id";
    public static final String COLUMN_PAGE_COUNT = "page_count";
    public static final String COLUMN_PAGE_START = "page_start";
    public static final String COLUMN_SPINE_FILE = "spine_file";
    public static final String TABLE_NAME = "epub_page_map";
}
