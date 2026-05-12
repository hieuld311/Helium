package com.hieuld.helium.db;

import android.provider.BaseColumns;

public class SyncDeletionsTable implements BaseColumns {
    public static final String COLUMN_DEVICE_ID = "device_id";
    public static final String COLUMN_SYNC_ID = "sync_id";
    public static final String COLUMN_TABLE = "table_name";
    public static final String COLUMN_TIMESTAMP = "timestamp";
    public static final String TABLE_NAME = "sync_deletions";
}
