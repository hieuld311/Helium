package com.hieuld.helium.util.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

/**
 * A generic adapter used to display a single custom layout as an item in a list.
 */
public class SingleViewAdapter extends BaseAdapter {
    private final Context mContext;
    private final int mResId;

    public SingleViewAdapter(Context context, int layoutResId) {
        this.mContext = context;
        this.mResId = layoutResId;
    }

    /** Override in subclasses to bind data to the inflated view. */
    public void bindView(View view) {
        // Default no-op
    }

    @Override
    public int getCount() {
        return 1;
    }

    @Override
    public Object getItem(int position) {
        return null;
    }

    @Override
    public long getItemId(int position) {
        return 0L;
    }

    @Override
    public boolean isEnabled(int position) {
        return false;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(mContext).inflate(mResId, parent, false);
        }
        bindView(convertView);
        return convertView;
    }
}
