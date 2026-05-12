package com.hieuld.helium.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.hieuld.helium.util.Utils;

public class OverScrollView extends FrameLayout {
    private final int mActionThreshold;
    private Delegate mDelegate;
    private int mDownX;
    private int mDownY;
    private boolean mMovingVertically;
    private final ValueAnimator mOverScrollPullAnimation;
    private boolean mOverScrolling;
    private int mPullX;
    private boolean mReversed;
    private boolean mTouchCancelled;
    private int mTouchOffset;
    private final int mTouchSlop;
    private boolean mTouchSlopped;
    private boolean mTransition;
    private boolean mVerticalRestricted;

    public interface Delegate {
        boolean canOverScroll(int direction);
        void onOverScroll(int direction);
    }

    public OverScrollView(Context context) {
        this(context, null);
    }

    public OverScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        this.mActionThreshold = Utils.dpToPx(context, 50);

        this.mOverScrollPullAnimation = new ValueAnimator();
        this.mOverScrollPullAnimation.setDuration(300L);
        this.mOverScrollPullAnimation.addUpdateListener(animation -> {
            this.mPullX = (Integer) animation.getAnimatedValue();
            invalidate();
        });
    }

    public void setDelegate(Delegate delegate) {
        this.mDelegate = delegate;
    }

    public void setReversed(boolean reversed) {
        this.mReversed = reversed;
    }

    public void setVerticalRestricted(boolean verticalRestricted) {
        this.mVerticalRestricted = verticalRestricted;
    }

    private View getChild() {
        return getChildAt(0);
    }

    private boolean isChildReadyForOverScroll(int direction) {
        View child = getChild();
        if (!(child instanceof ViewGroup)) {
            return true;
        }
        ViewGroup viewGroup = (ViewGroup) child;
        while (viewGroup != null) {
            if (viewGroup.canScrollHorizontally(direction)) {
                return false;
            }
            View nextChild = viewGroup.getChildAt(0);
            if (!(nextChild instanceof ViewGroup)) {
                return true;
            }
            viewGroup = (ViewGroup) nextChild;
        }
        return true;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (this.mTransition) {
            return false;
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            this.mDownX = (int) event.getX();
            this.mDownY = (int) event.getY();
            this.mOverScrolling = false;
            this.mTouchSlopped = false;
            this.mTouchCancelled = false;
            this.mMovingVertically = false;
        } else if (action == MotionEvent.ACTION_MOVE && handleMoveEvent(event)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (this.mTransition) {
            return false;
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_MOVE) {
            if (handleMoveEvent(event)) {
                return true;
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (this.mOverScrolling) {
                int direction = 0;
                if (this.mPullX < -this.mActionThreshold) {
                    direction = -1;
                } else if (this.mPullX > this.mActionThreshold) {
                    direction = 1;
                }

                if (direction != 0) {
                    final int finalDirection = this.mReversed ? (direction > 0 ? -1 : 1) : direction;
                    this.mOverScrollPullAnimation.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            mOverScrollPullAnimation.removeAllListeners();
                            mPullX = 0;
                            mTransition = false;
                            invalidate();
                            if (mDelegate != null) {
                                mDelegate.onOverScroll(finalDirection);
                            }
                        }
                    });
                    this.mTransition = true;
                }

                this.mOverScrollPullAnimation.setIntValues(this.mPullX, getWidth() * direction);
                this.mOverScrollPullAnimation.start();
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    public void cancel() {
        if (this.mTransition) {
            this.mOverScrollPullAnimation.removeAllListeners();
            this.mOverScrollPullAnimation.cancel();
            this.mTransition = false;
        }
        this.mOverScrolling = false;
        this.mPullX = 0;
        this.mTouchCancelled = true;
        invalidate();
    }

    public void doOverScroll(int direction) {
        if ((direction == 1 || direction == -1) && !this.mTransition) {
            final int finalDirection;
            if (!this.mReversed) {
                finalDirection = direction;
            } else {
                finalDirection = direction <= 0 ? 1 : -1;
            }

            this.mOverScrollPullAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mOverScrollPullAnimation.removeAllListeners();
                    mPullX = 0;
                    mTransition = false;
                    invalidate();
                    if (mDelegate != null) {
                        mDelegate.onOverScroll(finalDirection);
                    }
                }
            });

            this.mOverScrollPullAnimation.setIntValues(0, getWidth() * direction);
            this.mOverScrollPullAnimation.start();
            this.mTransition = true;
        }
    }

    private boolean handleMoveEvent(MotionEvent event) {
        if (this.mTouchCancelled) {
            return false;
        }

        int deltaX = ((int) event.getX()) - this.mDownX;
        int deltaY = ((int) event.getY()) - this.mDownY;

        if (!this.mTouchSlopped) {
            if (!this.mMovingVertically && Math.abs(deltaY) > Math.abs(deltaX)) {
                this.mMovingVertically = true;
            }
            boolean isHorizontalSwipe = !this.mMovingVertically && Math.abs(deltaX) > Math.abs(deltaY) * 2;

            if ((!this.mVerticalRestricted || isHorizontalSwipe) && Math.abs(deltaX) > this.mTouchSlop) {
                this.mTouchOffset = -deltaX;
                this.mTouchSlopped = true;
            } else {
                return false;
            }
        }

        int offsetDeltaX = deltaX + this.mTouchOffset;
        boolean canOverScrollLeft = isChildReadyForOverScroll(-1) && this.mDelegate != null && this.mDelegate.canOverScroll(this.mReversed ? 1 : -1);
        boolean canOverScrollRight = isChildReadyForOverScroll(1) && this.mDelegate != null && this.mDelegate.canOverScroll(this.mReversed ? -1 : 1);

        int targetPullX = 0;
        if ((offsetDeltaX > 0 && canOverScrollLeft) || (offsetDeltaX < 0 && canOverScrollRight)) {
            targetPullX = -offsetDeltaX;
            this.mOverScrolling = true;
        }

        if (targetPullX != this.mPullX) {
            this.mPullX = targetPullX;
            invalidate();
        }
        return this.mOverScrolling;
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        canvas.save();
        canvas.translate(-this.mPullX, 0.0f);
        boolean result = super.drawChild(canvas, child, drawingTime);
        canvas.restore();
        return result;
    }
}