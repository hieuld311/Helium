package com.hieuld.helium.themes;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import androidx.annotation.IntDef;
import androidx.core.graphics.ColorUtils;
import androidx.preference.PreferenceManager;

import com.hieuld.helium.R;
import com.hieuld.helium.db.DatabaseProvider;
import com.hieuld.helium.db.ThemesTable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

public class ThemeManager {
    private static final String TAG = "ThemeManager";

    public static final int BACKGROUND_COLOR = 0;
    public static final int TEXT_COLOR = 1;
    public static final int LINK_COLOR = 2;

    @IntDef({BACKGROUND_COLOR, TEXT_COLOR, LINK_COLOR})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ColorField {}

    private static volatile ThemeManager sInstance;
    private static final Object sInstanceLock = new Object();

    private final Context mContext;
    private final SQLiteDatabase mDb;
    private final SharedPreferences mPrefs;

    private static final String[] SELECTION = {
            "_id",
            "name",
            ThemesTable.COLUMN_BUILTIN,
            ThemesTable.COLUMN_BG_COLOR,
            ThemesTable.COLUMN_TEXT_COLOR,
            ThemesTable.COLUMN_LINK_COLOR,
            ThemesTable.COLUMN_USE_DARK_CHROME
    };

    private ThemeManager(Context context, SQLiteDatabase db) {
        this.mContext = context.getApplicationContext();
        this.mPrefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        this.mDb = db;
    }

    public List<Theme> getThemes() {
        List<Theme> themes = new ArrayList<>();
        try (Cursor cursor = this.mDb.query(ThemesTable.TABLE_NAME, SELECTION,
                "hidden=0", null, null, null, "position ASC, created_date ASC")) {
            while (cursor.moveToNext()) {
                themes.add(buildThemeFromCursor(cursor));
            }
        }
        return themes;
    }

    public Theme getTheme(long themeId) {
        if (themeId == 0) return null;
        try (Cursor cursor = this.mDb.query(ThemesTable.TABLE_NAME, SELECTION,
                "_id=?", new String[]{String.valueOf(themeId)}, null, null, null)) {
            return cursor.moveToFirst() ? buildThemeFromCursor(cursor) : null;
        }
    }

    public Theme getTheme() {
        return getTheme(getThemeId());
    }

    public void setTheme(long themeId) {
        this.mPrefs.edit().putLong("current_theme", themeId).apply();
    }

    public long getThemeId() {
        return this.mPrefs.getLong("current_theme", 0L);
    }

    public void updateThemeColor(long themeId, @ColorField int colorType, int colorValue) {
        String colorColumn;
        String timestampColumn;

        if (colorType == TEXT_COLOR) {
            colorColumn = ThemesTable.COLUMN_TEXT_COLOR;
            timestampColumn = ThemesTable.COLUMN_TEXT_COLOR_TIMESTAMP;
        } else if (colorType == LINK_COLOR) {
            colorColumn = ThemesTable.COLUMN_LINK_COLOR;
            timestampColumn = ThemesTable.COLUMN_LINK_COLOR_TIMESTAMP;
        } else {
            colorColumn = ThemesTable.COLUMN_BG_COLOR;
            timestampColumn = ThemesTable.COLUMN_BG_COLOR_TIMESTAMP;
        }

        long currentTime = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put(colorColumn, colorValue);
        values.put(timestampColumn, currentTime);
        values.put("modified_date", currentTime);
        this.mDb.update(ThemesTable.TABLE_NAME, values, "_id=?", new String[]{String.valueOf(themeId)});
    }

    public void updateThemeName(long themeId, String newName) {
        ContentValues values = new ContentValues();
        values.put("name", newName);
        values.put("modified_date", System.currentTimeMillis());
        this.mDb.update(ThemesTable.TABLE_NAME, values, "_id=?", new String[]{String.valueOf(themeId)});
    }

    public void updateThemeDarkChrome(long themeId, boolean useDarkChrome) {
        long currentTime = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put(ThemesTable.COLUMN_USE_DARK_CHROME, useDarkChrome);
        values.put(ThemesTable.COLUMN_USE_DARK_CHROME_TIMESTAMP, currentTime);
        values.put("modified_date", currentTime);
        this.mDb.update(ThemesTable.TABLE_NAME, values, "_id=?", new String[]{String.valueOf(themeId)});
    }

