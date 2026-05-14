package com.hieuld.helium.util;

import android.content.Context;
import android.content.res.Configuration;
import android.util.TypedValue;

import androidx.core.content.ContextCompat;

/**
 * Utility class for managing and detecting UI theme states.
 */
public class ThemeUtils {

    public static boolean isInDarkMode(Context context) {
        int currentNightMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    public static int resolveColor(Context context, int attr, int fallbackColorResId) {
        TypedValue typedValue = new TypedValue();
        if (context.getTheme().resolveAttribute(attr, typedValue, true)) {
            if (typedValue.resourceId != 0) {
                return ContextCompat.getColor(context, typedValue.resourceId);
            }
            return typedValue.data;
        }
        return ContextCompat.getColor(context, fallbackColorResId);
    }
}
