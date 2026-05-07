package com.hieuld.helium.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ScrollView;

public class ExpansionScrollView extends ScrollView {
    private View mButton;
    private View mContainer;

    public ExpansionScrollView(Context context) {
        super(context);
    }

    public ExpansionScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ExpansionScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setExpandButtonAndContainer(View button, View container) {
        this.mButton = button;
        this.mContainer = container;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        if (this.mButton == null || this.mContainer == null) {
            return;
        }

        int measuredHeight = getMeasuredHeight();

        // Kiểm tra xem container có đang mở (VISIBLE) không
        if (this.mContainer.getVisibility() == View.VISIBLE) {
            View child = getChildAt(0);
            if (child != null) {
                if (this.mButton.getMeasuredHeight() == 0) {
                    this.mButton.measure(
                            View.MeasureSpec.makeMeasureSpec(child.getMeasuredWidth(), View.MeasureSpec.AT_MOST),
                            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                    );
                }

                int expandedHeight = (child.getMeasuredHeight() - this.mContainer.getMeasuredHeight()) + this.mButton.getMeasuredHeight();
                measuredHeight = Math.min(measuredHeight, expandedHeight);
            }
        }
        setMeasuredDimension(getMeasuredWidth(), measuredHeight);
    }
}