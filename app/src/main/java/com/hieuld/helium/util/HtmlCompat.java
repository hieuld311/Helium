package com.hieuld.helium.util;

import android.text.Html;
import android.text.Spanned;

/**
 * Utility class for converting HTML strings into formatted Spanned text.
 */
public class HtmlCompat {

    /**
     * Converts an HTML string into a Spanned object using legacy formatting mode.
     *
     * @param source The HTML string to be converted.
     * @return Formatted text compatible with Android UI components.
     */
    public static Spanned fromHtml(String source) {
        return Html.fromHtml(source, Html.FROM_HTML_MODE_LEGACY);
    }
}
