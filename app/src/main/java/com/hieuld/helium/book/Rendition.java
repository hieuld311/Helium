package com.hieuld.helium.book;

/**
 * EPUB rendition properties derived from the OPF {@code <meta>} tags and spine attributes.
 *
 * <p>These map directly to the EPUB 3 Rendition vocabulary:</p>
 * <ul>
 *   <li><b>layout</b>: reflowable (default) vs. pre-paginated (fixed layout)</li>
 *   <li><b>flow</b>: paginated (default) vs. scrolled</li>
 * </ul>
 *
 * A value of {@link #UNSPECIFIED} ({@code 0}) means the property was not declared in the OPF.
 */
public class Rendition {

    public static final int UNSPECIFIED   = 0;

    // ── Layout ─────────────────────────────────────────────────────────────────
    public static final int LAYOUT_REFLOW = 1; // epub:layout = reflowable
    public static final int LAYOUT_FIXED  = 2; // epub:layout = pre-paginated

    // ── Flow ───────────────────────────────────────────────────────────────────
    public static final int FLOW_PAGED    = 1; // epub:flow = paginated
    public static final int FLOW_SCROLLED = 2; // epub:flow = scrolled-*

    public int layoutStyle;
    public int flowStyle;

    /** Creates a Rendition with both properties {@link #UNSPECIFIED}. */
    public Rendition() {
        layoutStyle = UNSPECIFIED;
        flowStyle   = UNSPECIFIED;
    }

    /** Copy constructor. */
    public Rendition(Rendition source) {
        layoutStyle = source.layoutStyle;
        flowStyle   = source.flowStyle;
    }

    /** Creates a Rendition with explicit values for both properties. */
    public Rendition(int layoutStyle, int flowStyle) {
        this.layoutStyle = layoutStyle;
        this.flowStyle   = flowStyle;
    }

    /**
     * Copies non-{@link #UNSPECIFIED} values from {@code override} into this rendition.
     * Used to layer spine-item-level overrides on top of the book-level defaults.
     */
    public void extend(Rendition override) {
        if (override == null) return;
        if (override.layoutStyle != UNSPECIFIED) layoutStyle = override.layoutStyle;
        if (override.flowStyle   != UNSPECIFIED) flowStyle   = override.flowStyle;
    }

    /** @return {@code true} if this rendition uses a fixed/pre-paginated layout. */
    public boolean isFixedLayout() {
        return layoutStyle == LAYOUT_FIXED;
    }

    /** Factory: creates a Rendition with only {@code layoutStyle} set. */
    public static Rendition withLayoutStyle(int layoutStyle) {
        Rendition r = new Rendition();
        r.layoutStyle = layoutStyle;
        return r;
    }

    /** Factory: creates a Rendition with only {@code flowStyle} set. */
    public static Rendition withFlowStyle(int flowStyle) {
        Rendition r = new Rendition();
        r.flowStyle = flowStyle;
        return r;
    }
}
