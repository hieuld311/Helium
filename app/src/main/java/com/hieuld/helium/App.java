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

//import com.hieuld.helium.db.BooksTable;
//import com.hieuld.helium.db.DatabaseProvider;
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

    private void upgradePrefsIfNecessary() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        try {
            prefs.getString("volume_pagination", null);
        } catch (ClassCastException ignored) {
            prefs.edit().putString("volume_pagination", prefs.getBoolean("volume_pagination", false) ? "enabled" : "disabled").apply();
        }
    }

    public static void reportAppShortcutUsed(Context context, String shortcutId) {
        Context appContext = context.getApplicationContext();

        mExecutor.execute(() -> {
            ShortcutManager shortcutManager = appContext.getSystemService(ShortcutManager.class);
            if (shortcutManager != null) {
                shortcutManager.reportShortcutUsed(shortcutId);
            }
        });
    }

    public static void disableShortcutsForBooks(Context context, List<Long> ids) {
        ShortcutManager shortcutManager = context.getSystemService(ShortcutManager.class);
        if (shortcutManager == null) return;

        List<String> shortcutIds = new ArrayList<>();
        for (Long id : ids) {
            shortcutIds.add("book_" + id);
        }
        shortcutManager.disableShortcuts(shortcutIds, context.getString(R.string.app_shortcut_disabled));
    }

    public static class GlideConfig implements GlideModule {
        private static final int MAX_CACHE_SIZE = 20971520;

        @Override
        public void registerComponents(Context context, Glide glide, Registry registry) {
        }

        @Override
        public void applyOptions(Context context, GlideBuilder builder) {
            builder.setDiskCache(new InternalCacheDiskCacheFactory(context, MAX_CACHE_SIZE));
            builder.setDefaultRequestOptions(new RequestOptions().format(DecodeFormat.PREFER_ARGB_8888).disallowHardwareConfig());
        }
    }
}
