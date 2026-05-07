package com.hieuld.helium.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import com.hieuld.helium.R;

public class ConstrainedContainerView extends FrameLayout {
    private int mMaxHeight;

    public ConstrainedContainerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ConstrainedContainerView, 0, 0);
            this.mMaxHeight = a.getDimensionPixelSize(R.styleable.ConstrainedContainerView_maxHeight, 0);
            a.recycle();
        }
    }

    public void setMaxHeight(int maxHeight) {
        this.mMaxHeight = maxHeight;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (this.mMaxHeight > 0) {
            int mode = View.MeasureSpec.getMode(heightMeasureSpec);
            int size = View.MeasureSpec.getSize(heightMeasureSpec);

            int newSize;
            if (mode != View.MeasureSpec.UNSPECIFIED) {
                newSize = Math.min(size, this.mMaxHeight);
            } else {
                newSize = this.mMaxHeight;
            }
            heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(newSize, View.MeasureSpec.AT_MOST);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}