package com.hieuld.helium;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.ViewCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.preference.PreferenceManager;

import com.hieuld.helium.model.ProManager;
import com.hieuld.helium.themes.AppThemeManager;
import com.hieuld.helium.util.Utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BaseActivity extends AppCompatActivity {
    private static final String AOSP_WEBVIEW_PACKAGE_NAME = "com.android.webview";
    private static final String CHROME_PACKAGE_NAME = "com.android.chrome";
    private static final String GOOGLE_WEBVIEW_PACKAGE_NAME = "com.google.android.webview";
    private static final String TAG = "BaseActivity";

    private DrawerLayout mDrawerLayout;
    private boolean mProState;
    private Toolbar mToolbar;
    private boolean mUseScreenSettings;
    private boolean mToolbarHasElevation = true;
    private boolean mToolbarVisible = true;
    private boolean mRequiresPermissions = true;

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (this.mRequiresPermissions && !checkCriticalPermissions()) {
            startActivity(new Intent(this, PermissionRequiredActivity.class));
            finish();
        }
        this.mProState = ProManager.isUnlocked(this);
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setToolbar(toolbar);

        ViewGroup contentRoot = findViewById(android.R.id.content);
        if (contentRoot != null && contentRoot.getChildCount() > 0) {
            View child = contentRoot.getChildAt(0);
            if (child instanceof DrawerLayout) {
                this.mDrawerLayout = (DrawerLayout) child;
            }
        }
    }

    protected void setToolbarHasElevation(boolean elevation) {
        this.mToolbarHasElevation = elevation;
    }

    protected void setToolbarVisible(boolean visible) {
        this.mToolbarVisible = visible;
        if (this.mToolbar != null) {
            this.mToolbar.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        }
    }

    protected void setUseScreenSettings(boolean useSettings) {
        this.mUseScreenSettings = useSettings;
    }

    protected void setRequiresPermissions(boolean requires) {
        this.mRequiresPermissions = requires;
    }

    public void setNoWindowBackground() {
        getWindow().setBackgroundDrawable(null);
    }

    public Toolbar getToolbar() {
        return this.mToolbar;
    }

    public void setToolbar(Toolbar toolbar) {
        this.mToolbar = toolbar;
        if (toolbar != null) {
            if (this.mToolbarHasElevation) {
                ViewCompat.setElevation(toolbar, Utils.dpToPx(this, 4));
            }
            setSupportActionBar(this.mToolbar);
        }
    }

    public void setDisplayUpButton(boolean displayUp) {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(displayUp);
        }
    }

    @Override
    public void onSupportActionModeStarted(ActionMode mode) {
        super.onSupportActionModeStarted(mode);
        Window window = getWindow();
        View actionModeBar = window.findViewById(androidx.appcompat.R.id.action_mode_bar);

        if (actionModeBar != null) {
            int elevation = 0;
            if (!(this.mToolbar != null && this.mToolbarVisible) && this.mToolbarHasElevation) {
                elevation = Utils.dpToPx(this, 4);
            }
            ViewCompat.setElevation(actionModeBar, elevation);
        }

        if (Build.VERSION.SDK_INT >= 21) {
            int color = ContextCompat.getColor(this, R.color.action_mode_color_status_bg);
            if (ViewCompat.isLaidOut(window.getDecorView())) {
                ObjectAnimator.ofArgb(window, "statusBarColor", getStatusBarColor(), color).start();
            } else {
                window.setStatusBarColor(color);
            }
        }
    }

    @Override
    public void onSupportActionModeFinished(ActionMode mode) {
        super.onSupportActionModeFinished(mode);
        if (Build.VERSION.SDK_INT >= 21) {
            resetStatusBarColor();
        }
    }

    private void resetStatusBarColor() {
        ObjectAnimator animator = ObjectAnimator.ofArgb(getWindow(), "statusBarColor", getStatusBarColor());
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (mDrawerLayout != null) {
                    mDrawerLayout.postDelayed(() -> getWindow().setStatusBarColor(Color.TRANSPARENT), 30L);
                }
            }
        });
        animator.start();
    }

    private int getStatusBarColor() {
        if (this.mDrawerLayout != null) {
            Drawable bg = this.mDrawerLayout.getStatusBarBackgroundDrawable();
            if (bg instanceof ColorDrawable) {
                return ((ColorDrawable) bg).getColor();
            }
        }
        return getWindow().getStatusBarColor();
    }

    @Override
    protected void onPause() {
        super.onPause();
        this.mProState = ProManager.isUnlocked(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateScreenSettings();
        ProManager.checkIfNecessary(this);
        if (this.mProState != ProManager.isUnlocked(this)) {
            onProStateChanged();
        }
    }

    protected void onProStateChanged() {
        // Override in subclasses to react to Pro state changes
    }

    protected void updateScreenSettings() {
        if (this.mUseScreenSettings) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            View content = findViewById(android.R.id.content);
            if (content != null) {
                // Sử dụng key từ strings.xml: "keep_screen_on"
                content.setKeepScreenOn(prefs.getBoolean("keep_screen_on", true));
            }

            // Lấy giá trị từ mảng pref_screen_rotation_values: "auto", "portrait", "landscape"
            String rotation = prefs.getString("screen_rotation", "auto");

            // Khởi tạo với giá trị mặc định (Unspecified)
            int orientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;

            if ("portrait".equals(rotation)) {
                orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
            } else if ("landscape".equals(rotation)) {
                orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
            }

            try {
                setRequestedOrientation(orientation);
            } catch (IllegalStateException ignored) {
                // Xử lý lỗi đặc thù trên Android 8.0 cho Activity trong suốt
            }

            setScreenBrightness(
                    prefs.getFloat("brightness", 0.5f),
                    prefs.getBoolean("brightness_auto", true));
        }
    }

    protected void setScreenBrightness(float brightness, boolean auto) {
        Window window = getWindow();
        WindowManager.LayoutParams attrs = window.getAttributes();
        attrs.screenBrightness = auto ? WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE : brightness;
        window.setAttributes(attrs);
    }

    public boolean checkCriticalPermissions() {
        if (Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageLegacy()) {
            return ContextCompat.checkSelfPermission(this, "android.permission.READ_EXTERNAL_STORAGE")
                    == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(this, "android.permission.WRITE_EXTERNAL_STORAGE")
                    == PackageManager.PERMISSION_GRANTED;
        }
        return Environment.isExternalStorageManager();
    }

    public void startFeedback(final Runnable onFinished) {
        final ProgressDialog dialog = new ProgressDialog(this);
        dialog.setMessage(getString(R.string.feedback_wait_dialog_message));
        dialog.show();

        final View decorView = getWindow().getDecorView();
        decorView.setDrawingCacheEnabled(true);
        Bitmap screenshot = Bitmap.createBitmap(decorView.getDrawingCache());

        mExecutor.execute(() -> {
            ArrayList<Uri> uris = generateFeedbackUris(screenshot);

            mHandler.post(() -> {
                dialog.dismiss();
                decorView.setDrawingCacheEnabled(false);

                Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
                intent.setType("plain/text");
                intent.putExtra(Intent.EXTRA_EMAIL, new String[]{"faultexceptionapps@gmail.com"});
                intent.putExtra(Intent.EXTRA_SUBJECT, "Helium Feedback");
                intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                if (getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).size() > 0) {
                    startActivity(intent);
                } else {
                    Toast.makeText(this, R.string.feedback_no_intent_handler, Toast.LENGTH_SHORT).show();
                }
                if (onFinished != null) {
                    onFinished.run();
                }
            });
        });
    }

    private ArrayList<Uri> generateFeedbackUris(Bitmap screenshot) {
        File dir = new File(getFilesDir(), "feedback");
        if (!dir.exists()) {
            dir.mkdirs();
        }

        ArrayList<Uri> uris = new ArrayList<>();
        Uri logcatUri = getLogcatUri();
        Uri infoUri = getInfoUri();
        Uri screenshotUri = getScreenshotUri(screenshot);

        if (logcatUri != null) uris.add(logcatUri);
        if (infoUri != null) uris.add(infoUri);
        if (screenshotUri != null) uris.add(screenshotUri);
        return uris;
    }

    private Uri getLogcatUri() {
        File file = new File(getFilesDir(), "feedback/helium.log.txt");
        try {
            int code = Runtime.getRuntime()
                    .exec(new String[]{"logcat", "-d", "-v", "time", "-f", file.getAbsolutePath()})
                    .waitFor();
            if (code == 0) {
                return FileProvider.getUriForFile(this, App.FILE_PROVIDER, file);
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }

    private Uri getScreenshotUri(Bitmap screenshot) {
        if (screenshot == null) return null;
        File file = new File(getFilesDir(), "feedback/helium.screenshot.png");
        try (FileOutputStream out = new FileOutputStream(file)) {
            if (screenshot.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                return FileProvider.getUriForFile(this, App.FILE_PROVIDER, file);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private PackageInfo getPackageInfoOrNull(String pkgName) {
        try {
            return getPackageManager().getPackageInfo(pkgName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private boolean isPackageEnabled(String pkgName) {
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(pkgName, 0);
            return info != null && info.enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private Uri getInfoUri() {
        File file = new File(getFilesDir(), "feedback/helium.info.txt");
        PackageInfo appInfo = getPackageInfoOrNull(getPackageName());
        PackageInfo proInfo = getPackageInfoOrNull(getPackageName() + ".pro");

        try (BufferedWriter bw = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            bw.write("App package: " + getPackageName()); bw.newLine();
            bw.write("App version name: " + (appInfo != null ? appInfo.versionName : "n/a")); bw.newLine();
            long vCode = appInfo != null
                    ? (Build.VERSION.SDK_INT >= 28 ? appInfo.getLongVersionCode() : appInfo.versionCode)
                    : -1;
            bw.write("App version code: " + vCode); bw.newLine();
            bw.write("Model: " + Build.MODEL); bw.newLine();
            bw.write("Android SDK: " + Build.VERSION.SDK_INT); bw.newLine();
            bw.write("Google WebView enabled: " + isPackageEnabled(GOOGLE_WEBVIEW_PACKAGE_NAME)); bw.newLine();
            bw.write("AOSP WebView enabled: " + isPackageEnabled(AOSP_WEBVIEW_PACKAGE_NAME)); bw.newLine();
            bw.write("Chrome enabled: " + isPackageEnabled(CHROME_PACKAGE_NAME)); bw.newLine();
            bw.write("Pro package installed: " + (proInfo != null)); bw.newLine();
            bw.write("Pro package version: " + (proInfo != null ? proInfo.versionName : "n/a")); bw.newLine();

            return FileProvider.getUriForFile(this, App.FILE_PROVIDER, file);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void showFeedbackDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.feedback_dialog_title)
                .setMessage(R.string.feedback_dialog_text)
                .setNegativeButton(R.string.feedback_dialog_cancel, null)
                .setPositiveButton(R.string.feedback_dialog_ok, (dialog, i) -> startFeedback(null))
                .show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            supportFinishAfterTransition();
            return true;
        }
        if (id == R.id.feedback) {
            showFeedbackDialog();
            return true;
        }
        if (id == R.id.settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mExecutor.shutdownNow();
    }
}
