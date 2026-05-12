package com.hieuld.helium.library;

import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceDialogFragmentCompat;

import com.hieuld.helium.R;

import java.io.File;

public class LibraryFolderPreferenceDialog extends PreferenceDialogFragmentCompat implements CompoundButton.OnCheckedChangeListener {
    private static final String SAVED_PATH_KEY = "saved_path";
    private String mPath;
    private EditText mPathView;
    private RadioGroup mSelectionGroup;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            this.mPath = savedInstanceState.getString(SAVED_PATH_KEY);
        } else {
            this.mPath = getLibraryFolderPreference().getPath();
        }
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        this.mSelectionGroup = view.findViewById(R.id.selection);
        this.mSelectionGroup.check(this.mPath != null ? R.id.custom : R.id.none);

        ((RadioButton) view.findViewById(R.id.none)).setOnCheckedChangeListener(this);
        ((RadioButton) view.findViewById(R.id.custom)).setOnCheckedChangeListener(this);

        this.mPathView = view.findViewById(R.id.path);
        String samplePath = this.mPath;
        if (samplePath == null) {
            samplePath = getSamplePath();
        }
        this.mPathView.setText(samplePath);
        this.mPathView.setEnabled(this.mPath != null);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(SAVED_PATH_KEY, this.mPath);
    }

    private String getSamplePath() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            // Cảnh báo: Từ Android 11 (API 30), hàm này đã bị khai tử. Giữ tạm theo file cũ.
            return new File(Environment.getExternalStorageDirectory(), "Books").getAbsolutePath();
        }
        return null;
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            int checkedId = this.mSelectionGroup.getCheckedRadioButtonId();
            String path = this.mPathView.getText().toString();

            if (checkedId == R.id.none || TextUtils.isEmpty(path)) {
                path = null;
            }

            LibraryFolderPreference pref = getLibraryFolderPreference();
            if (pref.callChangeListener(path)) {
                pref.setPath(path);
            }
        }
    }

    private LibraryFolderPreference getLibraryFolderPreference() {
        return (LibraryFolderPreference) getPreference();
    }

    public static LibraryFolderPreferenceDialog newInstance(String key) {
        LibraryFolderPreferenceDialog fragment = new LibraryFolderPreferenceDialog();
        Bundle b = new Bundle();
        b.putString("key", key);
        fragment.setArguments(b);
        return fragment;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (isChecked) {
            if (buttonView.getId() == R.id.none) {
                this.mPath = null;
                this.mPathView.setEnabled(false);
            } else {
                this.mPath = this.mPathView.getText().toString();
                this.mPathView.setEnabled(true);
            }
        }
    }
}