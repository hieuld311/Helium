package com.hieuld.helium.widget;

import android.content.Context;
import android.util.AttributeSet;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class AutoFitRecyclerView extends RecyclerView {
    private final int mPaddingLeft;
    private final int mPaddingRight;
    private int mSpanWidth;

    public AutoFitRecyclerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mPaddingLeft = getPaddingLeft();
        this.mPaddingRight = getPaddingRight();
    }

    public void setSpanWidth(int spanWidth) {
        this.mSpanWidth = spanWidth;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, heightSpec);

        if (!(getLayoutManager() instanceof GridLayoutManager) || this.mSpanWidth <= 0) {
            setPadding(this.mPaddingLeft, getPaddingTop(), this.mPaddingRight, getPaddingBottom());
            return;
        }

        int availableWidth = getMeasuredWidth() - this.mPaddingLeft - this.mPaddingRight;
        int spanCount = availableWidth / this.mSpanWidth;

        if (spanCount > 0) {
            ((GridLayoutManager) getLayoutManager()).setSpanCount(spanCount);
        }

        int remainder = availableWidth - (spanCount * this.mSpanWidth);
        if (remainder > 0) {
            int extraPadding = remainder / 2;
            setPadding(this.mPaddingLeft + extraPadding, getPaddingTop(), this.mPaddingRight + extraPadding, getPaddingBottom());
        } else {
            setPadding(this.mPaddingLeft, getPaddingTop(), this.mPaddingRight, getPaddingBottom());
        }
    }
}
