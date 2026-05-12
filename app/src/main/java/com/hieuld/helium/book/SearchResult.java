package com.hieuld.helium.book;

/**
 * A single search hit returned by {@link SearchProvider}.
 */
public class SearchResult {

    /** A result that matches text inside the book body. */
    public static final int TYPE_NORMAL    = 0;
    /** A result that matches a Table-of-Contents entry title. */
    public static final int TYPE_TOC_ENTRY = 1;

    /** {@link #TYPE_NORMAL} or {@link #TYPE_TOC_ENTRY}. */
    public int type;

    /**
     * For {@link #TYPE_NORMAL}: JSON object with keys {@code filename}, {@code start}, {@code end}.
     * For {@link #TYPE_TOC_ENTRY}: the TOC entry URL.
     */
    public String position;

    /** Surrounding context text shown in the search results list. */
    public String snippet;

    /** Inclusive start index of the match within {@link #snippet}. */
    public int snippetIndexStart;

    /** Exclusive end index of the match within {@link #snippet}. */
    public int snippetIndexEnd;
}
