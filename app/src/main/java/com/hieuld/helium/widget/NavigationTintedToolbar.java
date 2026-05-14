package com.hieuld.helium.widget;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;

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

    protected void tintMenuIcons() {
        fetchColor();
        Menu menu = getMenu();
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            Drawable icon = item.getIcon();
            if (icon != null) {
                Drawable wrappedDrawable = DrawableCompat.wrap(icon.mutate());
                DrawableCompat.setTint(wrappedDrawable, this.mColorControlNormal);
                item.setIcon(wrappedDrawable);
            }
        }
    }

    private void fetchColor() {
        if (this.mColorFetched) {
            return;
        }
        Context context = getContext();
        Resources.Theme theme = context.getTheme();
        TypedValue typedValue = new TypedValue();

        if (theme.resolveAttribute(androidx.appcompat.R.attr.colorControlNormal, typedValue, true)) {
            if (typedValue.resourceId != 0) {
                this.mColorControlNormal = ContextCompat.getColor(context, typedValue.resourceId);
            } else {
                this.mColorControlNormal = typedValue.data;
            }
        }
        this.mColorFetched = true;
    }
}
