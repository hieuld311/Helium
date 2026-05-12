package com.hieuld.helium.fonts;

import android.content.Context;
import android.graphics.Typeface;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Fonts {
    private static final String ASSET_FONT_PATH = "fonts";
    public static final int SCRIPT_CYRILLIC = 2;
    public static final int SCRIPT_GREEK = 4;
    public static final int SCRIPT_LATIN = 1;
    private static final List<Font> FONTS = Arrays.asList(fontWithWeights("Open Sans", "OpenSans", 7), fontWithWeights("PT Serif", "PT_Serif", 3), new Font("Domine", "Domine-Regular.ttf", "Domine-Bold.ttf", null, null, 1), fontWithWeights("Lato", "Lato", 1), new Font("Arbutus Slab", "ArbutusSlab-Regular.ttf", null, null, null, 1));
    private static Map<String, Typeface> typefaces = new HashMap();

    public static List<Font> getFonts() {
        return FONTS;
    }

    public static Typeface getTypeface(Context context, String str) {
        Typeface typeface = typefaces.get(str);
        if (typeface != null) {
            return typeface;
        }
        Typeface typefaceCreateFromAsset = Typeface.createFromAsset(context.getAssets(), "fonts/" + str);
        typefaces.put(str, typefaceCreateFromAsset);
        return typefaceCreateFromAsset;
    }

    public static List<Font> getCompatibleFonts(String str) {
        int i = SCRIPT_LATIN;
        if (isCyrillicLanguage(str)) {
            i = SCRIPT_CYRILLIC;
        } else if (isGreekLanguage(str)) {
            i = SCRIPT_GREEK;
        }
        ArrayList arrayList = new ArrayList();
        for (Font font : FONTS) {
            if ((font.scripts & i) != 0) {
                arrayList.add(font);
            }
        }
        return arrayList.isEmpty() ? FONTS : arrayList;
    }

    public static Font getFontByName(String str) {
        if (str == null) {
            return null;
        }
        for (Font font : getFonts()) {
            if (font.name.equals(str)) {
                return font;
            }
        }
        return null;
    }

    private static Font fontWithWeights(String str, String str2, int i) {
        return new Font(str, str2 + "-Regular.ttf", str2 + "-Bold.ttf", str2 + "-Italic.ttf", str2 + "-BoldItalic.ttf", i);
    }

    private static boolean isCyrillicLanguage(String str) {
        if (str == null) {
            return false;
        }
        String lowerCase = str.toLowerCase(Locale.US);
        return lowerCase.startsWith("ru") || lowerCase.startsWith("uk") || lowerCase.startsWith("bg") || lowerCase.startsWith("be") || lowerCase.startsWith("sr") || lowerCase.startsWith("mk") || lowerCase.startsWith("kk") || lowerCase.startsWith("ky");
    }

    private static boolean isGreekLanguage(String str) {
        return str != null && str.toLowerCase(Locale.US).startsWith("el");
    }
}
