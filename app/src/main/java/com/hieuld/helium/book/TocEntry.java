package com.hieuld.helium.book;

import java.util.List;

/**
 * One node in the book's Table of Contents tree.
 */
public class TocEntry {

    /** Display title shown in the TOC list. */
    public String title;

    /** Resolved URL (spine-relative path + optional fragment) this entry points to. */
    public String url;

    /** The OPF content document path used to build {@link #url} (may be {@code null}). */
    public String path;

    /** Nesting depth: 0 = top-level chapter, 1 = section, etc. */
    public int depth;

    /** Parent entry, or {@code null} if this is a top-level entry. */
    public TocEntry parent;

    /** Direct child entries (sub-sections). Never {@code null} after parsing. */
    public List<TocEntry> children;

    @Override
    public String toString() {
        return title;
    }
}
