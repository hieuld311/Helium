package com.hieuld.helium;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.database.Cursor;
import android.graphics.Color;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.core.content.ContextCompat;
import androidx.interpolator.view.animation.FastOutLinearInInterpolator;
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.hieuld.helium.db.BooksTable;
import com.hieuld.helium.util.ActionModeMultiCallback;
import com.hieuld.helium.util.ThemeUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BooksAdapter extends RecyclerView.Adapter<BooksAdapter.ViewHolder> {
    public static final int LAYOUT_GRID = 0;
    public static final int LAYOUT_LIST = 1;

    private final AppCompatActivity mActivity;
    private Cursor mCursor;
    private final boolean mDarkMode;
    private boolean mFreeformMultiSelect;
    private final RequestManager mGlide;
    private int mLayoutMode;
    private ActionMode mMultiChoiceActionMode;
    private final ActionModeMultiCallback mMultiChoiceModeListener;
    private boolean mMultiSelect;
    private final OnItemClickListener mOnItemClickListener;
    private Pattern mSearchPattern;
    private String mSearchQuery;

    private final CursorIndexContainer mIndexes = new CursorIndexContainer();
    private List<Long> mCheckedItemIds = new ArrayList<>();
    private List<Long> mMultiSelectCancelledIds = new ArrayList<>();

    public interface OnItemClickListener {
        void onItemClick(View view, long id);
    }

    public BooksAdapter(AppCompatActivity activity, OnItemClickListener listener,
                        ActionModeMultiCallback multiChoiceCallback) {
        this.mActivity = activity;
        this.mOnItemClickListener = listener;
        this.mMultiChoiceModeListener = multiChoiceCallback;
        this.mDarkMode = ThemeUtils.isInDarkMode(activity);
        this.mGlide = Glide.with(this.mActivity);
        setHasStableIds(true);
    }

    public void swapCursor(Cursor cursor) {
        if (this.mCursor != null) {
            this.mCursor.close();
        }
        this.mCursor = cursor;
        if (cursor != null) {
            this.mIndexes.id = cursor.getColumnIndexOrThrow("_id");
            this.mIndexes.title = cursor.getColumnIndexOrThrow(BooksTable.COLUMN_TITLE);
            this.mIndexes.creator = cursor.getColumnIndexOrThrow(BooksTable.COLUMN_CREATOR);
            this.mIndexes.cover = cursor.getColumnIndexOrThrow(BooksTable.COLUMN_COVER);
            this.mIndexes.coverBackground = cursor.getColumnIndexOrThrow(BooksTable.COLUMN_COVER_BACKGROUND);
        }

        if (this.mMultiChoiceActionMode != null) {
            if (cursor == null) {
                endMultiSelect();
                return;
            }
            List<Long> currentIds = new ArrayList<>();
            while (this.mCursor.moveToNext()) {
                currentIds.add(this.mCursor.getLong(this.mIndexes.id));
            }
            Iterator<Long> it = this.mCheckedItemIds.iterator();
            while (it.hasNext()) {
                if (!currentIds.contains(it.next())) {
                    it.remove();
                }
            }
            if (this.mCheckedItemIds.isEmpty()) {
                endMultiSelect();
            }
        }
        notifyDataSetChanged();
    }

    public void setLayoutMode(int layoutMode) {
        this.mLayoutMode = layoutMode;
        notifyDataSetChanged();
    }

    private Spannable highlightSearchQuery(String text) {
        Spannable spannable = Spannable.Factory.getInstance().newSpannable(text);
        Matcher matcher = this.mSearchPattern.matcher(text);
        while (matcher.find()) {
            spannable.setSpan(new StyleSpan(android.graphics.Typeface.BOLD),
                    matcher.start(), matcher.end(), 0);
        }
        return spannable;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutRes = (this.mLayoutMode == LAYOUT_GRID)
                ? R.layout.books_grid_item : R.layout.books_list_item;
        return new ViewHolder(LayoutInflater.from(this.mActivity).inflate(layoutRes, parent, false));
    }

    @Override
    public int getItemViewType(int position) {
        return (this.mLayoutMode == LAYOUT_GRID) ? LAYOUT_GRID : LAYOUT_LIST;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (!this.mCursor.moveToPosition(position)) {
            throw new IllegalStateException("Couldn't move cursor to position " + position
                    + " (cursor size=" + this.mCursor.getCount() + ")");
        }

        String title = this.mCursor.getString(this.mIndexes.title);
        String creator = this.mCursor.getString(this.mIndexes.creator);
        if (creator == null) {
            creator = this.mActivity.getString(R.string.book_no_creator);
        }

        if (this.mSearchQuery != null && !TextUtils.isEmpty(this.mSearchQuery.trim())) {
            holder.titleView.setText(highlightSearchQuery(title));
            holder.creatorView.setText(highlightSearchQuery(creator));
        } else {
            holder.titleView.setText(title);
            holder.creatorView.setText(creator);
        }

        String coverPath = this.mCursor.getString(this.mIndexes.cover);
        int bgColor = this.mCursor.getInt(this.mIndexes.coverBackground);
        int textColor = -1;

        if (this.mDarkMode) {
            bgColor = ContextCompat.getColor(this.mActivity, R.color.book_item_background_color);
        } else if (bgColor == 0) {
            textColor = ContextCompat.getColor(this.mActivity, R.color.black_high);
            bgColor = 0xFFF0F0F0;
        }

        int rgbDarkened = Color.rgb(
                (int) (Color.red(bgColor) * 0.8f),
                (int) (Color.green(bgColor) * 0.8f),
                (int) (Color.blue(bgColor) * 0.8f));

        if (coverPath != null) {
            this.mGlide.load(coverPath)
                    .transition(DrawableTransitionOptions.withCrossFade(100))
                    .into(holder.coverView);
        } else {
            this.mGlide.clear(holder.coverView);
            holder.coverView.setImageBitmap(null);
        }

        if (this.mLayoutMode == LAYOUT_GRID) {
            holder.titleView.setTextColor(textColor);
            holder.creatorView.setTextColor(textColor);
            holder.footerView.setBackgroundColor(bgColor);
        } else if (this.mDarkMode) {
            holder.footerView.setBackgroundColor(bgColor);
        }

        holder.coverContainerView.setBackgroundColor(rgbDarkened);
        holder.noCoverView.setVisibility(coverPath != null ? View.GONE : View.VISIBLE);

        boolean isChecked = this.mCheckedItemIds.contains(this.mCursor.getLong(this.mIndexes.id));
        if (isChecked != holder.itemView.isActivated()) {
            if (holder.itemView.getWindowVisibility() == View.VISIBLE) {
                holder.setChecked(isChecked);
            } else {
                holder.setCheckedInstant(isChecked);
            }
        }
    }

    @Override
    public int getItemCount() {
        return this.mCursor != null ? this.mCursor.getCount() : 0;
    }

    @Override
    public long getItemId(int position) {
        if (!this.mCursor.moveToPosition(position)) {
            throw new IllegalStateException("Couldn't move cursor to position " + position
                    + " (cursor size=" + this.mCursor.getCount() + ")");
        }
        return this.mCursor.getLong(this.mIndexes.id);
    }

    public ArrayList<Long> getCheckedItemIds() {
        return new ArrayList<>(this.mCheckedItemIds);
    }

    public void setFreeformMultiSelect(boolean enable) {
        if (enable) {
            if (this.mMultiSelect) {
                if (this.mFreeformMultiSelect) return;
                endMultiSelect();
            }
            this.mMultiSelectCancelledIds = new ArrayList<>();
            this.mMultiSelect = true;
            this.mFreeformMultiSelect = true;
        } else {
            endMultiSelect();
        }
    }

    public void endMultiSelect() {
        if (this.mMultiSelect) {
            if (this.mMultiChoiceActionMode != null) {
                this.mMultiChoiceActionMode.finish();
                this.mMultiChoiceActionMode = null;
            }
            if (!this.mCheckedItemIds.isEmpty()) {
                this.mMultiSelectCancelledIds = new ArrayList<>(this.mCheckedItemIds);
                this.mCheckedItemIds.clear();
                notifyDataSetChanged();
            }
            this.mMultiSelect = false;
            this.mFreeformMultiSelect = false;
        }
    }

    public void restoreSelectionState(List<Long> savedIds) {
        this.mMultiSelect = true;
        this.mCheckedItemIds = new ArrayList<>(savedIds);
        this.mMultiChoiceActionMode = this.mActivity.startSupportActionMode(this.mMultiChoiceModeListener);
        if (this.mMultiChoiceModeListener != null) {
            this.mMultiChoiceModeListener.onCheckedItemsChanged(this.mMultiChoiceActionMode);
        }
    }

    public void selectAll() {
        if (this.mCursor == null || this.mCursor.getCount() == 0) return;

        this.mCheckedItemIds.clear();
        this.mCursor.moveToPosition(-1);
        while (this.mCursor.moveToNext()) {
            this.mCheckedItemIds.add(this.mCursor.getLong(this.mIndexes.id));
        }

        if (!this.mMultiSelect) {
            this.mMultiSelect = true;
            this.mMultiChoiceActionMode = this.mActivity.startSupportActionMode(this.mMultiChoiceModeListener);
        }

        if (this.mMultiChoiceModeListener != null) {
            this.mMultiChoiceModeListener.onCheckedItemsChanged(this.mMultiChoiceActionMode);
        }
        notifyDataSetChanged();
    }

    public void setSearchQuery(String query) {
        this.mSearchQuery = query;
        if (query != null) {
            this.mSearchPattern = Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE);
        }
    }

    public class ViewHolder extends RecyclerView.ViewHolder
            implements View.OnClickListener, View.OnLongClickListener {
        public View coverContainerView;
        public ImageView coverView;
        public TextView creatorView;
        public View footerView;
        public View noCoverView;
        public View selectedCheck;
        public View selectedView;
        public TextView titleView;

        public ViewHolder(View view) {
            super(view);
            this.titleView = view.findViewById(R.id.title);
            this.creatorView = view.findViewById(R.id.creator);
            this.coverContainerView = view.findViewById(R.id.cover_container);
            this.coverView = view.findViewById(R.id.cover);
            this.noCoverView = view.findViewById(R.id.noCover);
            this.footerView = view.findViewById(R.id.footer);
            this.selectedView = view.findViewById(R.id.selected_scrim);
            this.selectedCheck = view.findViewById(R.id.selected_check);

            view.setOnClickListener(this);
            view.setOnLongClickListener(this);
        }

        @Override
        public void onClick(View view) {
            if (mMultiSelect) {
                setCheckedAndChangeState(!mCheckedItemIds.contains(getItemId()));
                if (mCheckedItemIds.isEmpty() && !mFreeformMultiSelect) {
                    endMultiSelect();
                }
                return;
            }
            if (mOnItemClickListener != null) {
                mOnItemClickListener.onItemClick(view, getItemId());
            }
        }

        @Override
        public boolean onLongClick(View view) {
            if (mMultiSelect) return false;

            mMultiChoiceActionMode = mActivity.startSupportActionMode(mMultiChoiceModeListener);
            mMultiSelectCancelledIds.clear();
            mMultiSelect = true;
            setCheckedAndChangeState(true);
            return true;
        }

        public void setChecked(boolean checked) {
            this.itemView.setActivated(checked);
            animateCheckedState(checked);
        }

        public void setCheckedAndChangeState(boolean checked) {
            if (checked) {
                mCheckedItemIds.add(getItemId());
            } else {
                mCheckedItemIds.remove(getItemId());
            }
            setChecked(checked);
            if (mMultiChoiceModeListener != null) {
                mMultiChoiceModeListener.onCheckedItemsChanged(mMultiChoiceActionMode);
            }
        }

        public void animateCheckedState(boolean checked) {
            TimeInterpolator interpolator = checked
                    ? new LinearOutSlowInInterpolator()
                    : new FastOutLinearInInterpolator();

            this.selectedView.animate().cancel();
            this.selectedCheck.animate().cancel();

            if (checked) {
                this.selectedView.setVisibility(View.VISIBLE);
                this.selectedView.setAlpha(0.0f);
                this.selectedView.animate()
                        .alpha(1.0f).setDuration(150L).setInterpolator(interpolator).setListener(null);

                this.selectedCheck.setVisibility(View.VISIBLE);
                this.selectedCheck.setScaleX(0.0f);
                this.selectedCheck.setScaleY(0.0f);
                this.selectedCheck.setAlpha(0.0f);
                this.selectedCheck.animate()
                        .scaleX(1.0f).scaleY(1.0f).alpha(1.0f)
                        .setDuration(150L).setInterpolator(interpolator).setListener(null);
            } else {
                this.selectedView.animate()
                        .alpha(0.0f).setDuration(80L).setInterpolator(interpolator)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                selectedView.setVisibility(View.GONE);
                            }
                        });

                this.selectedCheck.animate()
                        .scaleX(0.0f).scaleY(0.0f).alpha(1.0f)
                        .setDuration(80L).setInterpolator(interpolator)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                selectedCheck.setVisibility(View.GONE);
                            }
                        });
            }
        }

        public void resetCheckedProperties() {
            this.selectedView.animate().cancel();
            this.selectedView.setAlpha(1.0f);
            this.selectedCheck.animate().cancel();
            this.selectedCheck.setScaleX(1.0f);
            this.selectedCheck.setScaleY(1.0f);
        }

        public void setCheckedInstant(boolean checked) {
            this.itemView.setActivated(checked);
            resetCheckedProperties();
            this.selectedView.setVisibility(checked ? View.VISIBLE : View.GONE);
            this.selectedCheck.setVisibility(checked ? View.VISIBLE : View.GONE);
        }
    }

    private static class CursorIndexContainer {
        int cover;
        int coverBackground;
        int creator;
        int id;
        int title;
    }
}
