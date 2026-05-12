package com.hieuld.helium;

import android.os.Bundle;
import android.webkit.WebView;
import androidx.core.content.ContextCompat;
import com.hieuld.helium.util.ThemeUtils;

public class SettingsHtmlActivity extends BaseActivity {
    public static final String EXTRA_FILE = "file";
    public static final String EXTRA_TITLE_ID = "title_id";

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_settings_licenses);
        setTitle(getIntent().getIntExtra(EXTRA_TITLE_ID, -1));
        setDisplayUpButton(true);
        WebView webView = (WebView) findViewById(R.id.web_view);
        webView.setBackgroundColor(ContextCompat.getColor(this, R.color.color_surface));
        webView.getSettings().setJavaScriptEnabled(true);
        String str = "file:///android_asset/" + getIntent().getStringExtra(EXTRA_FILE) + ".html";
        if (ThemeUtils.isInDarkMode(this)) {
            str = str + "?dark";
        }
        webView.loadUrl(str);
    }
}
