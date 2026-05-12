package com.hieuld.helium.themes;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hieuld.helium.BaseActivity;
import com.hieuld.helium.R;
import com.hieuld.helium.model.ProManager;
import com.hieuld.helium.util.HtmlCompat;
import com.hieuld.helium.util.Utils;

import java.util.List;

public class ThemesActivity extends BaseActivity
        implements ThemeEditColorView.OnColorChangedListener, View.OnClickListener {

    public static final String EXTRA_THEME_ID = "theme_id";
    private static final String TAG = "ThemesActivity";

    private Button mActivateButton;
    private ThemeEditColorView mBackgroundColorView;
    private ThemeEditColorView mLinkColorView;
    private Adapter mListAdapter;
    private ThemeManager mManager;
    private View mMoreView;
    private TextView mNameView;
    private View mNoThemesView;
    private TextView mPreviewView;
    private View mRenameView;
    private ThemeEditColorView mTextColorView;
    private Theme mTheme;
    private View mThemeFrameView;
    private List<Theme> mThemeList;
    private CheckBox mUseDarkChromeView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_themes);
        setDisplayUpButton(true);

        if (!ProManager.isUnlocked(this)) {
            finish();
            return;
        }

        this.mManager = ThemeManager.getInstance(this);
        this.mThemeList = this.mManager.getThemes();

        RecyclerView recyclerView = findViewById(R.id.list);
        recyclerView.setLayoutManager(new LinearLayoutManager(this, RecyclerView.VERTICAL, false));
        this.mListAdapter = new Adapter(this);
        recyclerView.setAdapter(this.mListAdapter);

        this.mThemeFrameView = findViewById(R.id.theme_frame);
        this.mNoThemesView = findViewById(R.id.no_themes);
        this.mNameView = findViewById(R.id.name);

        this.mActivateButton = findViewById(R.id.activate_button);
        this.mActivateButton.setOnClickListener(this);

        this.mRenameView = findViewById(R.id.rename_button);
        this.mRenameView.setOnClickListener(this);

        this.mMoreView = findViewById(R.id.more_button);
        this.mMoreView.setOnClickListener(this);

        this.mPreviewView = findViewById(R.id.preview);
        this.mPreviewView.setText(HtmlCompat.fromHtml(getString(R.string.theme_preview_text)));

        this.mBackgroundColorView = findViewById(R.id.background_color);
        setupColorView(this.mBackgroundColorView);

        this.mTextColorView = findViewById(R.id.text_color);
        setupColorView(this.mTextColorView);

        this.mLinkColorView = findViewById(R.id.link_color);
        setupColorView(this.mLinkColorView);

        this.mUseDarkChromeView = findViewById(R.id.use_dark_chrome);
        this.mUseDarkChromeView.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (this.mTheme != null) {
                this.mTheme.darkChrome = isChecked;
                this.mManager.updateThemeDarkChrome(this.mTheme.id, isChecked);
            }
        });

        if (!this.mThemeList.isEmpty()) {
            long targetId;
            Bundle extras = getIntent() != null ? getIntent().getExtras() : null;
            if (savedInstanceState != null && savedInstanceState.containsKey("id")) {
                targetId = savedInstanceState.getLong("id");
            } else if (extras != null && extras.containsKey(EXTRA_THEME_ID)) {
                targetId = extras.getLong(EXTRA_THEME_ID);
            } else {
                targetId = this.mThemeList.get(0).id;
            }

            if (!selectTheme(targetId)) {
                selectTheme(this.mThemeList.get(0).id);
            }
            hideNoThemes();
        } else {
            showNoThemes();
        }
    }

    private void showNoThemes() {
        this.mThemeFrameView.setVisibility(View.GONE);
        this.mNoThemesView.setVisibility(View.VISIBLE);
    }

    private void hideNoThemes() {
        this.mThemeFrameView.setVisibility(View.VISIBLE);
        this.mNoThemesView.setVisibility(View.GONE);
    }

    private void setupColorView(ThemeEditColorView colorView) {
        FragmentManager fm = getSupportFragmentManager();
        String tag = "color_" + colorView.getId();
        colorView.setFragmentManager(fm);
        colorView.setFragmentTag(tag);
        colorView.setOnColorChangedListener(this);

        Fragment restored = fm.findFragmentByTag(tag);
        if (restored != null) {
            colorView.onFragmentRestored(restored);
        }
    }

    private boolean selectTheme(long id) {
        Theme theme = findThemeWithId(id);
        if (theme == null) {
            Log.e(TAG, "No such theme " + id);
            return false;
        }

        this.mTheme = theme;
        this.mNameView.setText(theme.name);
        this.mBackgroundColorView.setColor(this.mTheme.backgroundColor);
        this.mTextColorView.setColor(this.mTheme.textColor);
        this.mLinkColorView.setColor(this.mTheme.linkColor);
        this.mUseDarkChromeView.setChecked(this.mTheme.darkChrome);

        updatePreview();
        updateActivateButton();
        this.mListAdapter.notifyDataSetChanged();
        return true;
    }

    private void updatePreview() {
        if (this.mTheme != null) {
            this.mPreviewView.setBackgroundColor(this.mTheme.backgroundColor | 0xFF000000);
            this.mPreviewView.setTextColor(this.mTheme.textColor | 0xFF000000);
            this.mPreviewView.setLinkTextColor(this.mTheme.linkColor | 0xFF000000);
        }
    }

    private void updateActivateButton() {
        boolean isActive = this.mTheme.id == this.mManager.getThemeId();
        Drawable icon = isActive ? ContextCompat.getDrawable(this, R.drawable.ic_theme_active) : null;
        if (icon != null) {
            icon = DrawableCompat.wrap(icon);
            DrawableCompat.setTint(icon, ContextCompat.getColor(this, R.color.app_secondary));
        }
        this.mActivateButton.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null);
        this.mActivateButton.setText(isActive ? R.string.theme_active : R.string.theme_activate);
        this.mActivateButton.setEnabled(!isActive);
    }

    private void refreshThemes() {
        this.mThemeList = this.mManager.getThemes();
        this.mListAdapter.notifyDataSetChanged();
    }

    private Theme findThemeWithId(long id) {
        for (Theme theme : this.mThemeList) {
            if (theme.id == id) return theme;
        }
        return null;
    }

    public void onThemeClick(long id) {
        selectTheme(id);
    }

    @Override
    public void onColorChanged(View view, int color) {
        if (view == this.mBackgroundColorView) {
            this.mTheme.backgroundColor = color;
            this.mManager.updateThemeColor(this.mTheme.id, ThemeManager.BACKGROUND_COLOR, color);
        } else if (view == this.mTextColorView) {
            this.mTheme.textColor = color;
            this.mManager.updateThemeColor(this.mTheme.id, ThemeManager.TEXT_COLOR, color);
        } else if (view == this.mLinkColorView) {
            this.mTheme.linkColor = color;
            this.mManager.updateThemeColor(this.mTheme.id, ThemeManager.LINK_COLOR, color);
        }
        updatePreview();
        refreshThemes();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (this.mTheme != null) {
            outState.putLong("id", this.mTheme.id);
        }
    }

    @Override
    public void onClick(View view) {
        if (view == this.mRenameView) {
            showRenameDialog();
        } else if (view == this.mMoreView) {
            showMoreMenu();
        } else if (view == this.mActivateButton) {
            this.mManager.setTheme(this.mTheme.id);
            updateActivateButton();
        }
    }

    private void showMoreMenu() {
        PopupMenu popupMenu = new PopupMenu(this, this.mMoreView);
        popupMenu.inflate(R.menu.theme);
        popupMenu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.delete) {
                showDeleteDialog();
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    public void showRenameDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_theme_name, null, false);
        final EditText nameInput = dialogView.findViewById(R.id.name);
        nameInput.setText(this.mTheme.name);
        nameInput.selectAll();

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.theme_rename_dialog_title)
                .setNegativeButton(R.string.theme_rename_dialog_cancel, null)
                .setPositiveButton(R.string.theme_rename_dialog_ok, (d, which) -> {
                    String newName = nameInput.getText().toString().trim();
                    if (!TextUtils.isEmpty(newName)) {
                        this.mTheme.name = newName;
                        this.mNameView.setText(newName);
                        this.mManager.updateThemeName(this.mTheme.id, newName);
                        refreshThemes();
                    }
                })
                .setView(dialogView)
                .create();

        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        nameInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                        .setEnabled(!nameInput.getText().toString().trim().isEmpty());
            }
        });
        nameInput.setOnEditorActionListener((v, actionId, event) -> {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).callOnClick();
            return true;
        });
        dialog.show();
    }

    public void showNewDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_theme_name, null, false);
        final EditText nameInput = dialogView.findViewById(R.id.name);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.theme_new_dialog_title)
                .setNegativeButton(R.string.theme_new_dialog_cancel, null)
                .setPositiveButton(R.string.theme_new_dialog_ok, (d, which) -> {
                    String newName = nameInput.getText().toString().trim();
                    if (!TextUtils.isEmpty(newName)) {
                        long newId = (mTheme != null)
                                ? mManager.newThemeCloned(mTheme, newName)
                                : mManager.newTheme(newName, 0x000000, 0xFFFFFF, 0xF0E069, true);
                        if (newId > 0) {
                            refreshThemes();
                            selectTheme(newId);
                            hideNoThemes();
                        } else {
                            Log.e(TAG, "Failed to create new theme.");
                        }
                    }
                })
                .setView(dialogView)
                .create();

        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        nameInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                        .setEnabled(!nameInput.getText().toString().trim().isEmpty());
            }
        });
        nameInput.setOnEditorActionListener((v, actionId, event) -> {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).callOnClick();
            return true;
        });
        dialog.show();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
    }

    private void showDeleteDialog() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.theme_delete_dialog_message)
                .setNegativeButton(R.string.theme_delete_dialog_cancel, null)
                .setPositiveButton(R.string.theme_delete_dialog_ok, (dialog, which) -> deleteCurrentTheme())
                .show();
    }

    private void deleteCurrentTheme() {
        int index = -1;
        for (int i = 0; i < this.mThemeList.size(); i++) {
            if (this.mThemeList.get(i).id == this.mTheme.id) {
                index = i;
                break;
            }
        }

        this.mManager.deleteTheme(this.mTheme.id);
        refreshThemes();

        if (this.mThemeList.isEmpty()) {
            this.mTheme = null;
            showNoThemes();
        } else if (index < 1) {
            selectTheme(this.mThemeList.get(0).id);
        } else if (index < this.mThemeList.size()) {
            selectTheme(this.mThemeList.get(index).id);
        } else {
            selectTheme(this.mThemeList.get(index - 1).id);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.themes, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (super.onOptionsItemSelected(item)) return true;
        if (item.getItemId() == R.id.reset) {
            showResetDialog();
            return true;
        }
        return false;
    }

    private void showResetDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.themes_reset_dialog_title)
                .setMessage(R.string.themes_reset_dialog_message)
                .setNegativeButton(R.string.themes_reset_dialog_cancel, null)
                .setPositiveButton(R.string.themes_reset_dialog_ok, (dialog, which) -> {
                    this.mManager.resetToDefaults();
                    refreshThemes();
                    if (!this.mThemeList.isEmpty()) {
                        selectTheme(this.mThemeList.get(0).id);
                    }
                    hideNoThemes();
                })
                .show();
    }

    /** Convenience to avoid boilerplate in anonymous TextWatcher */
    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }

    private class Adapter extends RecyclerView.Adapter<ViewHolder> {
        private final Context mAdapterContext;

        public Adapter(Context context) {
            this.mAdapterContext = context;
            setHasStableIds(true);
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(this.mAdapterContext).inflate(viewType, parent, false));
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            if (position == ThemesActivity.this.mThemeList.size()) return;
            Theme theme = ThemesActivity.this.mThemeList.get(position);
            holder.compound.setText(theme.name);
            holder.compound.setActivated(ThemesActivity.this.mTheme != null
                    && ThemesActivity.this.mTheme.id == theme.id);

            int color = theme.backgroundColor | 0xFF000000;
            GradientDrawable drawable = (GradientDrawable) ContextCompat.getDrawable(
                    this.mAdapterContext, R.drawable.themes_theme_circle).mutate();
            drawable.setColor(color);
            drawable.setStroke(
                    Utils.dpToPx(this.mAdapterContext, 1),
                    ColorUtils.compositeColors(
                            ContextCompat.getColor(this.mAdapterContext, R.color.color_circle_stroke_overlay), color));

            holder.compound.setCompoundDrawablesWithIntrinsicBounds(null, drawable, null, null);
        }

        @Override
        public int getItemCount() {
            return ThemesActivity.this.mThemeList.size() + 1;
        }

        @Override
        public int getItemViewType(int position) {
            return position < ThemesActivity.this.mThemeList.size()
                    ? R.layout.themes_theme_item : R.layout.themes_theme_add_item;
        }

        @Override
        public long getItemId(int position) {
            if (position == ThemesActivity.this.mThemeList.size()) return -1L;
            return ThemesActivity.this.mThemeList.get(position).id;
        }
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        public TextView compound;

        public ViewHolder(View view) {
            super(view);
            if (view instanceof TextView) {
                this.compound = (TextView) view;
            }
            view.setOnClickListener(v -> {
                long itemId = getItemId();
                if (itemId != -1L) {
                    ThemesActivity.this.onThemeClick(itemId);
                } else {
                    ThemesActivity.this.showNewDialog();
                }
            });
        }
    }
}
