package com.hieuld.helium.util.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.hieuld.helium.R;

public class DrawerFooterAdapter extends BaseAdapter {
    private final int[] ICON_RES_IDS = {R.drawable.ic_drawer_settings, R.drawable.ic_drawer_feedback};
    private final int[] TITLE_RES_IDS = {R.string.action_settings, R.string.action_feedback};
    private final Context mContext;

    public DrawerFooterAdapter(Context context) {
        this.mContext = context;
    }

    @Override
    public int getCount() {
        return TITLE_RES_IDS.length;
    }

    @Override
    public Object getItem(int position) {
        return null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(mContext).inflate(R.layout.drawer_item, parent, false);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        holder.iconView.setImageResource(ICON_RES_IDS[position]);
        holder.titleView.setText(TITLE_RES_IDS[position]);
        return convertView;
    }

    private static class ViewHolder {
        public final ImageView iconView;
        public final TextView titleView;

        public ViewHolder(View view) {
            this.iconView = view.findViewById(R.id.icon);
            this.titleView = view.findViewById(R.id.title);
        }
    }
}
