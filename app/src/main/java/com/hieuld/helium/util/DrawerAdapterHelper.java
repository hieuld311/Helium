package com.hieuld.helium.util;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.hieuld.helium.R;

/**
 * Helper class to manage the creation and data binding of Navigation Drawer items.
 */
public class DrawerAdapterHelper {

    /** Inflates the layout for a drawer item and attaches a ViewHolder to it. */
    public static View inflateView(Context context, ViewGroup parent) {
        View view = LayoutInflater.from(context).inflate(R.layout.drawer_item, parent, false);
        view.setTag(new ViewHolder(view));
        return view;
    }

    /**
     * Populates the view with specific data like icon, title, and activation state.
     *
     * @param context   The application context.
     * @param view      The view to bind data to.
     * @param iconResId Resource ID of the icon.
     * @param title     The text to display.
     * @param activated Whether the item should appear as selected/active.
     */
    public static void bindView(Context context, View view, int iconResId, String title, boolean activated) {
        ViewHolder holder = (ViewHolder) view.getTag();
        holder.iconView.setImageResource(iconResId);
        holder.titleView.setText(title);
        holder.itemView.setActivated(activated);
    }

    public static class ViewHolder {
        public final ImageView iconView;
        public final View itemView;
        public final TextView titleView;

        public ViewHolder(View view) {
            this.itemView = view;
            this.iconView = view.findViewById(R.id.icon);
            this.titleView = view.findViewById(R.id.title);
        }
    }
}
