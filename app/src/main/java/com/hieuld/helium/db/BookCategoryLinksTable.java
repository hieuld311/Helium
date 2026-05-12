package com.hieuld.helium.db;

import android.provider.BaseColumns;

public class BookCategoryLinksTable implements BaseColumns, SyncBaseColumns {
    public static final String TABLE_NAME = "book_category_links";
    public static final String COLUMN_BOOK_ID = "book_id";
    public static final String COLUMN_CATEGORY_ID = "category_id";
}
