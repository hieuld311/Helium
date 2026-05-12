package com.hieuld.helium.library;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.AttributeSet;

import androidx.preference.DialogPreference;

import com.hieuld.helium.R;
import com.hieuld.helium.db.DatabaseProvider;
import com.hieuld.helium.db.LibraryFoldersTable;

public class LibraryFolderPreference extends DialogPreference {
    private SQLiteDatabase mDb;
    private String mPath;

    public LibraryFolderPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public LibraryFolderPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public LibraryFolderPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LibraryFolderPreference(Context context) {
        super(context);
        init();
    }

    private void init() {
        setPersistent(false);
        setDialogLayoutResource(R.layout.dialog_library_folder);
        setDialogTitle(R.string.pref_library_folder_title);
        setPositiveButtonText(R.string.pref_library_folder_save);
        setNegativeButtonText(R.string.pref_library_folder_cancel);

        this.mDb = DatabaseProvider.getDatabase(getContext());

        // Tối ưu hóa đóng Cursor bằng try-with-resources
        try (Cursor cursor = this.mDb.rawQuery("SELECT folder FROM library_folders", null)) {
            if (cursor.moveToFirst()) {
                this.mPath = cursor.getString(0);
            } else {
                this.mPath = null;
            }
        }
        updateSummary();
    }

    public void setPath(String path) {
        if (path != null) {
            ContentValues values = new ContentValues();
            values.put("folder", path);
            if (this.mPath != null && !path.equals(this.mPath)) {
                this.mDb.update(LibraryFoldersTable.TABLE_NAME, values, null, null);
            } else {
                this.mDb.insert(LibraryFoldersTable.TABLE_NAME, null, values);
            }
        } else if (this.mPath != null) {
            this.mDb.delete(LibraryFoldersTable.TABLE_NAME, null, null);
        }

        this.mPath = path;
        updateSummary();
        LibraryUpdate.cancelCurrentUpdate();
    }

    private void updateSummary() {
        String summary = this.mPath;
        if (summary == null) {
            summary = getContext().getString(R.string.pref_library_folder_none);
        }
        setSummary(summary);
    }

    public String getPath() {
        return this.mPath;
    }
}