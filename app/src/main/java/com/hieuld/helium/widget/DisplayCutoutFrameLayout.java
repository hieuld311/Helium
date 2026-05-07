package com.hieuld.helium.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.DisplayCutout;
import android.view.WindowInsets;
import android.widget.FrameLayout;

public class DisplayCutoutFrameLayout extends FrameLayout {
    public static final int MODE_OFF = 0;
    public static final int MODE_INSET = 1;
    public static final int MODE_INSET_PAINT = 2;

    private ColorDrawable mColor;
    private DisplayCutout mDisplayCutout;
    private boolean mInsetCutout;
    private boolean mPaintCutout;

    public DisplayCutoutFrameLayout(Context context) {
        this(context, null);
    }

    public DisplayCutoutFrameLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DisplayCutoutFrameLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        if (Build.VERSION.SDK_INT >= 28) {
            setOnApplyWindowInsetsListener((v, insets) -> onApplyWindowInsets(insets));
        }
    }

    public void setInsetCutout(int mode) {
        this.mInsetCutout = mode != MODE_OFF;
        this.mPaintCutout = mode == MODE_INSET_PAINT;

        if (!this.mInsetCutout) {
            setPadding(0, 0, 0, 0);
        }

        if (!this.mPaintCutout) {
            this.mColor = null;
        } else {
            this.mColor = new ColorDrawable(0xFF000000);
        }
        setWillNotDraw(!this.mPaintCutout);
    }

    // Yêu cầu API 28 trở lên để sử dụng DisplayCutout
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        if (!this.mInsetCutout) {
            return insets;
        }

        if (Build.VERSION.SDK_INT >= 28) {
            this.mDisplayCutout = insets.getDisplayCutout();
            if (this.mDisplayCutout == null) {
                return insets;
            }
            setPadding(
                    this.mDisplayCutout.getSafeInsetLeft(),
                    this.mDisplayCutout.getSafeInsetTop(),
                    this.mDisplayCutout.getSafeInsetRight(),
                    this.mDisplayCutout.getSafeInsetBottom()
            );
        }
        return insets;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (!this.mPaintCutout || this.mDisplayCutout == null || Build.VERSION.SDK_INT < 28) {
            return;
        }

        int width = getWidth();
        int height = getHeight();

        // Vẽ phần trên (Top)
        this.mColor.setBounds(0, 0, width, this.mDisplayCutout.getSafeInsetTop());
        this.mColor.draw(canvas);

        // Vẽ phần bên trái (Left)
        this.mColor.setBounds(0, this.mDisplayCutout.getSafeInsetTop(), this.mDisplayCutout.getSafeInsetLeft(), height - this.mDisplayCutout.getSafeInsetBottom());
        this.mColor.draw(canvas);

        // Vẽ phần dưới (Bottom)
        this.mColor.setBounds(0, height - this.mDisplayCutout.getSafeInsetBottom(), width, height);
        this.mColor.draw(canvas);

        // Vẽ phần bên phải (Right)
        this.mColor.setBounds(width - this.mDisplayCutout.getSafeInsetRight(), this.mDisplayCutout.getSafeInsetTop(), width, height - this.mDisplayCutout.getSafeInsetBottom());
        this.mColor.draw(canvas);
    }
}
