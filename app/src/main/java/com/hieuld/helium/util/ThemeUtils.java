package com.hieuld.helium.util;

import android.content.Context;
import android.content.res.Configuration;

/**
 * Utility class for managing and detecting UI theme states.
 */
public class ThemeUtils {

    public static boolean isInDarkMode(Context context) {
        int currentNightMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES;
    }
}