    private Theme buildThemeFromCursor(Cursor cursor) {
        Theme theme = new Theme();
        theme.id = cursor.getLong(0);
        theme.name = cursor.getString(1);
        theme.builtin = cursor.getInt(2) == 1;
        theme.backgroundColor = cursor.getInt(3);
        theme.textColor = cursor.getInt(4);
        theme.linkColor = cursor.getInt(5);
        theme.darkChrome = cursor.getInt(6) == 1;
        theme.bgIsDark = ColorUtils.calculateLuminance(theme.backgroundColor | 0xFF000000) < 0.5d;

        if (theme.builtin) {
            switch (theme.name) {
                case "night":
                    theme.name = this.mContext.getString(R.string.theme_builtin_name_night);
                    break;
                case "night_amoled":
                    theme.name = this.mContext.getString(R.string.theme_builtin_name_night_amoled);
                    break;
                case "sepia":
                    theme.name = this.mContext.getString(R.string.theme_builtin_name_sepia);
                    break;
            }
        }
        return theme;
    }

    public long newThemeCloned(Theme theme, String newName) {
        return newTheme(newName, theme.backgroundColor, theme.textColor, theme.linkColor, theme.darkChrome);
    }

    public long newTheme(String name, int bgColor, int textColor, int linkColor, boolean useDarkChrome) {
        int position = 0;
        try (Cursor cursor = this.mDb.query(ThemesTable.TABLE_NAME, new String[]{"position"},
                null, null, null, null, "position DESC LIMIT 1")) {
            if (cursor.moveToFirst()) {
                position = cursor.getInt(0) + 1;
            }
        }

        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("created_date", System.currentTimeMillis());
        values.put(ThemesTable.COLUMN_BUILTIN, false);
        values.put("position", position);
        values.put(ThemesTable.COLUMN_BG_COLOR, bgColor);
        values.put(ThemesTable.COLUMN_TEXT_COLOR, textColor);
        values.put(ThemesTable.COLUMN_LINK_COLOR, linkColor);
        values.put(ThemesTable.COLUMN_USE_DARK_CHROME, useDarkChrome);

        return this.mDb.insert(ThemesTable.TABLE_NAME, null, values);
    }

    public boolean deleteTheme(long themeId) {
        boolean isBuiltin;

        try (Cursor cursor = this.mDb.query(ThemesTable.TABLE_NAME,
                new String[]{ThemesTable.COLUMN_BUILTIN},
                "_id=?", new String[]{String.valueOf(themeId)}, null, null, null)) {
            if (!cursor.moveToFirst()) return false;
            isBuiltin = cursor.getInt(0) == 1;
        }

        if (isBuiltin) {
            if (!deleteBuiltinTheme(themeId)) return false;
        } else {
            this.mDb.beginTransaction();
            try {
                if (this.mDb.delete(ThemesTable.TABLE_NAME, "_id=?", new String[]{String.valueOf(themeId)}) == 0) {
                    return false;
                }
                this.mDb.setTransactionSuccessful();
            } finally {
                this.mDb.endTransaction();
            }
        }

        if (getThemeId() == themeId) {
            setTheme(0L);
        }
        return true;
    }

    private boolean deleteBuiltinTheme(long themeId) {
        ContentValues values = new ContentValues();
        values.put("hidden", 1);
        values.put("modified_date", System.currentTimeMillis());
        return this.mDb.update(ThemesTable.TABLE_NAME, values, "_id=?", new String[]{String.valueOf(themeId)}) > 0;
    }

    public void resetToDefaults() {
        this.mDb.beginTransaction();
        try {
            this.mDb.delete(ThemesTable.TABLE_NAME, "1", null);
            createBuiltinThemes(this.mDb, false);
            this.mDb.setTransactionSuccessful();
        } finally {
            this.mDb.endTransaction();
        }
        setTheme(0L);
    }

    private static void createBuiltinTheme(SQLiteDatabase db, String name, String builtinId,
                                           int bgColor, int textColor, int linkColor, boolean useDarkChrome, int position, boolean isUpdate) {
        long currentTime = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put(ThemesTable.COLUMN_INTERNAL_ID, builtinId);
        values.put("name", name);
        values.put("created_date", currentTime);
        values.put(ThemesTable.COLUMN_BUILTIN, true);
        values.put("hidden", false);
        values.put("position", position);
        values.put(ThemesTable.COLUMN_BG_COLOR, bgColor);
        values.put(ThemesTable.COLUMN_TEXT_COLOR, textColor);
        values.put(ThemesTable.COLUMN_LINK_COLOR, linkColor);
        values.put(ThemesTable.COLUMN_USE_DARK_CHROME, useDarkChrome);

        if (isUpdate) {
            values.put("modified_date", currentTime);
            values.put(ThemesTable.COLUMN_BG_COLOR_TIMESTAMP, currentTime);
            values.put(ThemesTable.COLUMN_TEXT_COLOR_TIMESTAMP, currentTime);
            values.put(ThemesTable.COLUMN_LINK_COLOR_TIMESTAMP, currentTime);
            values.put(ThemesTable.COLUMN_USE_DARK_CHROME_TIMESTAMP, currentTime);
        }
        db.insert(ThemesTable.TABLE_NAME, null, values);
    }

