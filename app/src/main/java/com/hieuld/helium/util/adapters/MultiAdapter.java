package com.hieuld.helium.util.adapters;

import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * An Adapter that combines multiple sub-adapters into a single linear list.
 */
public class MultiAdapter extends BaseAdapter {
    private int mCurrentSize;
    private final List<SourceInfo> mSources = new ArrayList<>();

    /**
     * Represents the mapping of a global position to a specific sub-adapter.
     */
    public static class Projection {
        public BaseAdapter adapter;
        public int index;
        protected SourceInfo source;
    }

    /**
     * Holds metadata for each sub-adapter added to the MultiAdapter.
     */
    public static class SourceInfo {
        public BaseAdapter adapter;
        public int curCount;
        public int curStartIndex;
        public boolean enabled = true;

        public SourceInfo(BaseAdapter adapter) {
            this.adapter = adapter;
        }
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    /** Adds a new sub-adapter to the end of the list. */
    public void addAdapter(BaseAdapter adapter) {
        this.mSources.add(new SourceInfo(adapter));
    }

    @Override
    public int getCount() {
        return this.mCurrentSize;
    }

    @Override
    public Object getItem(int position) {
        Projection projection = getProjection(position);
        return projection.adapter.getItem(projection.index);
    }

    @Override
    public long getItemId(int position) {
        Projection projection = getProjection(position);
        return projection.adapter.getItemId(projection.index);
    }

    @Override
    public boolean isEnabled(int position) {
        Projection projection = getProjection(position);
        return projection.adapter.isEnabled(projection.index);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Projection projection = getProjection(position);
        return projection.adapter.getView(projection.index, convertView, parent);
    }

    @Override
    public int getViewTypeCount() {
        int viewTypeCount = 0;
        for (SourceInfo source : this.mSources) {
            viewTypeCount += source.adapter.getViewTypeCount();
        }
        return Math.max(1, viewTypeCount);
    }

    @Override
    public int getItemViewType(int position) {
        Projection projection = getProjection(position);
        int viewTypeOffset = 0;

        for (SourceInfo source : this.mSources) {
            if (source == projection.source) {
                break;
            }
            viewTypeOffset += source.adapter.getViewTypeCount();
        }

        return viewTypeOffset + projection.source.adapter.getItemViewType(projection.index);
    }

    @Override
    public void notifyDataSetChanged() {
        int totalSize = 0;
        for (SourceInfo sourceInfo : this.mSources) {
            if (sourceInfo.enabled) {
                sourceInfo.adapter.notifyDataSetChanged();
                sourceInfo.curStartIndex = totalSize;
                sourceInfo.curCount = sourceInfo.adapter.getCount();
                totalSize += sourceInfo.curCount;
            }
        }
        this.mCurrentSize = totalSize;
        super.notifyDataSetChanged();
    }

    /** Finds which sub-adapter owns the given global position. */
    public Projection getProjection(int position) {
        for (SourceInfo source : this.mSources) {
            if (source.enabled && position >= source.curStartIndex && position < source.curStartIndex + source.curCount) {
                Projection projection = new Projection();
                projection.source = source;
                projection.adapter = source.adapter;
                projection.index = position - source.curStartIndex;
                return projection;
            }
        }

        throw new IndexOutOfBoundsException("getProjection() called with out of bounds index=" + position + ", size=" + mCurrentSize);
    }

    /** Dynamically show or hide a specific sub-adapter. */
    public void setEnabled(BaseAdapter adapter, boolean enabled) {
        for (SourceInfo sourceInfo : this.mSources) {
            if (sourceInfo.adapter == adapter) {
                sourceInfo.enabled = enabled;
                return;
            }
        }
    }
}
