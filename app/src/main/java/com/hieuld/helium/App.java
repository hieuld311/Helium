package com.hieuld.helium;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;

import androidx.preference.PreferenceManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.Registry;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory;
import com.bumptech.glide.module.GlideModule;
import com.bumptech.glide.request.RequestOptions;

import com.hieuld.helium.db.BooksTable;
import com.hieuld.helium.db.DatabaseProvider;
import com.hieuld.helium.themes.AppThemeManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class App extends Application {
    private static final int APP_SHORTCUTS_BOOK_COUNT = 4;
    public static final String FILE_PROVIDER = BuildConfig.APPLICATION_ID + ".fileprovider";
    private static final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    @Override
    public void onCreate() {
        super.onCreate();
        upgradePrefsIfNecessary();
        AppThemeManager.getInstance(this).apply();
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
    }

    private void upgradePrefsIfNecessary() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        try {
            prefs.getString("volume_pagination", null);
        } catch (ClassCastException ignored) {
            prefs.edit()
                    .putString("volume_pagination",
                            prefs.getBoolean("volume_pagination", false) ? "enabled" : "disabled")
                    .apply();
        }
    }

    public static void refreshAppShortcuts(Context context) {
        Context appContext = context.getApplicationContext();

        mExecutor.execute(() -> {
            try (Cursor cursor = DatabaseProvider.getDatabase(appContext).query(
                    BooksTable.TABLE_NAME,
                    new String[]{"_id", BooksTable.COLUMN_TITLE, BooksTable.COLUMN_COVER},
                    DatabaseUtils.concatenateWhere("hidden = 0", "last_open_date != 0"),
                    null, null, null,
                    "last_open_date DESC LIMIT " + APP_SHORTCUTS_BOOK_COUNT)) {

                List<ShortcutInfo> shortcuts = new ArrayList<>();
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(0);
                    String shortcutId = "book_" + id;
                    String title = cursor.getString(1);
                    String coverPath = cursor.getString(2);

                    if (title != null && !title.isEmpty()) {
                        Intent intent = new Intent(appContext, ReaderActivity.class)
                                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                .setAction(Intent.ACTION_VIEW)
                                .putExtra("book_id", id)
                                .putExtra(ReaderActivity.EXTRA_LAUNCH_SOURCE,
                                        ReaderActivity.LAUNCH_SOURCE_APP_SHORTCUT);

                        ShortcutInfo.Builder builder = new ShortcutInfo.Builder(appContext, shortcutId)
                                .setShortLabel(title)
                                .setLongLabel(title)
                                .setIntent(intent);

                        Bitmap bitmap = coverPath != null ? BitmapFactory.decodeFile(coverPath) : null;
                        if (bitmap != null) {
                            builder.setIcon(Icon.createWithBitmap(bitmap));
                        } else {
                            builder.setIcon(Icon.createWithResource(appContext, R.drawable.ic_app_shortcut_no_cover));
                        }
                        shortcuts.add(builder.build());
                    }
                }
                ShortcutManager sm = appContext.getSystemService(ShortcutManager.class);
                if (sm != null) {
                    sm.setDynamicShortcuts(shortcuts);
                }
            }
        });
    }

    public static void reportAppShortcutUsed(Context context, String shortcutId) {
        Context appContext = context.getApplicationContext();
        mExecutor.execute(() -> {
            ShortcutManager sm = appContext.getSystemService(ShortcutManager.class);
            if (sm != null) {
                sm.reportShortcutUsed(shortcutId);
            }
        });
    }

    public static void disableShortcutsForBooks(Context context, List<Long> ids) {
        ShortcutManager sm = context.getSystemService(ShortcutManager.class);
        if (sm == null) return;

        List<String> shortcutIds = new ArrayList<>();
        for (Long id : ids) {
            shortcutIds.add("book_" + id);
        }
        sm.disableShortcuts(shortcutIds, context.getString(R.string.app_shortcut_disabled));
    }

    public static class GlideConfig implements GlideModule {
        private static final int MAX_CACHE_SIZE = 20 * 1024 * 1024; // 20 MB

        @Override
        public void registerComponents(Context context, Glide glide, Registry registry) {
        }

        @Override
        public void applyOptions(Context context, GlideBuilder builder) {
            builder.setDiskCache(new InternalCacheDiskCacheFactory(context, MAX_CACHE_SIZE));
            builder.setDefaultRequestOptions(
                    new RequestOptions().format(DecodeFormat.PREFER_ARGB_8888).disallowHardwareConfig());
        }
    }
}