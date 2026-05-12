package com.hieuld.helium.util.adapters;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.hieuld.helium.util.DrawerAdapterHelper;

/**
 * A simple adapter used to display a single, specific action item in a list.
 */
public class SingleActionAdapter extends BaseAdapter {
    private final Context mContext;
    private final int mIconId;
    private final int mTitleId;

    public SingleActionAdapter(Context context, int iconId, int titleId) {
        this.mContext = context;
        this.mIconId = iconId;
        this.mTitleId = titleId;
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
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = DrawerAdapterHelper.inflateView(mContext, parent);
        }

        DrawerAdapterHelper.bindView(
                mContext,
                convertView,
                mIconId,
                mContext.getString(mTitleId),
                false
        );

        return convertView;
    }
}
