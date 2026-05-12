package com.hieuld.helium.db;

import android.provider.BaseColumns;

public class BooksTable implements BaseColumns {
    public static final String TABLE_NAME = "books";

    public static final String COLUMN_FILE_PATH = "file_path";
    public static final String COLUMN_TITLE = "title";
    public static final String COLUMN_CREATOR = "creator";
    public static final String COLUMN_COVER = "cover";
    public static final String COLUMN_COVER_BACKGROUND = "cover_bg";
    public static final String COLUMN_FOLDER = "folder";
    public static final String COLUMN_HIDDEN = "hidden";
    public static final String COLUMN_HASH = "hash";
    public static final String COLUMN_COPIED = "copied";
    public static final String COLUMN_ADDED_DATE = "added_date";
    public static final String COLUMN_GROUP_ADDED_DATE = "group_added_date";
    public static final String COLUMN_LAST_OPEN_DATE = "last_open_date";
    public static final String COLUMN_CURRENT_POSITION = "current_position";
    public static final String COLUMN_CURRENT_POSITION_TIMESTAMP = "current_position_timestamp";
    public static final String COLUMN_LIBRARY_VERSION = "library_version";

    public static final int CURRENT_LIBRARY_VERSION = 3;

    public static class Deprecated {
        public static final String COLUMN_CURRENT_FILE = "current_file";
        public static final String COLUMN_CURRENT_PROGRESS = "current_progress";
    }
}
