package com.hieuld.helium.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import com.github.chrisbanes.photoview.PhotoView;
import com.hieuld.helium.util.Utils;

public class PhotoViewDragToCloseFrame extends FrameLayout implements ValueAnimator.AnimatorUpdateListener {
    private static final float CLOSE_THRESHOLD = 0.5f;
    private static final int FINAL_DISTANCE_DP = 100;

    private final ValueAnimator mCancelAnimation;
    private float mCancelRatio;
    private float mCancelScale;
    private float mCancelTranslationY;
    private boolean mDragging;
    private final int mFinalDistance;
    private float mLastY;
    private Listener mListener;
    private PhotoView mPhotoView;
    private int mPointerId;
    private boolean mStopped;
    private final int mTouchSlop;
    private float mTouchY;
    private float mTranslationY;
    private VelocityTracker mVelocityTracker;

    public interface Listener {
        void onDragClose();
        void onDragCloseRatio(float ratio);
    }

    public PhotoViewDragToCloseFrame(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mCancelAnimation = ValueAnimator.ofFloat(1.0f, 0.0f);
        this.mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        this.mFinalDistance = Utils.dpToPx(context, FINAL_DISTANCE_DP);

        this.mCancelAnimation.addUpdateListener(this);
        this.mCancelAnimation.setDuration(300L);
        this.mCancelAnimation.setInterpolator(new FastOutSlowInInterpolator());
    }

    public void setOnCloseListener(Listener listener) {
        this.mListener = listener;
    }

    public void stop() {
        if (this.mCancelAnimation.isRunning()) {
            this.mCancelAnimation.cancel();
        }
        if (this.mVelocityTracker != null) {
            this.mVelocityTracker.recycle();
            this.mVelocityTracker = null;
        }
        this.mStopped = true;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (this.mStopped) {
            return false;
        }
        if (this.mPhotoView == null) {
            this.mPhotoView = (PhotoView) getChildAt(0);
        }
        if (this.mPhotoView.getScale() != 1.0f) {
            if (this.mVelocityTracker != null) {
                this.mVelocityTracker.recycle();
                this.mVelocityTracker = null;
            }
            return super.onInterceptTouchEvent(event);
        }
        if (this.mVelocityTracker == null) {
            this.mVelocityTracker = VelocityTracker.obtain();
        }
        this.mVelocityTracker.addMovement(event);

        int actionMasked = event.getActionMasked();
        if (actionMasked == MotionEvent.ACTION_DOWN) {
            this.mTouchY = event.getY();
        } else if (actionMasked == MotionEvent.ACTION_MOVE) {
            if (event.getPointerCount() == 1) {
                float y = event.getY();
                if (Math.abs(y - this.mTouchY) > this.mTouchSlop) {
                    this.mDragging = true;
                    this.mPointerId = event.getPointerId(0);
                    this.mLastY = y;
                    this.mTranslationY = 0.0f;
                    return true;
                }
            }
        } else if (actionMasked == MotionEvent.ACTION_UP || actionMasked == MotionEvent.ACTION_CANCEL) {
            if (this.mVelocityTracker != null) {
                this.mVelocityTracker.recycle();
                this.mVelocityTracker = null;
            }
        }
        return super.onInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (this.mStopped || !this.mDragging) {
            return super.onTouchEvent(event);
        }

        int pointerIndex = event.findPointerIndex(this.mPointerId);
        if (pointerIndex == -1) {
            finishDrag();
            return false;
        }

        if (this.mVelocityTracker == null) {
            this.mVelocityTracker = VelocityTracker.obtain();
        }
        this.mVelocityTracker.addMovement(event);

        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_MOVE) {
            this.mTranslationY += (event.getY(pointerIndex) - this.mLastY);
            this.mPhotoView.setTranslationY(this.mTranslationY);
            this.mLastY = event.getY(pointerIndex);

            float dragRatio = Math.min(Math.abs(this.mTranslationY) / this.mFinalDistance, 1.0f);
            float scale = 1.0f - (0.1f * dragRatio); // Thu nhỏ xuống 0.9x khi vuốt

            this.mPhotoView.setScaleX(scale);
            this.mPhotoView.setScaleY(scale);

            if (this.mListener != null) {
                this.mListener.onDragCloseRatio(dragRatio);
            }
            return true;

        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            // Android chuẩn: Tính vận tốc theo pixel/giây (1000ms)
            this.mVelocityTracker.computeCurrentVelocity(1000);

            if (Math.abs(this.mVelocityTracker.getYVelocity(this.mPointerId)) > this.mFinalDistance) {
                if (this.mListener != null) {
                    this.mListener.onDragClose();
                }
                this.mDragging = false;
            } else {
                finishDrag();
            }

            if (this.mVelocityTracker != null) {
                this.mVelocityTracker.recycle();
                this.mVelocityTracker = null;
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    private void finishDrag() {
        float absTranslation = Math.abs(this.mTranslationY);
        if (absTranslation > this.mFinalDistance * CLOSE_THRESHOLD) {
            if (this.mListener != null) {
                this.mListener.onDragClose();
            }
        } else {
            this.mCancelScale = this.mPhotoView.getScaleX();
            this.mCancelTranslationY = this.mTranslationY;
            this.mCancelRatio = Math.min(absTranslation / this.mFinalDistance, 1.0f);
            this.mCancelAnimation.start();
        }
        this.mDragging = false;
    }

    @Override
    public void onAnimationUpdate(ValueAnimator animator) {
        float fraction = animator.getAnimatedFraction();

        float scale = this.mCancelScale + ((1.0f - this.mCancelScale) * fraction);
        this.mPhotoView.setScaleX(scale);
        this.mPhotoView.setScaleY(scale);

        float translationY = this.mCancelTranslationY + ((0.0f - this.mCancelTranslationY) * fraction);
        this.mPhotoView.setTranslationY(translationY);

        if (this.mListener != null) {
            float ratio = this.mCancelRatio + ((0.0f - this.mCancelRatio) * fraction);
            this.mListener.onDragCloseRatio(ratio);
        }
    }
}
