package com.hieuld.helium.util;

import androidx.appcompat.view.ActionMode;

public interface ActionModeMultiCallback extends ActionMode.Callback {
    void onCheckedItemsChanged(ActionMode actionMode);
}
