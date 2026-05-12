package com.hieuld.helium.prefs;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.Preference;

import com.hieuld.helium.R;

public class InfoPreference extends Preference {

    public InfoPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public InfoPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public InfoPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public InfoPreference(Context context) {
        super(context);
        init();
    }

    private void init() {
        setLayoutResource(R.layout.preference_info);
        setSelectable(false);
    }
}
