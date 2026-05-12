package com.hieuld.helium;

import android.content.Context;
import android.text.Spannable;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.hieuld.helium.book.SearchResult;
import java.util.ArrayList;
import java.util.List;

public class ReaderSearchResultsAdapter extends RecyclerView.Adapter<ReaderSearchResultsAdapter.ViewHolder> {
    private static final int TYPE_HEADER = 2;
    private static final int TYPE_ITEM = 0;
    private static final int TYPE_ITEM_TOC = 1;
    private Context mContext;
    private OnClickListener mOnClickListener;
    private List<SearchResult> mResults = new ArrayList<>();
    private boolean mLoading = true;

    public interface OnClickListener {
        void onSearchResultClick(SearchResult searchResult);
    }

    public ReaderSearchResultsAdapter(Context context) {
        this.mContext = context;
    }

    public void setOnClickListener(OnClickListener onClickListener) {
        this.mOnClickListener = onClickListener;
    }

    public void finished(List<SearchResult> list) {
        this.mResults = list;
        this.mLoading = false;
        notifyDataSetChanged();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        if (i == TYPE_ITEM) {
            return new ViewHolder(LayoutInflater.from(this.mContext).inflate(R.layout.reader_search_result, viewGroup, false));
        }
        if (i == TYPE_ITEM_TOC) {
            return new ViewHolder(LayoutInflater.from(this.mContext).inflate(R.layout.reader_search_result_toc, viewGroup, false));
        }
        return new ViewHolder(LayoutInflater.from(this.mContext).inflate(R.layout.reader_search_header, viewGroup, false));
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, int i) {
        if (getItemViewType(i) != TYPE_HEADER) {
            final SearchResult searchResult = this.mResults.get(i - 1);
            Spannable spannableNewSpannable = Spannable.Factory.getInstance().newSpannable(searchResult.snippet);
            spannableNewSpannable.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this.mContext, R.color.app_secondary)), searchResult.snippetIndexStart, searchResult.snippetIndexEnd, 0);
            viewHolder.snippet.setText(spannableNewSpannable);
            viewHolder.itemView.setOnClickListener(view -> {
                if (mOnClickListener != null) {
                    mOnClickListener.onSearchResultClick(searchResult);
                }
            });
        } else if (this.mLoading) {
            viewHolder.info.setText(R.string.reader_search_searching);
            viewHolder.progressBar.setVisibility(View.VISIBLE);
        } else {
            viewHolder.info.setText(this.mContext.getResources().getQuantityString(R.plurals.reader_search_results, this.mResults.size(), this.mResults.size()));
            viewHolder.progressBar.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public int getItemCount() {
        return this.mResults.size() + 1;
    }

    @Override
    public int getItemViewType(int i) {
        if (i == 0) {
            return TYPE_HEADER;
        }
        return this.mResults.get(i - 1).type == 0 ? TYPE_ITEM : TYPE_ITEM_TOC;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView info;
        public ProgressBar progressBar;
        public TextView snippet;
        public TextView title;

        public ViewHolder(View view) {
            super(view);
            this.title = (TextView) view.findViewById(R.id.title);
            this.snippet = (TextView) view.findViewById(R.id.snippet);
            this.info = (TextView) view.findViewById(R.id.info);
            this.progressBar = (ProgressBar) view.findViewById(R.id.progress);
        }
    }
}