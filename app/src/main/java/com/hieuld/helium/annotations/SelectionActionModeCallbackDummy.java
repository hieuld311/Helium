package com.hieuld.helium.annotations;

import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;

/**
 * A no-op {@link ActionMode.Callback} used to intercept and suppress the system's
 * default text-selection action mode.
 *
 * When the app manages its own selection toolbar ({@link SelectionToolbarView}),
 * the system action mode bar must not appear. This callback is installed as the
 * action mode callback so that the system bar is never inflated (onCreateActionMode
 * always returns {@code false}).
 */
public class SelectionActionModeCallbackDummy implements ActionMode.Callback {

    private final ActionMode.Callback mOriginalCallback;

    public SelectionActionModeCallbackDummy(ActionMode.Callback originalCallback) {
        mOriginalCallback = originalCallback;
    }

    /**
     * Delegate to the original callback so it can perform any necessary setup,
     * but return {@code false} to prevent the system bar from being shown.
     */
    @Override
    public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
        mOriginalCallback.onCreateActionMode(actionMode, menu);
        return false;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
        return false;
    }

    @Override
    public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
        return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode actionMode) {
        // nothing to clean up
    }
}
