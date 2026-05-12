package com.hieuld.helium.db;

import android.provider.BaseColumns;

public class SearchHistoryTable implements BaseColumns {
    public static final String COLUMN_FIELD = "field";
    public static final String COLUMN_LAST_SEARCH = "last_search";
    public static final String COLUMN_QUERY = "query";
    public static final String TABLE_NAME = "search_history";
}
