package com.hieuld.helium.db;

import android.provider.BaseColumns;

public class AnnotationsTable implements BaseColumns, SyncBaseColumns {
    public static final String TABLE_NAME = "highlights";

    public static final String COLUMN_BOOK_ID = "book_id";
    public static final String COLUMN_URL = "url";
    public static final String COLUMN_RANGE = "range";
    public static final String COLUMN_TEXT = "selected_text";
    public static final String COLUMN_TYPE = "type";
    public static final String COLUMN_TYPE_TIMESTAMP = "type_timestamp";
    public static final String COLUMN_COLOR = "color";
    public static final String COLUMN_COLOR_TIMESTAMP = "color_timestamp";
    public static final String COLUMN_NOTE = "note";
    public static final String COLUMN_NOTE_TIMESTAMP = "note_timestamp";
    public static final String COLUMN_CREATED_DATE = "created_date";
    public static final String COLUMN_EDITED_DATE = "edited_date";
    public static final String COLUMN_SECTION_TITLE = "section_title";
}
