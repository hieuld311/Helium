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
    private final int mSdkVersion;

    private AppThemeManager(Context context) {
        Context applicationContext = context.getApplicationContext();
        this.mContext = applicationContext;
        this.mPrefs = PreferenceManager.getDefaultSharedPreferences(applicationContext);
        this.mSdkVersion = Build.VERSION.SDK_INT;
    }

    private AppThemeManager(Context context, int sdkVersion) {
        this.mContext = context;
        this.mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        this.mSdkVersion = sdkVersion;
    }

    private int getNightModeForPref(String pref) {
        if (VALUE_DARK.equals(pref)) {
            return AppCompatDelegate.MODE_NIGHT_YES;
        }
        if (VALUE_LIGHT.equals(pref)) {
            return AppCompatDelegate.MODE_NIGHT_NO;
        }
        return this.mSdkVersion >= 29
                ? AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                : AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY;
    }

    private String getDefaultPrefValue() {
        return this.mSdkVersion >= 21 ? VALUE_AUTO : VALUE_LIGHT;
    }

    public String getCurrentPrefValue() {
        String val = this.mPrefs.getString(APP_THEME, getDefaultPrefValue());
        return (this.mSdkVersion >= 21 || !VALUE_AUTO.equals(val)) ? val : VALUE_LIGHT;
    }

    public void setTheme(String pref) {
        this.mPrefs.edit().putString(APP_THEME, pref).apply();
        apply(pref);
    }

    private void apply(String pref) {
        AppCompatDelegate.setDefaultNightMode(getNightModeForPref(pref));
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

    public static AppThemeManager createForTest(Context context, int sdkVersion) {
        return new AppThemeManager(context, sdkVersion);
    }
}
