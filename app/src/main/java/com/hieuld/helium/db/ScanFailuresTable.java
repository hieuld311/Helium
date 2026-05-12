package com.hieuld.helium.db;

import android.provider.BaseColumns;

public class ScanFailuresTable implements BaseColumns {
    public static final String COLUMN_FILE_PATH = "file_path";
    public static final String COLUMN_LAST_ATTEMPT_VER = "last_attempt_ver";
    public static final String TABLE_NAME = "scan_failures";
}
