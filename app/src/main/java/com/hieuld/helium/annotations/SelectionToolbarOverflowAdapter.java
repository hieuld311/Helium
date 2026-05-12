package com.hieuld.helium.annotations;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.hieuld.helium.R;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView adapter for the overflow menu inside {@link SelectionToolbarView}.
 *
 * The overflow menu shows:
 * <ul>
 *   <li>A "back" button (view type {@link #VIEW_TYPE_BACK}) that dismisses the overflow.</li>
 *   <li>One row per {@link Action} (view type {@link #VIEW_TYPE_ACTION}) for each
 *       third-party PROCESS_TEXT activity or built-in action (e.g. Web Search).</li>
 * </ul>
 *
 * The back button is placed at the top or bottom depending on whether the toolbar is
 * shown above or below the selection (controlled by {@link #setTopToBottom}).
 */
public class SelectionToolbarOverflowAdapter extends RecyclerView.Adapter<SelectionToolbarOverflowAdapter.ViewHolder> {

    private static final int VIEW_TYPE_ACTION = 0;
    private static final int VIEW_TYPE_BACK   = 1;

    // ── Data ─────────────────────────────────────────────────────────────────

    /** Describes a single overflow action (built-in or resolved via PROCESS_TEXT). */
    public static class Action {
        /** Display label shown in the overflow row. */
        public String name;
        /** Package name of the target activity, or {@code null} for built-in actions. */
        public String packageName;
        /** Activity class name, or a sentinel string such as {@code "search"} for built-ins. */
        public String className;
    }

    /** Callbacks from the overflow adapter to the toolbar. */
    public interface Listener {
        /** Called when the user taps an overflow action row. */
        void onActionClick(Action action);
        /** Called when the user taps the back button to close the overflow. */
        void onOverflowBack();
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final Context  mContext;
    private final Listener mListener;
    private List<Action>   mActions    = new ArrayList<>();
    /** When {@code true}, the back button is at position 0 (toolbar shown below selection). */
    private boolean        mTopToBottom;

    // ── Constructor ───────────────────────────────────────────────────────────

    public SelectionToolbarOverflowAdapter(Context context, Listener listener) {
        mContext  = context;
        mListener = listener;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void setActions(List<Action> actions) {
        mActions = actions;
    }

    /**
     * @param topToBottom {@code true} if the toolbar is displayed below the selection
     *                    (back button goes at the top of the list).
     */
    public void setTopToBottom(boolean topToBottom) {
        mTopToBottom = topToBottom;
    }

    // ── Adapter ───────────────────────────────────────────────────────────────

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        int layoutRes = (viewType == VIEW_TYPE_ACTION)
                ? R.layout.selection_toolbar_overflow_action
                : R.layout.selection_toolbar_overflow_back;
        View itemView = LayoutInflater.from(mContext).inflate(layoutRes, parent, false);
        return new ViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        if (holder.nameView != null) {
            // Action row — skip the back-button slot when it sits at the top
            int actionIndex = mTopToBottom ? position - 1 : position;
            final Action action = mActions.get(actionIndex);
            holder.nameView.setText(action.name);
            holder.itemView.setOnClickListener(v -> mListener.onActionClick(action));
        } else {
            // Back button row
            holder.backView.setOnClickListener(v -> mListener.onOverflowBack());
        }
    }

    @Override
    public int getItemCount() {
        return mActions.size() + 1; // +1 for the back button
    }

    @Override
    public int getItemViewType(int position) {
        if (mTopToBottom && position == 0)              return VIEW_TYPE_BACK;
        if (!mTopToBottom && position == mActions.size()) return VIEW_TYPE_BACK;
        return VIEW_TYPE_ACTION;
    }

    // ── ViewHolder ────────────────────────────────────────────────────────────

    public static class ViewHolder extends RecyclerView.ViewHolder {
        /** Non-null for action rows. */
        final TextView    nameView;
        /** Non-null for the back-button row. */
        final ImageButton backView;

        public ViewHolder(View itemView) {
            super(itemView);
            nameView = itemView.findViewById(R.id.name);
            backView = itemView.findViewById(R.id.back);
        }
    }
}
