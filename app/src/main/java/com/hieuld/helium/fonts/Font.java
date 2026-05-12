package com.hieuld.helium.fonts;

/**
 * Represents a font family with its various weight/style variants.
 */
public class Font {
    public String bold;
    public String boldItalic;
    public String italic;
    public String name;
    public String regular;
    public int scripts;

    public Font(String name, String regular, String bold, String italic, String boldItalic, int scripts) {
        this.name = name;
        this.regular = regular;
        this.bold = bold;
        this.italic = italic;
        this.boldItalic = boldItalic;
        this.scripts = scripts;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Font) {
            return ((Font) obj).name.equals(this.name);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }
}