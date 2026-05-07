package com.hieuld.helium.util;

import android.content.Context;
import android.content.res.Configuration;

public class ThemeUtils {
    public static boolean isInDarkMode(Context context) {
        // Extract the night mode bits from the current UI configuration
        int currentNightMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;

        // Check if the extracted mode matches the "Night Yes" constant
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES;
    }
}
