package com.hieuld.helium.annotations;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.ViewCompat;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hieuld.helium.R;
import com.hieuld.helium.model.ProManager;
import com.hieuld.helium.util.Utils;
import com.hieuld.helium.widget.ConstrainedContainerView;

import java.util.ArrayList;

public class SelectionToolbarView extends CardView implements View.OnClickListener, SelectionToolbarOverflowAdapter.Listener {
    private static final String TAG = "SelectionToolbarView";
    private int mAnchorY;
    private int mAnnotationMargin;
    private View.OnClickListener mColorClickListener;
    private ViewGroup mColorsView;
    private View mContainerView;
    private Context mContext;
    private Interpolator mFastOutSlowInInterpolator;
    private ValueAnimator mHeightAnimator;
    private boolean mIgnoreTouches;
    private boolean mInNotesDialog;
    private ToolbarListener mListener;
    private int mMarginAboveSelection;
    private int mMarginBelowSelection;
    private View mNormalContainerView;
    private View mNotesContainerView;
    private TextView mNotesView;
    private SelectionToolbarOverflowAdapter mOverflowAdapter;
    private ImageButton mOverflowButton;
    private ConstrainedContainerView mOverflowContainerView;
    private int mScreenMargin;
    private boolean mSearchInOverflow;
    private boolean mShownUpwards;
    private State mState;
    private ImageButton mUnderlineButton;
    private boolean mVisible;
    private Button mWebSearchButton;
    private ValueAnimator mWidthAnimator;

    public static class State {
        public static final int TYPE_ANNOTATION = 1;
        public static final int TYPE_TEXT_SELECTION = 0;
        int annotationType;
        int color;
        String notes;
        int stateType;
        String text;
    }

    public interface ToolbarListener {
        void onAnnotationDelete();
        void onColorPicked(int i);
        void onDismiss();
        void onNotesUpdated(String str);
        void onTypeSet(int i);
    }

