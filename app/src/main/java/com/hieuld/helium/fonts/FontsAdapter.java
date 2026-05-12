package com.hieuld.helium.fonts;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import com.hieuld.helium.R;
import java.util.ArrayList;
import java.util.List;

public class FontsAdapter extends BaseAdapter {
    private final Context mContext;
    private List<Font> mFonts = new ArrayList<>();

    public FontsAdapter(Context context) {
        this.mContext = context;
    }

    public void setFonts(List<Font> fonts) {
        this.mFonts = fonts;
        notifyDataSetChanged(); // Nên gọi notifyDataSetChanged để update UI khi list thay đổi
    }

    @Override
    public int getCount() {
        // Cộng 1 vì vị trí số 0 luôn dành cho mục "Font gốc" (Original Font)
        return this.mFonts.size() + 1;
    }

    @Override
    public Object getItem(int position) {
        if (position > 0) {
            return this.mFonts.get(position - 1);
        }
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
            convertView = LayoutInflater.from(this.mContext).inflate(R.layout.display_font_list_item, parent, false);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        Font font = (Font) getItem(position);

        if (font != null) {
            holder.nameView.setTypeface(Fonts.getTypeface(this.mContext, font.regular));
            holder.nameView.setText(font.name);
        } else {
            // Vị trí số 0: Font gốc của sách
            holder.nameView.setTypeface(null);
            holder.nameView.setText(R.string.display_settings_font_original);
        }

        return convertView;
    }

    public int indexOf(Font font) {
        return this.mFonts.indexOf(font);
    }

    // Áp dụng ViewHolder pattern để tối ưu hiệu năng cuộn (scroll)
    private static class ViewHolder {
        final TextView nameView;

        ViewHolder(View view) {
            this.nameView = view.findViewById(R.id.name);
        }
    }
}