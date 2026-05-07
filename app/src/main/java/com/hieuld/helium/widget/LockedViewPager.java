package com.hieuld.helium.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.viewpager.widget.ViewPager;

public class LockedViewPager extends ViewPager {

    public LockedViewPager(Context context) {
        super(context);
    }

    public LockedViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        // Trả về false để chặn việc ViewPager bắt sự kiện vuốt ngang
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Trả về false để chặn việc cuộn (swipe)
        return false;
    }
}
