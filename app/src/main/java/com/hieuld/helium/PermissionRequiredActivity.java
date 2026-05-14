package com.hieuld.helium;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;

import androidx.annotation.NonNull;

import com.hieuld.helium.util.ThemeUtils;

public class PermissionRequiredActivity extends BaseActivity implements View.OnClickListener {
    private static final int PERMISSION_REQUEST_CODE = 1;
    private Button mContinueButton;
    private boolean mDeniedPermanently;
    private Button mExitButton;
    private View mReasonView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        setRequiresPermissions(false);
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(ThemeUtils.resolveColor(this, R.attr.surfaceStatusBarColor, R.color.helium_surface_status_bar_light));
        if (!ThemeUtils.isInDarkMode(this)) {
            View decorView = getWindow().getDecorView();
            decorView.setSystemUiVisibility(decorView.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        setContentView(R.layout.permission_required);
        this.mReasonView = findViewById(R.id.reason);
        this.mReasonView.setVisibility(View.GONE);

        this.mExitButton = findViewById(R.id.exit);
        this.mExitButton.setOnClickListener(this);

        this.mContinueButton = findViewById(R.id.next);
        this.mContinueButton.setOnClickListener(this);

        if (shouldShowRequestPermissionRationale(android.Manifest.permission.READ_EXTERNAL_STORAGE)) {
            this.mReasonView.setVisibility(View.VISIBLE);
            return;
        }

        if (savedInstanceState == null) {
            if (Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageLegacy()) {
                askForPermission();
            } else {
                this.mReasonView.setVisibility(View.VISIBLE);
            }
        } else if (savedInstanceState.getBoolean("reason_shown")) {
            this.mReasonView.setVisibility(View.VISIBLE);
        }
    }

    private void askForPermission() {
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageLegacy()) {
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        .setData(Uri.parse("package:" + getPackageName())));
            } catch (ActivityNotFoundException e) {
                startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        } else {
            requestPermissions(new String[]{
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != PERMISSION_REQUEST_CODE) return;

        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
            continueToApp();
            return;
        }

        if (this.mReasonView.getVisibility() == View.GONE) {
            showReasonView();
        }

        if (!shouldShowRequestPermissionRationale(android.Manifest.permission.READ_EXTERNAL_STORAGE)) {
            // Khôi phục cờ 65536
            if (getPackageManager().resolveActivity(getAppSettingsIntent(), PackageManager.MATCH_DEFAULT_ONLY) != null) {
                this.mDeniedPermanently = true;
                this.mContinueButton.setText(R.string.permissions_app_settings);
            }
        }
    }

    private void continueToApp() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void showReasonView() {
        this.mReasonView.setVisibility(View.VISIBLE);
        this.mReasonView.setAlpha(0.0f);
        this.mReasonView.animate().alpha(1.0f).setDuration(500L).setInterpolator(new DecelerateInterpolator()).start();
    }

    @Override
    public void onClick(View view) {
        if (view == this.mExitButton) {
            finish();
        } else if (view == this.mContinueButton) {
            if (this.mDeniedPermanently) {
                startActivity(getAppSettingsIntent());
            } else {
                askForPermission();
            }
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("reason_shown", this.mReasonView.getVisibility() == View.VISIBLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (checkCriticalPermissions()) {
            continueToApp();
        }
    }

    private Intent getAppSettingsIntent() {
        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        return intent;
    }
}
