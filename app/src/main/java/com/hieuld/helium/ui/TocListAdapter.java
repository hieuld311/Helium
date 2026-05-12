package com.hieuld.helium.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hieuld.helium.R;
import com.hieuld.helium.book.TocEntry;
import com.hieuld.helium.util.Utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TocListAdapter extends RecyclerView.Adapter<TocListAdapter.ViewHolder> {
    private List<TocEntry> mAllEntries;
    private final Context mContext;
    private TocEntry mCurrentEntry;
    private List<TocEntry> mEntries = new ArrayList<>();
    private final Set<TocEntry> mExpandedRoots = new HashSet<>();
    private final OnClickListener mOnClickListener;

    public interface OnClickListener {
        void onTocEntryClick(TocEntry entry);
    }

    public TocListAdapter(Context context, OnClickListener onClickListener) {
        this.mContext = context;
        this.mOnClickListener = onClickListener;
    }

    @Override
    public long getItemId(int position) {
        return 0L;
    }

    public void setEntries(List<TocEntry> entries) {
        this.mAllEntries = entries;
        this.mEntries.clear();
        for (TocEntry entry : entries) {
            if (entry.parent == null) {
                this.mEntries.add(entry);
            }
        }
        notifyDataSetChanged();
    }

    private void updateEntries() {
        List<Integer> insertions = new ArrayList<>();
        List<Integer> removals = new ArrayList<>();
        List<TocEntry> newVisibleEntries = new ArrayList<>();

        int currentIndex = 0;
        for (TocEntry entry : this.mAllEntries) {
            if (entry.parent == null || this.mExpandedRoots.contains(entry.parent)) {
                newVisibleEntries.add(entry);
                if (!this.mEntries.contains(entry)) {
                    insertions.add(currentIndex);
                }
                currentIndex++;
            } else {
                int oldIndex = this.mEntries.indexOf(entry);
                if (oldIndex != -1) {
                    removals.add(0, oldIndex);
                }
            }
        }

        this.mEntries = newVisibleEntries;

        for (Integer removeIndex : removals) {
            notifyItemRemoved(removeIndex);
        }
        for (Integer insertIndex : insertions) {
            notifyItemInserted(insertIndex);
        }
    }

    public void setCurrentEntry(TocEntry entry) {
        Set<TocEntry> newlyExpanded = new HashSet<>();
        if (entry != null) {
            for (TocEntry parent = entry.parent; parent != null; parent = parent.parent) {
                if (this.mExpandedRoots.add(parent)) {
                    newlyExpanded.add(parent);
                }
            }
        }

        if (!newlyExpanded.isEmpty()) {
            updateEntries();
        }

        for (int i = 0; i < this.mEntries.size(); i++) {
            TocEntry item = this.mEntries.get(i);
            if (item == this.mCurrentEntry || item == entry || newlyExpanded.contains(item)) {
                notifyItemChanged(i);
            }
        }
        this.mCurrentEntry = entry;
    }

    public boolean toggleRoot(TocEntry entry) {
        boolean isExpanded;
        if (this.mExpandedRoots.contains(entry)) {
            collapseRoot(entry);
            isExpanded = false;
        } else {
            this.mExpandedRoots.add(entry);
            isExpanded = true;
        }
        updateEntries();
        return isExpanded;
    }

    private void collapseRoot(TocEntry entry) {
        this.mExpandedRoots.remove(entry);
        for (TocEntry child : entry.children) {
            collapseRoot(child);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(this.mContext).inflate(R.layout.toc_list_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TocEntry entry = this.mEntries.get(position);
        holder.title.setText(entry.title);
        holder.itemView.setActivated(entry == this.mCurrentEntry);

        holder.title.setPadding(entry.depth * Utils.dpToPx(this.mContext, 20), 0, 0, 0);

        holder.expand.setVisibility(entry.children.isEmpty() ? View.GONE : View.VISIBLE);
        holder.setExpanded(this.mExpandedRoots.contains(entry));
    }

    @Override
    public int getItemCount() {
        return this.mEntries.size();
    }

    public int getFinalPosition(TocEntry entry) {
        return this.mEntries.indexOf(entry);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public ImageView expand;
        public TextView title;

        public ViewHolder(View view) {
            super(view);
            this.title = view.findViewById(R.id.title);
            this.expand = view.findViewById(R.id.expand);

            view.setOnClickListener(v -> onItemClicked());
            this.expand.setOnClickListener(v -> onExpandClicked());
        }

        private void onItemClicked() {
            int position = getAdapterPosition();
            if (position == RecyclerView.NO_POSITION) return;

            if (mOnClickListener != null) {
                mOnClickListener.onTocEntryClick(mEntries.get(position));
            }
        }

        private void onExpandClicked() {
            int position = getAdapterPosition();
            if (position == RecyclerView.NO_POSITION) return;

            setExpanded(toggleRoot(mEntries.get(position)));
        }

        public void setExpanded(boolean expanded) {
            this.expand.setImageResource(expanded ? R.drawable.ic_expand_less : R.drawable.ic_expand_more);
            this.expand.setContentDescription(expanded
                    ? mContext.getString(R.string.contents_collapse_desc)
                    : mContext.getString(R.string.contents_expand_desc));
        }
    }
}
