package com.hieuld.helium.annotations;

/**
 * Abstract base for annotation rendering backends.
 *
 * Each concrete implementation bridges between the {@link AnnotationController} and the
 * underlying content view (e.g. {@link HtmlAnnotationRenderer} for WebView-based EPUB content).
 */
public abstract class AnnotationRenderer {

    /** Remove any active text-selection highlight. */
    public abstract void clearSelection();

    /**
     * Ask the renderer to remove the annotation with the given database id from the view.
     *
     * @param annotationId the {@code _id} of the annotation row to remove
     */
    public abstract void deleteAnnotation(long annotationId);

    /** Temporarily hide note-marker icons (e.g. while a selection is active). */
    public abstract void hideNoteMarkers();

    /**
     * Request fresh bounding-rect data for the current selection or annotation.
     *
     * @param requestId     monotonically-increasing token; stale callbacks are ignored
     * @param annotationId  the annotation whose bounds are needed, or {@code -1} for a plain selection
     */
    public abstract void requestUpdatedBounds(int requestId, long annotationId);

    /**
     * Re-draw a previously saved annotation after the page has been (re-)loaded.
     *
     * @param annotationId  database id
     * @param type          {@link AnnotationController#TYPE_HIGHLIGHT} or {@link AnnotationController#TYPE_UNDERLINE}
     * @param range         serialised CFI/range string
     * @param color         ARGB color int
     * @param hasNote       {@code true} if a note is attached
     * @param restoreFlags  bitmask of {@code RESTORE_FLAG_*} constants
     */
    public abstract void restoreAnnotation(long annotationId, int type, String range,
                                           int color, boolean hasNote, int restoreFlags);

    /**
     * Change the highlight color for the current text selection (before it is committed).
     *
     * @param color ARGB color int
     */
    public abstract void setSelectionColor(int color);

    /** Re-show note-marker icons after they were hidden. */
    public abstract void showNoteMarkers();

    /**
     * Update the visual appearance of an existing annotation.
     *
     * @param annotationId  database id
     * @param type          highlight or underline
     * @param color         ARGB color int
     * @param hasNote       whether a note is now attached
     */
    public abstract void updateAnnotation(long annotationId, int type, int color, boolean hasNote);
}
