package com.hieuld.helium.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.hieuld.helium.R;

import java.util.ArrayList;
import java.util.List;

public class RecentSearchesAdapter extends BaseAdapter {
    private final Context mContext;
    private List<String> mQueries = new ArrayList<>();

    public RecentSearchesAdapter(Context context) {
        this.mContext = context;
    }

    public void setQueries(List<String> queries) {
        this.mQueries = queries;
        notifyDataSetChanged();
    }

    public String getQuery(int position) {
        return this.mQueries.get(position);
    }

    @Override
    public int getCount() {
        return this.mQueries.size();
    }

    @Override
    public Object getItem(int position) {
        return this.mQueries.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(this.mContext).inflate(R.layout.recent_search_item, parent, false);
        }
        ((TextView) convertView).setText(this.mQueries.get(position));
        return convertView;
    }
}
