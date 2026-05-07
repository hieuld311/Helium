package com.hieuld.helium.themes;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.hieuld.helium.R;
import com.hieuld.helium.util.Utils;
import com.jaredrummler.android.colorpicker.ColorPickerDialog;
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener;

public class ThemeEditColorView extends AppCompatTextView implements View.OnClickListener, ColorPickerDialogListener {

    private int mColor;
    private Context mContext;
    private FragmentManager mFragmentManager;
    private String mFragmentTag;
    private OnColorChangedListener mListener;

    public interface OnColorChangedListener {
        void onColorChanged(View view, int color);
    }

    public ThemeEditColorView(Context context) {
        super(context);
        init(context);
    }

    public ThemeEditColorView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ThemeEditColorView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        this.mContext = context;

        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16.0f);
        setGravity(Gravity.CENTER_VERTICAL);

        setCompoundDrawablesWithIntrinsicBounds(R.drawable.themes_theme_circle, 0, 0, 0);
        setCompoundDrawablePadding(Utils.dpToPx(this.mContext, 16));
        setOnClickListener(this);
    }

    public void setFragmentManager(FragmentManager fragmentManager) {
        this.mFragmentManager = fragmentManager;
    }

    public void setFragmentTag(String tag) {
        this.mFragmentTag = tag;
    }

    public void onFragmentRestored(Fragment fragment) {
        if (fragment instanceof ColorPickerDialog) {
            ((ColorPickerDialog) fragment).setColorPickerDialogListener(this);
        }
    }

    public void setColor(int color) {
        this.mColor = color;
        int opaqueColor = color | 0xFF000000;

        GradientDrawable gradientDrawable = (GradientDrawable) ContextCompat.getDrawable(this.mContext, R.drawable.themes_theme_circle).mutate();
        gradientDrawable.setColor(opaqueColor);

        // draw border
        int strokeWidth = Utils.dpToPx(this.mContext, 1);
        int strokeColor = ColorUtils.compositeColors(ContextCompat.getColor(this.mContext, R.color.color_circle_stroke_overlay), opaqueColor);
        gradientDrawable.setStroke(strokeWidth, strokeColor);

        setCompoundDrawablesWithIntrinsicBounds(gradientDrawable, null, null, null);
    }

    public void setOnColorChangedListener(OnColorChangedListener listener) {
        this.mListener = listener;
    }

    @Override
    public void onClick(View view) {
        ColorPickerDialog dialog = ColorPickerDialog.newBuilder()
                .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
                .setColor(this.mColor | 0xFF000000)
                .setAllowCustom(false)
                .setAllowPresets(false)
                .setDialogTitle(R.string.theme_color_dialog_title)
                .setSelectedButtonText(R.string.theme_color_dialog_ok)
                .create();

        dialog.setColorPickerDialogListener(this);
        dialog.show(this.mFragmentManager, this.mFragmentTag);
    }

    @Override
    public void onColorSelected(int dialogId, int color) {
        int rgbColor = color & 0xFFFFFF;

        setColor(rgbColor);

        if (this.mListener != null) {
            this.mListener.onColorChanged(this, rgbColor);
        }
    }

    @Override
    public void onDialogDismissed(int dialogId) {
    }
}
