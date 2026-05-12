package com.hieuld.helium;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.transition.ChangeBounds;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import androidx.core.view.ViewCompat;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import com.hieuld.helium.widget.PhotoViewDragToCloseFrame;
import com.hieuld.helium.widget.ScrimInsetsFrameLayout;
import com.github.chrisbanes.photoview.OnViewTapListener;
import com.github.chrisbanes.photoview.PhotoView;

public class PhotoActivity extends BaseActivity implements View.OnSystemUiVisibilityChangeListener, OnViewTapListener, PhotoViewDragToCloseFrame.Listener {
    public static Bitmap sPhotoCarry;
    private Bitmap mBitmap;
    private boolean mChromeVisible;
    private PhotoViewDragToCloseFrame mCloseFrame;
    private View mDecorView;
    private PhotoView mImageView;
    private ScrimInsetsFrameLayout mScrimLayout;
    private ColorDrawable mWindowBg = new ColorDrawable(ViewCompat.MEASURED_STATE_MASK);

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        getWindow().setBackgroundDrawable(this.mWindowBg);
        if (Build.VERSION.SDK_INT >= 19) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams attributes = getWindow().getAttributes();
            attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(attributes);
        }
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setSharedElementEnterTransition(new ChangeBounds().setDuration(250L).setInterpolator(new FastOutSlowInInterpolator()));
            getWindow().setSharedElementsUseOverlay(false);
        }
        setToolbarHasElevation(false);
        setContentView(R.layout.activity_photo);
        setUseScreenSettings(true);
        setDisplayUpButton(true);
        View decorView = getWindow().getDecorView();
        this.mDecorView = decorView;
        decorView.setOnSystemUiVisibilityChangeListener(this);
        Bitmap bitmap = (Bitmap) getLastCustomNonConfigurationInstance();
        this.mBitmap = bitmap;
        if (bitmap == null) {
            this.mBitmap = sPhotoCarry;
            sPhotoCarry = null;
        }
        this.mScrimLayout = (ScrimInsetsFrameLayout) findViewById(R.id.scrim_layout);
        this.mScrimLayout.setOnInsetsCallback(rect -> findViewById(R.id.ui_container).setPadding(rect.left, rect.top, rect.right, rect.bottom));
        this.mCloseFrame = (PhotoViewDragToCloseFrame) findViewById(R.id.close_frame);
        this.mCloseFrame.setOnCloseListener(this);
        this.mImageView = (PhotoView) findViewById(R.id.showcasedImage);
        this.mImageView.setOnViewTapListener(this);
        this.mImageView.setImageDrawable(new OpaqueBitmapDrawable(getResources(), this.mBitmap));
        ViewCompat.setTransitionName(this.mImageView, "showcased_image");
        this.mChromeVisible = true;
    }

    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        return this.mBitmap;
    }

    @Override
    protected void onSaveInstanceState(Bundle bundle) {
        super.onSaveInstanceState(bundle);
        bundle.putBoolean("chrome_visible", this.mChromeVisible);
    }

    @Override
    protected void onRestoreInstanceState(Bundle bundle) {
        super.onRestoreInstanceState(bundle);
        setChromeVisible(bundle.getBoolean("chrome_visible"), false);
    }

    @Override
    public void finishAfterTransition() {
        super.finishAfterTransition();
        this.mCloseFrame.stop();
    }

    private void setChromeVisible(final boolean z, boolean z2) {
        if (this.mChromeVisible == z) return;
        if (Build.VERSION.SDK_INT >= 19) {
            this.mDecorView.setSystemUiVisibility(!z ? 3846 : 1792);
        }
        if (z2) {
            getToolbar().animate().alpha(z ? 1.0f : 0.0f).setDuration(100L).setInterpolator(new DecelerateInterpolator()).setListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationStart(Animator animator) { if (z) setToolbarVisible(true); }
                @Override public void onAnimationEnd(Animator animator) { setToolbarVisible(z); }
            });
        } else {
            setToolbarVisible(z);
        }
        this.mChromeVisible = z;
    }

    @Override
    public void onSystemUiVisibilityChange(int i) {
        if ((i & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
            setChromeVisible(true, true);
        } else {
            setChromeVisible(false, true);
        }
    }

    @Override
    public void onViewTap(View view, float f, float f2) {
        setChromeVisible(!this.mChromeVisible, true);
    }

    @Override
    public void onDragCloseRatio(float f) {
        if (Build.VERSION.SDK_INT >= 21) {
            this.mWindowBg.setAlpha((int) (((-255.0f) * f) + 255.0f));
        }
        getToolbar().setAlpha(1.0f - f);
    }

    @Override
    public void onDragClose() {
        supportFinishAfterTransition();
    }

    private static class OpaqueBitmapDrawable extends BitmapDrawable {
        private Rect mBounds;
        private boolean mDrawBackground;

        @Override
        public int getOpacity() { return PixelFormat.UNKNOWN; }

        public OpaqueBitmapDrawable(Resources resources, Bitmap bitmap) {
            super(resources, bitmap);
            if (bitmap != null) {
                this.mDrawBackground = bitmap.hasAlpha();
            }
        }

        @Override
        protected void onBoundsChange(Rect rect) {
            super.onBoundsChange(rect);
            this.mBounds = rect;
        }

        @Override
        public void draw(Canvas canvas) {
            if (this.mDrawBackground && this.mBounds != null) {
                canvas.save();
                canvas.clipRect(this.mBounds);
                canvas.drawColor(-1);
                canvas.restore();
            }
            super.draw(canvas);
        }
    }
}