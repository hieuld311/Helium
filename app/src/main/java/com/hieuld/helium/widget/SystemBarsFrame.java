package com.hieuld.helium.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import com.hieuld.helium.R;

public class SystemBarsFrame extends FrameLayout {
    private static final Rect EMPTY_RECT = new Rect();
    private boolean mDoInsetBottom;
    private boolean mDoInsetTop;
    private Rect mInsets;
    private final ColorDrawable mSystemBarsBackground;

    public SystemBarsFrame(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mSystemBarsBackground = new ColorDrawable();

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SystemBarsFrame, 0, 0);
            this.mSystemBarsBackground.setColor(a.getColor(R.styleable.SystemBarsFrame_systemBarsColor, 0));
            this.mDoInsetTop = a.getBoolean(R.styleable.SystemBarsFrame_doInsetTop, true);
            this.mDoInsetBottom = a.getBoolean(R.styleable.SystemBarsFrame_doInsetBottom, true);
            a.recycle();
        }
    }

    public void setSystemBarsBackgroundColor(int color) {
        this.mSystemBarsBackground.setColor(color);
        invalidate();
    }

    @Override
    protected boolean fitSystemWindows(Rect insets) {
        if (getFitsSystemWindows()) {
            setPadding(
                    insets.left,
                    this.mDoInsetTop ? insets.top : 0,
                    insets.right,
                    this.mDoInsetBottom ? insets.bottom : 0
            );
            this.mInsets = new Rect(insets);
        } else {
            setPadding(0, 0, 0, 0);
            this.mInsets = EMPTY_RECT;
        }
        setWillNotDraw(this.mInsets.equals(EMPTY_RECT));
        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();

        if (this.mInsets == null || this.mInsets.equals(EMPTY_RECT)) return;

        if (this.mDoInsetTop && this.mInsets.top > 0) {
            this.mSystemBarsBackground.setBounds(0, 0, width, this.mInsets.top);
            this.mSystemBarsBackground.draw(canvas);
        }
        if (this.mInsets.left > 0) {
            this.mSystemBarsBackground.setBounds(0, this.mInsets.top, this.mInsets.left, height - this.mInsets.bottom);
            this.mSystemBarsBackground.draw(canvas);
        }
        if (this.mDoInsetBottom && this.mInsets.bottom > 0) {
            this.mSystemBarsBackground.setBounds(0, height - this.mInsets.bottom, width, height);
            this.mSystemBarsBackground.draw(canvas);
        }
        if (this.mInsets.right > 0) {
            this.mSystemBarsBackground.setBounds(width - this.mInsets.right, this.mInsets.top, width, height - this.mInsets.bottom);
            this.mSystemBarsBackground.draw(canvas);
        }
    }
}
