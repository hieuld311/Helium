package com.hieuld.helium.model;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

/**
 * ProManager stub - all features are unlocked in this build.
 * The Pro/Sync companion app functionality has been removed.
 */
public class ProManager {

    /**
     * Always returns true since all features are unlocked in this build.
     */
    public static boolean isUnlocked(Context context) {
        return true;
    }

    /**
     * Returns null since there is no upgrade path in this build.
     */
    public static Intent getUpgradeIntent(String source) {
        // No upgrade flow needed; all features are free.
        return new Intent(Intent.ACTION_VIEW)
                .setData(Uri.parse("https://github.com/faultexception/reader"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    /**
     * No-op: pro check not required.
     */
    public static void checkIfNecessary(android.app.Activity activity) {
        // All features are available without a Pro companion app.
    }
}