    public SelectionToolbarView(Context context) {
        super(context);
        this.mColorClickListener = view -> onColorClick(((Integer) view.findViewById(R.id.image).getTag()));
        this.mContext = context;
        LayoutInflater.from(context).inflate(R.layout.selection_toolbar, this);
        Resources resources = getResources();
        this.mScreenMargin = resources.getDimensionPixelOffset(R.dimen.selection_toolbar_screen_margin);
        this.mMarginAboveSelection = resources.getDimensionPixelOffset(R.dimen.selection_toolbar_selection_margin_above);
        this.mMarginBelowSelection = resources.getDimensionPixelOffset(R.dimen.selection_toolbar_selection_margin_below);
        this.mAnnotationMargin = resources.getDimensionPixelOffset(R.dimen.selection_toolbar_annotation_margin);
        setRadius(Utils.dpToPx(context, 4));
        findViewById(R.id.copy).setOnClickListener(this);
        findViewById(R.id.share).setOnClickListener(this);
        Button button = findViewById(R.id.web_search);
        this.mWebSearchButton = button;
        button.setOnClickListener(this);
        ImageButton imageButton = findViewById(R.id.overflow);
        this.mOverflowButton = imageButton;
        imageButton.setOnClickListener(this);
        RecyclerView recyclerView = findViewById(R.id.overflow_actions);
        SelectionToolbarOverflowAdapter selectionToolbarOverflowAdapter = new SelectionToolbarOverflowAdapter(this.mContext, this);
        this.mOverflowAdapter = selectionToolbarOverflowAdapter;
        recyclerView.setAdapter(selectionToolbarOverflowAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this.mContext, RecyclerView.VERTICAL, false));
        this.mContainerView = findViewById(R.id.container);
        this.mNormalContainerView = findViewById(R.id.normal_container);
        this.mOverflowContainerView = findViewById(R.id.overflow_container);
        this.mColorsView = findViewById(R.id.colors);
        buildColorsView();
        ImageButton imageButton2 = findViewById(R.id.underline);
        this.mUnderlineButton = imageButton2;
        imageButton2.setImageDrawable(DrawableCompat.wrap(imageButton2.getDrawable()));
        this.mUnderlineButton.setOnClickListener(this);
        this.mNotesContainerView = findViewById(R.id.notes_container);
        TextView textView = findViewById(R.id.notes);
        this.mNotesView = textView;
        textView.setOnClickListener(view -> startNotesDialog());
        this.mFastOutSlowInInterpolator = new FastOutSlowInInterpolator();
    }

    public void setToolbarListener(ToolbarListener toolbarListener) {
        this.mListener = toolbarListener;
    }

    private void buildColorsView() {
        LayoutInflater layoutInflaterFrom = LayoutInflater.from(this.mContext);
        addColorView(layoutInflaterFrom, 0);
        for (int i : ProManager.isUnlocked(this.mContext) ? AnnotationColors.PRO_COLORS : AnnotationColors.FREE_COLORS) {
            addColorView(layoutInflaterFrom, i);
        }
    }

    private void addColorView(LayoutInflater layoutInflater, int i) {
        View viewInflate = layoutInflater.inflate(R.layout.selection_toolbar_color_button, this.mColorsView, false);
        ImageView imageView = viewInflate.findViewById(R.id.image);
        if (i == 0) {
            imageView.setImageResource(R.drawable.annotation_color_none);
        } else {
            imageView.setImageDrawable(getCircularColorDrawable(i));
        }
        imageView.setTag(i);
        viewInflate.setOnClickListener(this.mColorClickListener);
        this.mColorsView.addView(viewInflate);
    }

    private void updateColorsView() {
        Drawable drawable = ContextCompat.getDrawable(this.mContext, R.drawable.annotation_color_none);
        Drawable drawableWrap = DrawableCompat.wrap(ContextCompat.getDrawable(this.mContext, R.drawable.ic_annotation_remove));
        DrawableCompat.setTint(drawableWrap, ContextCompat.getColor(this.mContext, R.color.annotation_remove_color));
        for (int i = 0; i < this.mColorsView.getChildCount(); i++) {
            ImageView imageView = this.mColorsView.getChildAt(i).findViewById(R.id.image);
            int iIntValue = (Integer) imageView.getTag();
            if (iIntValue == 0) {
                imageView.setImageDrawable(this.mState.stateType == 0 ? drawable : drawableWrap);
            }
            imageView.setActivated(iIntValue == this.mState.color);
        }
    }

    private void updateUnderlineView() {
        this.mUnderlineButton.setActivated(this.mState.annotationType == 1);
    }

    private Drawable getCircularColorDrawable(int i) {
        int i2 = i | ViewCompat.MEASURED_STATE_MASK;
        GradientDrawable gradientDrawable = (GradientDrawable) ContextCompat.getDrawable(this.mContext, R.drawable.annotation_color).mutate();
        gradientDrawable.setColor(i2);
        gradientDrawable.setStroke(Utils.dpToPx(this.mContext, 1), ColorUtils.compositeColors(ContextCompat.getColor(this.mContext, R.color.color_circle_stroke_overlay), i2));
        return gradientDrawable;
    }

    public void show(State state, Rect rect) {
        Rect rect2 = new Rect(0, 0, ((View) getParent()).getWidth(), ((View) getParent()).getHeight());
        Rect rect3 = new Rect();
        if (rect3.setIntersect(rect, rect2)) {
            if (state != null) {
                this.mState = state;
                updateColorsView();
                updateNotesView();
                updateUnderlineView();
            }
            this.mNotesContainerView.setVisibility(this.mState.color != 0 ? View.VISIBLE : View.GONE);
            resetAnimations();
            calculateAndApplyPosition(rect3);
            moveActionsToOverflowMaybe();
            setupOverflowMenu();
            setVisibility(View.VISIBLE);
            setAlpha(0.0f);
            animate().cancel();
            animate().alpha(1.0f).setDuration(150L);
            this.mVisible = true;
        }
    }

    private void calculateAndApplyPosition(Rect rect) {
        int parentWidth = ((View) getParent()).getWidth();
        int parentHeight = ((View) getParent()).getHeight();
        int centerX = rect.centerX();

        measure(View.MeasureSpec.makeMeasureSpec(parentWidth - (this.mScreenMargin * 2), View.MeasureSpec.AT_MOST), 0);
        int halfWidth = getMeasuredWidth() / 2;

        if (centerX - halfWidth < this.mScreenMargin) {
            centerX = halfWidth + this.mScreenMargin;
        } else if (centerX + halfWidth > parentWidth - this.mScreenMargin) {
            centerX = (parentWidth - this.mScreenMargin) - halfWidth;
        }

        int alignMode;
        int anchorY;

        if (rect.height() <= parentHeight / 2) {
            anchorY = rect.centerY();
            alignMode = RelativeLayout.ALIGN_PARENT_TOP;
        } else if (parentHeight - rect.bottom <= rect.top) {
            anchorY = rect.bottom + (this.mState.stateType == State.TYPE_ANNOTATION ? this.mAnnotationMargin : this.mMarginBelowSelection);
            alignMode = RelativeLayout.ALIGN_PARENT_BOTTOM;
        } else {
            anchorY = rect.top - (this.mState.stateType == State.TYPE_ANNOTATION ? this.mAnnotationMargin : this.mMarginAboveSelection);
            alignMode = RelativeLayout.ALIGN_PARENT_TOP;
        }

        this.mAnchorY = anchorY;

        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) getLayoutParams();
        params.leftMargin = centerX - halfWidth - this.mScreenMargin;

        if (alignMode == RelativeLayout.ALIGN_PARENT_TOP) {
            params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0);
            params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            params.topMargin = anchorY - this.mScreenMargin;
            params.bottomMargin = 0;
            this.mShownUpwards = false;
        } else if (alignMode == RelativeLayout.ALIGN_PARENT_BOTTOM) {
            params.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0);
            params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            params.topMargin = 0;
            params.bottomMargin = parentHeight - anchorY - this.mScreenMargin;
            this.mShownUpwards = true;
        }

        setLayoutParams(params);
    }

    public void hide() {
        if (this.mVisible) {
            animate().cancel();
            animate().alpha(0.0f).setDuration(100L).withEndAction(() -> {
                setVisibility(View.INVISIBLE);
                this.mNotesContainerView.setVisibility(View.GONE);
            });
            this.mVisible = false;
        }
    }

    private void startNotesDialog() {
        View viewInflate = LayoutInflater.from(getContext()).inflate(R.layout.dialog_notes_edit, null);
        final EditText editText = viewInflate.findViewById(R.id.notes);
        editText.setText(this.mState.notes);

        AlertDialog alertDialogCreate = new AlertDialog.Builder(getContext())
                .setView(viewInflate)
                .setNegativeButton(R.string.selection_notes_dialog_cancel, null)
                .setPositiveButton(R.string.selection_notes_dialog_save, (dialogInterface, i) -> updateNotes(editText.getText().toString()))
                .setOnCancelListener(dialogInterface -> updateNotes(editText.getText().toString()))
                .setOnDismissListener(dialogInterface -> this.mInNotesDialog = false)
                .create();

        if (this.mState.notes == null) {
            viewInflate.findViewById(R.id.focus_catcher).setVisibility(View.GONE);
            editText.requestFocus();
            alertDialogCreate.getWindow().setSoftInputMode(4);
        }
        alertDialogCreate.show();
        this.mInNotesDialog = true;
    }

    private void updateNotes(String str) {
        if (str.trim().isEmpty()) {
            str = null;
        }
        this.mState.notes = str;
        updateNotesView();
        if (this.mListener != null) {
            this.mListener.onNotesUpdated(str);
        }
    }

    private void updateNotesView() {
        int color;
        this.mNotesView.setText(this.mState.notes != null ? this.mState.notes : getResources().getString(R.string.selection_notes_none));
        if (this.mState.notes != null) {
            TypedArray typedArrayObtainStyledAttributes = this.mContext.getTheme().obtainStyledAttributes(new int[]{android.R.attr.textColorPrimary});
            color = typedArrayObtainStyledAttributes.getColor(0, -1);
            typedArrayObtainStyledAttributes.recycle();
        } else {
            color = ContextCompat.getColor(this.mContext, R.color.selection_notes_none_color);
        }
        this.mNotesView.setTextColor(color);
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.copy) {
            ((ClipboardManager) this.mContext.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("text", this.mState.text));
        } else if (id == R.id.overflow) {
            openOverflowMenu();
            return;
        } else if (id == R.id.share) {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.putExtra(Intent.EXTRA_TEXT, this.mState.text);
            intent.setType("text/plain");
            try {
                this.mContext.startActivity(Intent.createChooser(intent, this.mContext.getString(R.string.selection_share_dialog)));
            } catch (ActivityNotFoundException unused) {
            }
        } else if (id == R.id.underline) {
            if (this.mState.annotationType != 1) {
                this.mState.annotationType = 1;
            } else {
                this.mState.annotationType = 0;
            }
            if (this.mListener != null) {
                this.mListener.onTypeSet(this.mState.annotationType);
            }
            updateUnderlineView();
            if (this.mState.stateType == 0) {
                SharedPreferences defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.mContext);
                if (defaultSharedPreferences.getBoolean("underline_notice_shown", false)) {
                    return;
                }
                defaultSharedPreferences.edit().putBoolean("underline_notice_shown", true).apply();
                Toast.makeText(this.mContext, R.string.underline_notice, Toast.LENGTH_SHORT).show();
                return;
            }
            return;
        } else if (id == R.id.web_search) {
            Intent intent2 = new Intent(Intent.ACTION_WEB_SEARCH);
            intent2.putExtra("query", this.mState.text);
            try {
                this.mContext.startActivity(intent2);
            } catch (ActivityNotFoundException unused2) {
                Toast.makeText(this.mContext, R.string.selection_web_search_failed, Toast.LENGTH_SHORT).show();
            }
        }
        if (this.mListener != null) {
            this.mListener.onDismiss();
        }
    }

    private void onColorClick(int i) {
        if (i == 0 && this.mState.stateType == 1) {
            if (this.mListener != null) {
                this.mListener.onAnnotationDelete();
                return;
            }
            return;
        }
        if (this.mState.color == 0 && i != 0) {
            animateShowView(this.mNotesContainerView);
        } else if (this.mState.color != 0 && i == 0) {
            animateHideView(this.mNotesContainerView);
        }
        this.mState.color = i;
        updateColorsView();
        if (this.mListener != null) {
            this.mListener.onColorPicked(i);
        }
    }

    private void animateShowView(View view) {
        boolean z = view.getVisibility() == View.VISIBLE;
        float alpha = view.getAlpha();
        if (this.mHeightAnimator != null && this.mHeightAnimator.isRunning()) {
            this.mHeightAnimator.cancel();
        }
        view.measure(View.MeasureSpec.makeMeasureSpec(getWidth(), View.MeasureSpec.AT_MOST), 0);
        int height = this.mContainerView.getHeight();
        int height2 = this.mNormalContainerView.getHeight() + (z ? 0 : view.getMeasuredHeight());

        final ViewGroup.LayoutParams normalParams = this.mNormalContainerView.getLayoutParams();
        normalParams.height = height2;
        this.mNormalContainerView.setLayoutParams(normalParams);

        final ViewGroup.LayoutParams containerParams = this.mContainerView.getLayoutParams();
        this.mHeightAnimator = ValueAnimator.ofInt(height, height2);
        this.mHeightAnimator.addUpdateListener(valueAnimator -> {
            containerParams.height = (Integer) valueAnimator.getAnimatedValue();
            this.mContainerView.setLayoutParams(containerParams);
        });

        this.mHeightAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                containerParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                SelectionToolbarView.this.mContainerView.setLayoutParams(containerParams);
                normalParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                SelectionToolbarView.this.mNormalContainerView.setLayoutParams(normalParams);
            }
        });

        this.mHeightAnimator.setDuration((((float) (height2 - height)) / getResources().getDisplayMetrics().density) > 75.0f ? 325 : ItemTouchHelper.Callback.DEFAULT_SWIPE_ANIMATION_DURATION);
        this.mHeightAnimator.setInterpolator(this.mFastOutSlowInInterpolator);
        this.mHeightAnimator.start();
        view.setVisibility(View.VISIBLE);
        if (!z) {
            alpha = 0.0f;
        }
        view.setAlpha(alpha);
        view.animate().alpha(1.0f).setDuration(150L);
    }

    private void animateHideView(final View view) {
        if (this.mHeightAnimator != null && this.mHeightAnimator.isRunning()) {
            this.mHeightAnimator.cancel();
        }
        int height = this.mContainerView.getHeight();
        int height2 = this.mNormalContainerView.getHeight() - view.getHeight();

        final ViewGroup.LayoutParams normalParams = this.mNormalContainerView.getLayoutParams();
        normalParams.height = this.mNormalContainerView.getHeight();
        this.mNormalContainerView.setLayoutParams(normalParams);

        final ViewGroup.LayoutParams containerParams = this.mContainerView.getLayoutParams();
        this.mHeightAnimator = ValueAnimator.ofInt(height, height2);
        this.mHeightAnimator.addUpdateListener(valueAnimator -> {
            containerParams.height = (Integer) valueAnimator.getAnimatedValue();
            this.mContainerView.setLayoutParams(containerParams);
        });

        this.mHeightAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                containerParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                SelectionToolbarView.this.mContainerView.setLayoutParams(containerParams);
                normalParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                SelectionToolbarView.this.mNormalContainerView.setLayoutParams(normalParams);
                view.setVisibility(View.GONE);
                view.setAlpha(1.0f);
            }
        });

        this.mHeightAnimator.setDuration((((float) (height - height2)) / getResources().getDisplayMetrics().density) > 75.0f ? 275 : 200);
        this.mHeightAnimator.setInterpolator(this.mFastOutSlowInInterpolator);
        this.mHeightAnimator.start();
        view.animate().alpha(0.0f).setDuration(150L);
    }

    private void transitionToView(final View view, final View view2, final int i, final int i2) {
        int duration;
        final ViewGroup.LayoutParams layoutParams = this.mContainerView.getLayoutParams();
        final int width = this.mContainerView.getWidth();
        final int height = this.mContainerView.getHeight();
        final float startX = getX() + width;
        int diffX = Math.abs(i - width);
        int diffY = Math.abs(i2 - height);
        double distance = Math.sqrt((diffX * diffX) + (diffY * diffY));
        double density = getResources().getDisplayMetrics().density;
        double speedDp = distance / density;

        if (speedDp < 150.0d) {
            duration = 200;
        } else {
            duration = speedDp > 300.0d ? 300 : ItemTouchHelper.Callback.DEFAULT_SWIPE_ANIMATION_DURATION;
        }

        this.mWidthAnimator = ValueAnimator.ofInt(width, i);
        this.mWidthAnimator.addUpdateListener(valueAnimator -> {
            layoutParams.width = (Integer) valueAnimator.getAnimatedValue();
            this.mContainerView.setLayoutParams(layoutParams);
            setX(startX - layoutParams.width);
            view.setTranslationX(layoutParams.width - i);
            view2.setTranslationX(layoutParams.width - width);
        });
        this.mWidthAnimator.setDuration(duration);
        this.mWidthAnimator.start();

        this.mHeightAnimator = ValueAnimator.ofInt(height, i2);
        this.mHeightAnimator.addUpdateListener(valueAnimator -> {
            layoutParams.height = (Integer) valueAnimator.getAnimatedValue();
            this.mContainerView.setLayoutParams(layoutParams);
            if (this.mShownUpwards) {
                view.setTranslationY(layoutParams.height - i2);
                view2.setTranslationY(layoutParams.height - height);
            }
        });
        this.mHeightAnimator.setDuration(duration);
        this.mHeightAnimator.start();

        view2.setTranslationX(0.0f);
        view2.setTranslationY(0.0f);
        view.setTranslationX(0.0f);
        view.setTranslationY(0.0f);
        view.setAlpha(0.0f);
        view.animate().alpha(1.0f).setDuration(150L).setListener(null);
        view.setVisibility(View.VISIBLE);

        final ViewGroup.LayoutParams layoutParams2 = view2.getLayoutParams();
        layoutParams2.width = width;
        layoutParams2.height = height;
        view2.setLayoutParams(layoutParams2);

        final ViewGroup.LayoutParams layoutParams3 = view.getLayoutParams();
        layoutParams3.width = i;
        layoutParams3.height = i2;
        view.setLayoutParams(layoutParams3);

        this.mIgnoreTouches = true;
        view2.animate().alpha(0.0f).setDuration(150L);
        this.mHeightAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                view2.setVisibility(View.GONE);
                layoutParams2.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                layoutParams2.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                view2.setLayoutParams(layoutParams2);
                layoutParams3.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                layoutParams3.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                view.setLayoutParams(layoutParams3);
                layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                SelectionToolbarView.this.mContainerView.setLayoutParams(layoutParams);
                SelectionToolbarView.this.mIgnoreTouches = false;
            }
        });
    }

    private void resetAnimations() {
        if (this.mWidthAnimator != null) {
            this.mWidthAnimator.cancel();
            this.mHeightAnimator.cancel();
        }
        this.mNormalContainerView.animate().cancel();
        this.mOverflowContainerView.animate().cancel();
        this.mNormalContainerView.setVisibility(View.VISIBLE);
        this.mNormalContainerView.setAlpha(1.0f);
        this.mNormalContainerView.setTranslationX(0.0f);
        this.mNormalContainerView.setTranslationY(0.0f);
        this.mOverflowContainerView.setVisibility(View.GONE);
        setTranslationX(0.0f);

        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) this.mNormalContainerView.getLayoutParams();
        layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        this.mNormalContainerView.setLayoutParams(layoutParams);

        FrameLayout.LayoutParams layoutParams2 = (FrameLayout.LayoutParams) this.mOverflowContainerView.getLayoutParams();
        layoutParams2.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        layoutParams2.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        this.mOverflowContainerView.setLayoutParams(layoutParams2);

        FrameLayout.LayoutParams layoutParams3 = (FrameLayout.LayoutParams) this.mContainerView.getLayoutParams();
        layoutParams3.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        layoutParams3.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        this.mContainerView.setLayoutParams(layoutParams3);
    }

    private void moveActionsToOverflowMaybe() {
        View viewCopy = findViewById(R.id.copy);
        View viewShare = findViewById(R.id.share);
        View viewWebSearch = findViewById(R.id.web_search);

        if (measureViewWidth(viewCopy) + measureViewWidth(viewShare) + measureViewWidth(viewWebSearch) + this.mOverflowButton.getWidth() > ((View) viewCopy.getParent()).getWidth()) {
            viewWebSearch.setVisibility(View.GONE);
            this.mSearchInOverflow = true;
        } else {
            viewWebSearch.setVisibility(View.VISIBLE);
            this.mSearchInOverflow = false;
        }
    }

    private int measureViewWidth(View view) {
        view.measure(0, 0);
        return view.getMeasuredWidth();
    }

    private void setupOverflowMenu() {
        int height;
        ArrayList<SelectionToolbarOverflowAdapter.Action> arrayList = new ArrayList<>();

        if (this.mSearchInOverflow) {
            SelectionToolbarOverflowAdapter.Action action = new SelectionToolbarOverflowAdapter.Action();
            action.name = this.mContext.getString(R.string.selection_web_search);
            action.packageName = null;
            action.className = "search";
            arrayList.add(action);
        }

        if (Build.VERSION.SDK_INT >= 23) {
            PackageManager packageManager = this.mContext.getPackageManager();
            for (ResolveInfo resolveInfo : packageManager.queryIntentActivities(new Intent().setAction(Intent.ACTION_PROCESS_TEXT).setType("text/plain"), 0)) {
                SelectionToolbarOverflowAdapter.Action action2 = new SelectionToolbarOverflowAdapter.Action();
                action2.name = resolveInfo.loadLabel(packageManager).toString();
                action2.packageName = resolveInfo.activityInfo.packageName;
                action2.className = resolveInfo.activityInfo.name;
                arrayList.add(action2);
            }
        }

        if (this.mShownUpwards) {
            height = this.mAnchorY;
        } else {
            height = ((ViewGroup) getParent()).getHeight() - this.mAnchorY;
        }

        this.mOverflowContainerView.setMaxHeight(height - Utils.dpToPx(this.mContext, 20));
        this.mOverflowButton.setVisibility(arrayList.isEmpty() ? View.GONE : View.VISIBLE);
        this.mOverflowAdapter.setActions(arrayList);
        this.mOverflowAdapter.setTopToBottom(!this.mShownUpwards);
        this.mOverflowAdapter.notifyDataSetChanged();
    }

    @Override
    public boolean onTouchEvent(MotionEvent motionEvent) {
        return this.mIgnoreTouches || super.onTouchEvent(motionEvent);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent motionEvent) {
        return this.mIgnoreTouches || super.onInterceptTouchEvent(motionEvent);
    }

    private void openOverflowMenu() {
        this.mOverflowContainerView.measure(0, 0);
        transitionToView(this.mOverflowContainerView, this.mNormalContainerView, this.mOverflowContainerView.getMeasuredWidth(), this.mOverflowContainerView.getMeasuredHeight());
    }

    @Override
    public void onOverflowBack() {
        this.mNormalContainerView.measure(View.MeasureSpec.makeMeasureSpec(this.mNormalContainerView.getWidth(), View.MeasureSpec.UNSPECIFIED), 0);
        transitionToView(this.mNormalContainerView, this.mOverflowContainerView, this.mNormalContainerView.getWidth(), this.mNormalContainerView.getMeasuredHeight());
    }

    @Override
    public void onActionClick(SelectionToolbarOverflowAdapter.Action action) {
        if (action.packageName == null) {
            if ("search".equals(action.className)) {
                onClick(this.mWebSearchButton);
            }
        } else {
            this.mContext.startActivity(new Intent()
                    .setAction(Intent.ACTION_PROCESS_TEXT)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
                    .putExtra(Intent.EXTRA_PROCESS_TEXT, this.mState.text)
                    .setClassName(action.packageName, action.className));

            postDelayed(() -> {
                if (this.mListener != null) {
                    this.mListener.onDismiss();
                }
            }, 300L);
        }
    }

    public void onProStateChanged() {
        this.mColorsView.removeAllViews();
        buildColorsView();
    }

    public boolean isInNotesDialog() {
        return this.mInNotesDialog;
    }
}