    private static void createBuiltinThemeAsHidden(SQLiteDatabase db, String name, String builtinId,
                                                   int bgColor, int textColor, int linkColor, boolean useDarkChrome, int position) {
        long currentTime = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put(ThemesTable.COLUMN_INTERNAL_ID, builtinId);
        values.put("name", name);
        values.put("created_date", currentTime);
        values.put("modified_date", currentTime);
        values.put(ThemesTable.COLUMN_BUILTIN, true);
        values.put("hidden", true);
        values.put("position", position);
        values.put(ThemesTable.COLUMN_BG_COLOR, bgColor);
        values.put(ThemesTable.COLUMN_TEXT_COLOR, textColor);
        values.put(ThemesTable.COLUMN_LINK_COLOR, linkColor);
        values.put(ThemesTable.COLUMN_USE_DARK_CHROME, useDarkChrome);
        db.insert(ThemesTable.TABLE_NAME, null, values);
    }

    public static void createBuiltinThemes(SQLiteDatabase db, boolean isUpdate) {
        db.beginTransaction();
        try {
            createBuiltinTheme(db, "night_amoled", ThemesTable.NIGHT_AMOLED_BUILTIN_ID, 0x000000, 0xE0E0E0, 0xF0E069, true, 1, isUpdate);
            createBuiltinTheme(db, "night",        ThemesTable.NIGHT_BUILTIN_ID,        0x3B3B3B, 0xFFFFFF, 0xF0E069, true, 0, isUpdate);
            createBuiltinTheme(db, "sepia",        ThemesTable.SEPIA_BUILTIN_ID,        0xF5DEB3, 0x000000, 0x000000, false, 2, isUpdate);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public static void addBuiltinThemesAsHidden(SQLiteDatabase db, boolean addAmoled, boolean addNight, boolean addSepia) {
        if (addAmoled) createBuiltinThemeAsHidden(db, "night_amoled", ThemesTable.NIGHT_AMOLED_BUILTIN_ID, 0x000000, 0xE0E0E0, 0xF0E069, true, 1);
        if (addNight)  createBuiltinThemeAsHidden(db, "night",        ThemesTable.NIGHT_BUILTIN_ID,        0x3B3B3B, 0xFFFFFF, 0xF0E069, true, 0);
        if (addSepia)  createBuiltinThemeAsHidden(db, "sepia",        ThemesTable.SEPIA_BUILTIN_ID,        0xF5DEB3, 0x000000, 0x000000, false, 2);
    }

    public static void removeDuplicatedBuiltinThemes(SQLiteDatabase db) {
        try (Cursor cursor = db.rawQuery("SELECT _id, _sync_id FROM themes WHERE builtin=1 ORDER BY _id ASC", null)) {
            if (cursor.getCount() <= 3) return;

            boolean hasAmoled = false;
            boolean hasNight = false;
            boolean hasSepia = false;

            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                String internalId = cursor.getString(1);
                boolean isDuplicate = false;

                if (ThemesTable.NIGHT_AMOLED_BUILTIN_ID.equals(internalId)) {
                    if (hasAmoled) isDuplicate = true;
                    else hasAmoled = true;
                } else if (ThemesTable.NIGHT_BUILTIN_ID.equals(internalId)) {
                    if (hasNight) isDuplicate = true;
                    else hasNight = true;
                } else if (ThemesTable.SEPIA_BUILTIN_ID.equals(internalId)) {
                    if (hasSepia) isDuplicate = true;
                    else hasSepia = true;
                }

                if (isDuplicate) {
                    db.delete(ThemesTable.TABLE_NAME, "_id=?", new String[]{String.valueOf(id)});
                }
            }
        }
    }

    public static ThemeManager getInstance(Context context) {
        if (sInstance == null) {
            synchronized (sInstanceLock) {
                if (sInstance == null) {
                    sInstance = new ThemeManager(context, DatabaseProvider.getDatabase(context));
                }
            }
        }
        return sInstance;
    }

    public static ThemeManager createManagerForTest(Context context, SQLiteDatabase db) {
        return new ThemeManager(context, db);
    }
}
