package com.hieuld.helium.widget;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;

import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.hieuld.helium.R;

public class NavigationTintedToolbar extends Toolbar {
    private int mColorControlNormal;
    private boolean mColorFetched;

    public NavigationTintedToolbar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void setNavigationIcon(Drawable icon) {
        if (icon == null) {
            super.setNavigationIcon(null);
            return;
        }

        fetchColor();
        Drawable wrappedDrawable = DrawableCompat.wrap(icon.mutate());
        DrawableCompat.setTint(wrappedDrawable, this.mColorControlNormal);
        super.setNavigationIcon(wrappedDrawable);
    }

    private void fetchColor() {
        if (this.mColorFetched) {
            return;
        }
        Context context = getContext();
        Resources.Theme theme = context.getTheme();
        TypedValue typedValue = new TypedValue();

        if (theme.resolveAttribute(androidx.appcompat.R.attr.colorControlNormal, typedValue, true)) {
            this.mColorControlNormal = ContextCompat.getColor(context, typedValue.resourceId);
        }
        this.mColorFetched = true;
    }
}