package com.hieuld.helium.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.core.view.ViewCompat;

import com.hieuld.helium.R;

public class ScrimInsetsFrameLayout extends FrameLayout {
    private Drawable mInsetForeground;
    private Rect mInsets;
    private OnInsetsCallback mOnInsetsCallback;
    private final Rect mTempRect = new Rect();

    public interface OnInsetsCallback {
        void onInsetsChanged(Rect insets);
    }

    public ScrimInsetsFrameLayout(Context context) {
        this(context, null);
    }

    public ScrimInsetsFrameLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ScrimInsetsFrameLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr);
    }

    private void init(Context context, AttributeSet attributeSet, int i) {
        TypedArray typedArrayObtainStyledAttributes = context.obtainStyledAttributes(attributeSet, R.styleable.ScrimInsetsView, i, 0);
        if (typedArrayObtainStyledAttributes == null) {
            return;
        }
        this.mInsetForeground = typedArrayObtainStyledAttributes.getDrawable(0);
        typedArrayObtainStyledAttributes.recycle();
        setWillNotDraw(true);
    }

    @Override
    protected boolean fitSystemWindows(Rect insets) {
        this.mInsets = new Rect(insets);
        setWillNotDraw(this.mInsetForeground == null);
        ViewCompat.postInvalidateOnAnimation(this);

        if (this.mOnInsetsCallback != null) {
            this.mOnInsetsCallback.onInsetsChanged(this.mInsets);
        }
        return true;
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        int width = getWidth();
        int height = getHeight();

        if (this.mInsets == null || this.mInsetForeground == null) {
            return;
        }

        int saveCount = canvas.save();
        canvas.translate(getScrollX(), getScrollY());

        // Top
        this.mTempRect.set(0, 0, width, this.mInsets.top);
        this.mInsetForeground.setBounds(this.mTempRect);
        this.mInsetForeground.draw(canvas);

        // Bottom
        this.mTempRect.set(0, height - this.mInsets.bottom, width, height);
        this.mInsetForeground.setBounds(this.mTempRect);
        this.mInsetForeground.draw(canvas);

        // Left
        this.mTempRect.set(0, this.mInsets.top, this.mInsets.left, height - this.mInsets.bottom);
        this.mInsetForeground.setBounds(this.mTempRect);
        this.mInsetForeground.draw(canvas);

        // Right
        this.mTempRect.set(width - this.mInsets.right, this.mInsets.top, width, height - this.mInsets.bottom);
        this.mInsetForeground.setBounds(this.mTempRect);
        this.mInsetForeground.draw(canvas);

        canvas.restoreToCount(saveCount);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (this.mInsetForeground != null) {
            this.mInsetForeground.setCallback(this);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (this.mInsetForeground != null) {
            this.mInsetForeground.setCallback(null);
        }
    }

    public void setOnInsetsCallback(OnInsetsCallback callback) {
        this.mOnInsetsCallback = callback;
    }
}
