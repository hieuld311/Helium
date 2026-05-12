package com.hieuld.helium.book;
import android.content.Context;
import android.graphics.Bitmap;

import com.hieuld.helium.exceptions.BookLoadException;
import com.hieuld.helium.
exceptions.UnsupportedBookException;
import com.hieuld.helium.
util.Utils;

import java.util.List;
import java.util.Set;

/**
 * Abstract base class for all book formats supported by the reader.
 *
 * <p>Use {@link #create(String)} to obtain a concrete instance for a given file path.
 * The returned book is already loaded and ready for use.</p>
 */
public abstract class Book {

    public static final int DIRECTION_LTR  = 0;
    public static final int DIRECTION_RTL  = 1;
    public static final int THUMBNAIL_SIZE = 320; // dp

    // ── Abstract interface ────────────────────────────────────────────────────

    /** Release all resources held by this book (zip file handles, etc.). */
    public abstract void close();

    /**
     * Find the TOC entry whose URL equals {@code url} (case-insensitive).
     *
     * @return the matching entry, or {@code null} if not found.
     */
    public abstract TocEntry findTocEntry(String url);

    /** Render and return the cover image scaled to {@link #THUMBNAIL_SIZE} dp, or {@code null}. */
    public abstract Bitmap getCoverBitmap(Context context);

    /** Book creator/author string from metadata, or {@code null}. */
    public abstract String getCreator();

    /** BCP-47 language tag from metadata, or {@code null}. */
    public abstract String getLanguage();

    /** {@link #DIRECTION_LTR} or {@link #DIRECTION_RTL}. */
    public abstract int getPageDirection();

    /**
     * Set of paper page numbers declared in the navigation document.
     * Returns an empty set if no page-list is present.
     */
    public abstract Set<Integer> getPaperPages();

    /** Returns a {@link SearchProvider} for searching this book's content. */
    public abstract SearchProvider getSearchProvider();

    /** Book title from metadata, or {@code null}. */
    public abstract String getTitle();

    /** Flat depth-first ordered list of Table of Contents entries. */
    public abstract List<TocEntry> getTocEntries();

    /**
     * Load the book from the file at {@code filePath}.
     * Called once by {@link #create} immediately after construction.
     *
     * @throws BookLoadException if the file cannot be read or is malformed.
     */
    protected abstract void load(String filePath) throws BookLoadException;

    // ── Factory ───────────────────────────────────────────────────────────────

    /**
     * Create and load a book from the given file path.
     *
     * @param filePath absolute path to the book file.
     * @return a fully loaded {@link Book} instance.
     * @throws BookLoadException        if loading fails for any reason.
     * @throws UnsupportedBookException if the file extension is not recognised.
     */
    public static Book create(String filePath) throws BookLoadException {
        String extension = filePath.substring(filePath.lastIndexOf('.') + 1).toLowerCase();
        if ("epub".equals(extension)) {
            EPubBook book = new EPubBook();
            book.load(filePath);
            return book;
        }
        throw new UnsupportedBookException(extension + " is unsupported.");
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    /**
     * Scale {@code bitmap} to fit within a {@link #THUMBNAIL_SIZE} dp square,
     * preserving aspect ratio. Recycles the original bitmap if a new one is created.
     */
    protected Bitmap scaleToThumbnail(Context context, Bitmap bitmap) {
        int srcWidth  = bitmap.getWidth();
        int srcHeight = bitmap.getHeight();
        int maxPx     = Utils.dpToPx(context, THUMBNAIL_SIZE);

        int dstWidth, dstHeight;
        if (srcWidth > srcHeight) {
            dstWidth  = maxPx;
            dstHeight = (int) ((float) srcHeight / srcWidth * maxPx);
        } else {
            dstHeight = maxPx;
            dstWidth  = (int) ((float) srcWidth / srcHeight * maxPx);
        }

        if (dstWidth == 0 || dstHeight == 0) return bitmap;

        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, dstWidth, dstHeight, true);
        if (scaled != bitmap) bitmap.recycle();
        return scaled;
    }

    /**
     * Returns the maximum paper page number declared in the book's page-list,
     * or {@code 0} if no pages are declared.
     */
    public int getMaxPaperPage() {
        int max = 0;
        for (int page : getPaperPages()) {
            if (page > max) max = page;
        }
        return max;
    }
}
