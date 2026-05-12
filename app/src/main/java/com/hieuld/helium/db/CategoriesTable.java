package com.hieuld.helium.db;

import android.provider.BaseColumns;

public class CategoriesTable implements BaseColumns, SyncBaseColumns {
    public static final String TABLE_NAME = "categories";
    public static final String COLUMN_NAME = "name";
    public static final String COLUMN_CREATED_DATE = "created_date";
    public static final String COLUMN_MODIFIED_DATE = "modified_date";
}
