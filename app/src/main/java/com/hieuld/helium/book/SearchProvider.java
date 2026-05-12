package com.hieuld.helium.book;

import java.util.List;

/**
 * Contract for book full-text search backends.
 */
public interface SearchProvider {

    /** Maximum number of results ever returned by a single search. */
    int MAXIMUM_RESULTS = 5000;

    /** Receives the finished result set on the main thread. */
    interface Callbacks {
        void onSearchFinished(List<SearchResult> results);
    }

    /**
     * Start an asynchronous search for {@code query} across the entire book.
     * Results are delivered via {@link Callbacks#onSearchFinished} on the main thread.
     * Any previously running search is cancelled first.
     */
    void startSearch(String query, Callbacks callbacks);

    /** Cancel any in-progress search. Safe to call when no search is running. */
    void cancelSearch();
}
