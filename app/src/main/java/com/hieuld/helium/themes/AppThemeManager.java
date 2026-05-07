package com.hieuld.helium.themes;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

public class AppThemeManager {
    public static final String VALUE_AUTO = "auto";
    public static final String VALUE_DARK = "dark";
    public static final String VALUE_LIGHT = "light";
    public static final String APP_THEME = "app_theme";

    private static AppThemeManager sInstance;
    private static final Object sInstanceLock = new Object();

    private final Context mContext;
    private final SharedPreferences mPrefs;

    private AppThemeManager(Context context) {
        this.mContext = context.getApplicationContext();
        this.mPrefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
    }

    private int getNightModeForPref(String mode) {
        switch (mode) {
            case VALUE_DARK:
                return AppCompatDelegate.MODE_NIGHT_YES;
            case VALUE_LIGHT:
                return AppCompatDelegate.MODE_NIGHT_NO;
            case VALUE_AUTO:
            default:
                // Android 10 (API 29) trở lên dùng FOLLOW_SYSTEM, các bản thấp hơn (API 26, 27, 28) dùng AUTO_BATTERY
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                } else {
                    return AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY;
                }
        }
    }

    public String getCurrentPrefValue() {
        return this.mPrefs.getString(APP_THEME, VALUE_AUTO);
    }

    public void setTheme(String themeValue) {
        this.mPrefs.edit().putString(APP_THEME, themeValue).apply();
        apply(themeValue);
    }

    private void apply(String themeValue) {
        AppCompatDelegate.setDefaultNightMode(getNightModeForPref(themeValue));
    }

    public void apply() {
        apply(getCurrentPrefValue());
    }

    public static AppThemeManager getInstance(Context context) {
        if (sInstance == null) {
            synchronized (sInstanceLock) {
                if (sInstance == null) {
                    sInstance = new AppThemeManager(context);
                }
            }
        }
        return sInstance;
    }
}