package com.hieuld.helium.db;

import android.provider.BaseColumns;

public class SyncDevicesTable implements BaseColumns {
    public static final String COLUMN_ADDED_DATE = "added_date";
    public static final String COLUMN_ID = "device_id";
    public static final String COLUMN_LAST_TIMESTAMP = "last_timestamp";
    public static final String COLUMN_MODEL = "model";
    public static final String TABLE_NAME = "sync_devices";
}
