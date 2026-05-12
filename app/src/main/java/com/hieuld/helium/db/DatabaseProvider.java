package com.hieuld.helium.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

public class DatabaseProvider {
    private static SQLiteDatabase sDatabase;
    private static final Object sLock = new Object();

    public static SQLiteDatabase getDatabase(Context context) {
        if (sDatabase == null) {
            synchronized (sLock) {
                if (sDatabase == null) {
                    sDatabase = new DatabaseOpenHelper(context.getApplicationContext()).getWritableDatabase();
                }
            }
        }
        return sDatabase;
    }
}
