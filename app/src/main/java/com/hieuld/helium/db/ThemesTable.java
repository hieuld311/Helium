package com.hieuld.helium.db;

import android.provider.BaseColumns;

public class ThemesTable implements BaseColumns {
    public static final String TABLE_NAME = "themes";

    // Column for the internal _sync_id field (kept for DB schema compatibility)
    public static final String COLUMN_INTERNAL_ID = "_sync_id";

    // Stable IDs used to identify built-in themes in DB
    public static final String NIGHT_BUILTIN_ID = "04fd477e-bdbb-4dea-8f38-6bead547a00b";
    public static final String NIGHT_AMOLED_BUILTIN_ID = "f9715217-d3bb-41e3-974c-71e0ffaeee0b";
    public static final String SEPIA_BUILTIN_ID = "4948c360-f7cb-42b7-af6a-cf3431145f41";

    public static final String COLUMN_NAME = "name";
    public static final String COLUMN_POSITION = "position";
    public static final String COLUMN_HIDDEN = "hidden";
    public static final String COLUMN_BUILTIN = "builtin";
    public static final String COLUMN_CREATED_DATE = "created_date";
    public static final String COLUMN_MODIFIED_DATE = "modified_date";
    public static final String COLUMN_BG_COLOR = "bg_color";
    public static final String COLUMN_BG_COLOR_TIMESTAMP = "bg_color_timestamp";
    public static final String COLUMN_TEXT_COLOR = "text_color";
    public static final String COLUMN_TEXT_COLOR_TIMESTAMP = "text_color_timestamp";
    public static final String COLUMN_LINK_COLOR = "link_color";
    public static final String COLUMN_LINK_COLOR_TIMESTAMP = "link_color_timestamp";
    public static final String COLUMN_USE_DARK_CHROME = "use_dark_chrome";
    public static final String COLUMN_USE_DARK_CHROME_TIMESTAMP = "use_dark_chrome_timestamp";
}
