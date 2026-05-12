package com.hieuld.helium;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.hieuld.helium.db.ThemesTable;
import com.hieuld.helium.fonts.Font;
import com.hieuld.helium.fonts.Fonts;
import com.hieuld.helium.fonts.FontsAdapter;
import com.hieuld.helium.model.ProManager;
import com.hieuld.helium.themes.AppThemeManager;
import com.hieuld.helium.themes.Theme;
import com.hieuld.helium.themes.ThemeManager;
import com.hieuld.helium.themes.ThemesActivity;
import com.hieuld.helium.util.Utils;
import com.hieuld.helium.widget.ExpansionScrollView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DisplaySettingsFragment extends Fragment implements View.OnClickListener, SeekBar.OnSeekBarChangeListener, AdapterView.OnItemSelectedListener {
    private static final float LINE_SPACING_MAX = 2.0f;
    private static final float LINE_SPACING_MIN = 1.3f;
    private static final int MARGIN_MAX = 10;
    private static final int MARGIN_MIN = 0;
    private static final String STATE_EXPANDED = "expanded";
    private static final String STATE_FEATURES = "features";
    private static final String STATE_FIXED_LAYOUT = "fixed_layout";
    private static final String STATE_LANGUAGE = "language";
    private static final int TEXT_SIZE_MAX = 260;
    private static final int TEXT_SIZE_MIN = 80;

    private ImageButton mBrightnessAutoButton;
    private SeekBar mBrightnessSeekBar;
    private int mContentFeatures;
    private int mDefaultMargin;
    private View mEndPadding;
    private ImageButton mExpandButton;
    private boolean mFixedLayout;
    private View mFixedLayoutNoteView;
    private Button mFlowAutoButton;
    private Button mFlowPagedButton;
    private Button mFlowScrolledButton;
    private View mFlowStyleView;
    private FontsAdapter mFontsAdapter;
    private Spinner mFontsSpinner;
    private View mFontsView;
    private String mLanguage;
    private ImageButton mLineSpacingDecButton;
    private ImageButton mLineSpacingIncButton;
    private TextView mLineSpacingValueView;
    private View mLineSpacingView;
    private ImageButton mMarginDecButton;
    private ImageButton mMarginIncButton;
    private TextView mMarginValueView;
    private View mMarginView;
    private ViewGroup mMoreSection;
    private OnSettingChangedListener mOnSettingChangedListener;
    private SharedPreferences mPrefs;
    private ImageButton mTextAlignJustifyButton;
    private ImageButton mTextAlignStartButton;
    private TextView mTextAlignValueView;
    private View mTextAlignView;
    private ImageButton mTextSizeDecButton;
    private ImageButton mTextSizeIncButton;
    private TextView mTextSizeValueView;
    private View mTextSizeView;
    private RecyclerView mThemeListView;
    private ThemeManager mThemeManager;
    private View mThemeView;
    private ThemesAdapter mThemesAdapter;
    private boolean mThemesLaunched;

    public interface OnSettingChangedListener {
        void onBrightnessChanged();
        void onBrightnessPreview(float f);
        void onFlowStyleChanged(String str);
        void onFontChanged(Font font);
        void onLineSpacingChanged(float f);
        void onMarginChanged(int i);
        void onTextAlignChanged(int i);
        void onTextSizeChanged(int i);
        void onThemeChanged();
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {}

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {}

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        this.mPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        this.mDefaultMargin = getResources().getInteger(R.integer.default_margin_pc);
        this.mThemeManager = ThemeManager.getInstance(getActivity());
    }

    @Override
    public View onCreateView(LayoutInflater layoutInflater, ViewGroup viewGroup, Bundle bundle) {
        final ExpansionScrollView expansionScrollView = (ExpansionScrollView) layoutInflater.inflate(R.layout.fragment_display_settings, viewGroup, false);
        this.mFixedLayoutNoteView = expansionScrollView.findViewById(R.id.fixedLayoutNote);
        this.mTextSizeView = expansionScrollView.findViewById(R.id.text_size);
        this.mTextSizeValueView = (TextView) expansionScrollView.findViewById(R.id.textSizeValue);

        this.mTextSizeIncButton = (ImageButton) expansionScrollView.findViewById(R.id.textSizeIncrease);
        this.mTextSizeIncButton.setOnClickListener(this);
        this.mTextSizeIncButton.setImageDrawable(DrawableCompat.wrap(this.mTextSizeIncButton.getDrawable().mutate()));

        this.mTextSizeDecButton = (ImageButton) expansionScrollView.findViewById(R.id.textSizeDecrease);
        this.mTextSizeDecButton.setOnClickListener(this);
        this.mTextSizeDecButton.setImageDrawable(DrawableCompat.wrap(this.mTextSizeDecButton.getDrawable().mutate()));

        this.mMarginView = expansionScrollView.findViewById(R.id.margin);
        this.mMarginValueView = (TextView) expansionScrollView.findViewById(R.id.marginValue);

        this.mMarginIncButton = (ImageButton) expansionScrollView.findViewById(R.id.marginIncrease);
        this.mMarginIncButton.setOnClickListener(this);
        this.mMarginIncButton.setImageDrawable(DrawableCompat.wrap(this.mMarginIncButton.getDrawable().mutate()));

        this.mMarginDecButton = (ImageButton) expansionScrollView.findViewById(R.id.marginDecrease);
        this.mMarginDecButton.setOnClickListener(this);
        this.mMarginDecButton.setImageDrawable(DrawableCompat.wrap(this.mMarginDecButton.getDrawable().mutate()));

        this.mLineSpacingView = expansionScrollView.findViewById(R.id.line_spacing);
        this.mLineSpacingValueView = (TextView) expansionScrollView.findViewById(R.id.lineSpacingValue);

        this.mLineSpacingIncButton = (ImageButton) expansionScrollView.findViewById(R.id.lineSpacingIncrease);
        this.mLineSpacingIncButton.setOnClickListener(this);
        this.mLineSpacingIncButton.setImageDrawable(DrawableCompat.wrap(this.mLineSpacingIncButton.getDrawable().mutate()));

        this.mLineSpacingDecButton = (ImageButton) expansionScrollView.findViewById(R.id.lineSpacingDecrease);
        this.mLineSpacingDecButton.setOnClickListener(this);
        this.mLineSpacingDecButton.setImageDrawable(DrawableCompat.wrap(this.mLineSpacingDecButton.getDrawable().mutate()));

        this.mTextAlignView = expansionScrollView.findViewById(R.id.text_align);
        this.mTextAlignValueView = (TextView) expansionScrollView.findViewById(R.id.text_align_value);

        this.mTextAlignStartButton = (ImageButton) expansionScrollView.findViewById(R.id.text_align_start);
        this.mTextAlignStartButton.setOnClickListener(this);
        this.mTextAlignStartButton.setImageDrawable(DrawableCompat.wrap(this.mTextAlignStartButton.getDrawable().mutate()));

        this.mTextAlignJustifyButton = (ImageButton) expansionScrollView.findViewById(R.id.text_align_justify);
        this.mTextAlignJustifyButton.setOnClickListener(this);
        this.mTextAlignJustifyButton.setImageDrawable(DrawableCompat.wrap(this.mTextAlignJustifyButton.getDrawable().mutate()));

        this.mBrightnessAutoButton = (ImageButton) expansionScrollView.findViewById(R.id.brightness_auto);
        this.mBrightnessAutoButton.setOnClickListener(this);
        this.mBrightnessAutoButton.setImageDrawable(DrawableCompat.wrap(this.mBrightnessAutoButton.getDrawable()));

        this.mBrightnessSeekBar = (SeekBar) expansionScrollView.findViewById(R.id.brightness_seek);
        this.mBrightnessSeekBar.setOnSeekBarChangeListener(this);
        this.mBrightnessSeekBar.setThumb(DrawableCompat.wrap(this.mBrightnessSeekBar.getThumb()));
        DrawableCompat.setTint(this.mBrightnessSeekBar.getThumb(), ContextCompat.getColor(layoutInflater.getContext(), R.color.app_secondary));

        this.mThemeView = expansionScrollView.findViewById(R.id.theme);
        this.mThemeListView = (RecyclerView) expansionScrollView.findViewById(R.id.theme_list);
        this.mThemeListView.setLayoutManager(new LinearLayoutManager(layoutInflater.getContext(), RecyclerView.HORIZONTAL, false));
        this.mThemeListView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        this.mThemesAdapter = new ThemesAdapter(layoutInflater.getContext());
        this.mThemesAdapter.setThemes(this.mThemeManager.getThemes());
        this.mThemesAdapter.setCurrentThemeId(this.mThemeManager.getThemeId());
        this.mThemeListView.setAdapter(this.mThemesAdapter);

        this.mFontsView = expansionScrollView.findViewById(R.id.font);
        this.mFontsSpinner = (Spinner) expansionScrollView.findViewById(R.id.font_spinner);
        this.mFontsAdapter = new FontsAdapter(layoutInflater.getContext());
        this.mFontsSpinner.setAdapter((SpinnerAdapter) this.mFontsAdapter);
        this.mFontsSpinner.setOnItemSelectedListener(this);

        this.mFlowStyleView = expansionScrollView.findViewById(R.id.flow);

        this.mFlowAutoButton = (Button) expansionScrollView.findViewById(R.id.flow_auto);
        this.mFlowAutoButton.setOnClickListener(this);
        this.mFlowAutoButton.setCompoundDrawables(null, DrawableCompat.wrap(this.mFlowAutoButton.getCompoundDrawables()[1]), null, null);
        DrawableCompat.setTintList(this.mFlowAutoButton.getCompoundDrawables()[1], AppCompatResources.getColorStateList(layoutInflater.getContext(), R.color.display_settings_control_color_selector));

        this.mFlowPagedButton = (Button) expansionScrollView.findViewById(R.id.flow_paged);
        this.mFlowPagedButton.setOnClickListener(this);
        this.mFlowPagedButton.setCompoundDrawables(null, DrawableCompat.wrap(this.mFlowPagedButton.getCompoundDrawables()[1]), null, null);
        DrawableCompat.setTintList(this.mFlowPagedButton.getCompoundDrawables()[1], AppCompatResources.getColorStateList(layoutInflater.getContext(), R.color.display_settings_control_color_selector));

        this.mFlowScrolledButton = (Button) expansionScrollView.findViewById(R.id.flow_scrolled);
        this.mFlowScrolledButton.setOnClickListener(this);
        this.mFlowScrolledButton.setCompoundDrawables(null, DrawableCompat.wrap(this.mFlowScrolledButton.getCompoundDrawables()[1]), null, null);
        DrawableCompat.setTintList(this.mFlowScrolledButton.getCompoundDrawables()[1], AppCompatResources.getColorStateList(layoutInflater.getContext(), R.color.display_settings_control_color_selector));

        this.mEndPadding = expansionScrollView.findViewById(R.id.end_padding);
        this.mExpandButton = (ImageButton) expansionScrollView.findViewById(R.id.expand_more);
        this.mMoreSection = (ViewGroup) expansionScrollView.findViewById(R.id.more_section);

        this.mExpandButton.setOnClickListener(view -> {
            DisplaySettingsFragment.this.mMoreSection.setVisibility(View.VISIBLE);
            DisplaySettingsFragment.this.mExpandButton.setVisibility(View.GONE);
            expansionScrollView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View view2, int i, int i2, int i3, int i4, int i5, int i6, int i7, int i8) {
                    expansionScrollView.removeOnLayoutChangeListener(this);
                    expansionScrollView.smoothScrollTo(0, (int) DisplaySettingsFragment.this.mExpandButton.getY());
                }
            });
        });

        expansionScrollView.setExpandButtonAndContainer(this.mExpandButton, this.mMoreSection);

        if (bundle != null) {
            this.mContentFeatures = bundle.getInt(STATE_FEATURES);
            this.mFixedLayout = bundle.getBoolean(STATE_FIXED_LAYOUT);
            this.mLanguage = bundle.getString(STATE_LANGUAGE);
            if (this.mLanguage != null) {
                this.mFontsAdapter.setFonts(Fonts.getCompatibleFonts(this.mLanguage));
                this.mFontsAdapter.notifyDataSetChanged();
            }
            if (bundle.getBoolean(STATE_EXPANDED)) {
                update();
                this.mMoreSection.setVisibility(View.VISIBLE);
                this.mExpandButton.setVisibility(View.GONE);
            }
        }
        return expansionScrollView;
    }

    @Override
    public void onSaveInstanceState(Bundle bundle) {
        super.onSaveInstanceState(bundle);
        bundle.putInt(STATE_FEATURES, this.mContentFeatures);
        bundle.putBoolean(STATE_FIXED_LAYOUT, this.mFixedLayout);
        bundle.putString(STATE_LANGUAGE, this.mLanguage);
        bundle.putBoolean(STATE_EXPANDED, this.mMoreSection.getVisibility() == View.VISIBLE);
    }

    @Override
    public void onResume() {
        super.onResume();
        update();
        if (this.mThemesLaunched) {
            this.mThemesAdapter.setThemes(this.mThemeManager.getThemes());
            this.mThemesAdapter.setCurrentThemeId(this.mThemeManager.getThemeId());
            this.mThemesAdapter.notifyDataSetChanged();
            this.mOnSettingChangedListener.onThemeChanged();
        }
    }

    public void update() {
        if (isAdded()) {
            this.mFixedLayoutNoteView.setVisibility(this.mFixedLayout ? View.VISIBLE : View.GONE);
            setVisibilityForFeature(this.mTextSizeView, 1);
            setVisibilityForFeature(this.mMarginView, 2);
            setVisibilityForFeature(this.mLineSpacingView, 8);
            setVisibilityForFeature(this.mFontsView, 4);
            setVisibilityForFeature(this.mTextAlignView, 16);

            int i = this.mPrefs.getInt("textSize", 100);
            this.mTextSizeDecButton.setEnabled(i > 80);
            this.mTextSizeIncButton.setEnabled(i < TEXT_SIZE_MAX);
            this.mTextSizeValueView.setText(getString(R.string.display_settings_value_percentage, i));

            int i2 = this.mPrefs.getInt("margin", this.mDefaultMargin);
            this.mMarginDecButton.setEnabled(i2 > 0);
            this.mMarginIncButton.setEnabled(i2 < 10);
            this.mMarginValueView.setText(getString(R.string.display_settings_value_percentage, i2));

            float f = this.mPrefs.getFloat("line_spacing", 1.5f);
            this.mLineSpacingDecButton.setEnabled(f > LINE_SPACING_MIN);
            this.mLineSpacingIncButton.setEnabled(f < LINE_SPACING_MAX);
            this.mLineSpacingValueView.setText(String.format(Locale.getDefault(), "%.1f", f));

            this.mFlowStyleView.setVisibility(this.mFixedLayout ? View.GONE : View.VISIBLE);
            updateFlowButtons();
            updateBrightnessAuto();
            updateBrightnessBar();
            updateTextAlign();
            updateExpandButton();
        }
    }

    public void onProStateChanged() {
        this.mThemesAdapter.notifyDataSetChanged();
    }

    private void updateFlowButtons() {
        if (isAdded()) {
            String string = this.mPrefs.getString("page_flow", AppThemeManager.VALUE_AUTO);
            this.mFlowAutoButton.setActivated(AppThemeManager.VALUE_AUTO.equals(string));
            this.mFlowPagedButton.setActivated("paged".equals(string));
            this.mFlowScrolledButton.setActivated("scrolled".equals(string));
        }
    }

    public void updateBrightnessAuto() {
        if (isAdded()) {
            boolean z = this.mPrefs.getBoolean("brightness_auto", true);
            this.mBrightnessAutoButton.setActivated(z);
            int i = z ? 128 : 255;
            this.mBrightnessSeekBar.getThumb().setAlpha(i);
            this.mBrightnessSeekBar.getProgressDrawable().setAlpha(i);
        }
    }

    public void updateBrightnessBar() {
        this.mBrightnessSeekBar.setProgress(Math.round(this.mPrefs.getFloat("brightness", 0.5f) * 100.0f));
    }

    private void updateTextAlign() {
        int i = this.mPrefs.getInt("text_align", 0);
        this.mTextAlignValueView.setText(i != 1 ? i != 2 ? R.string.display_settings_text_align_default : R.string.display_settings_text_align_justify : R.string.display_settings_text_align_start);
        this.mTextAlignStartButton.setActivated(i == 1);
        this.mTextAlignJustifyButton.setActivated(i == 2);
    }

    private void updateExpandButton() {
        if (this.mMoreSection.getVisibility() == View.VISIBLE) {
            return;
        }
        boolean isAnyChildVisible = false;
        for (int i = 0; i < this.mMoreSection.getChildCount(); i++) {
            if (this.mMoreSection.getChildAt(i).getVisibility() == View.VISIBLE) {
                isAnyChildVisible = true;
                break;
            }
        }
        this.mExpandButton.setVisibility(isAnyChildVisible ? View.VISIBLE : View.GONE);
        this.mEndPadding.setVisibility(isAnyChildVisible ? View.GONE : View.INVISIBLE);
    }

    public void setOnSettingChangedListener(OnSettingChangedListener onSettingChangedListener) {
        this.mOnSettingChangedListener = onSettingChangedListener;
    }

    public void setFixedLayout(boolean z) {
        this.mFixedLayout = z;
        update();
    }

    public void setFeatures(int i) {
        this.mContentFeatures = i;
        update();
    }

    public void setLanguage(String str) {
        this.mLanguage = str;
        this.mFontsAdapter.setFonts(Fonts.getCompatibleFonts(str));
        this.mFontsAdapter.notifyDataSetChanged();
    }

    public void setCurrentFont(Font font) {
        this.mFontsSpinner.setOnItemSelectedListener(null);
        this.mFontsSpinner.setSelection(font != null ? this.mFontsAdapter.indexOf(font) + 1 : 0, false);
        this.mFontsSpinner.setOnItemSelectedListener(this);
    }

    private void setVisibilityForFeature(View view, int i) {
        view.setVisibility(!this.mFixedLayout && (i & this.mContentFeatures) > 0 ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onClick(View view) {
        int i = 0;
        if (view == this.mTextSizeIncButton || view == this.mTextSizeDecButton) {
            int iMax = Math.max(80, Math.min(this.mPrefs.getInt("textSize", 100) + (view != this.mTextSizeIncButton ? -10 : 10), TEXT_SIZE_MAX));
            this.mPrefs.edit().putInt("textSize", iMax).apply();
            this.mTextSizeDecButton.setEnabled(iMax > 80);
            this.mTextSizeIncButton.setEnabled(iMax < TEXT_SIZE_MAX);
            this.mTextSizeValueView.setText(getString(R.string.display_settings_value_percentage, iMax));
            this.mOnSettingChangedListener.onTextSizeChanged(iMax);
            return;
        }
        if (view == this.mMarginIncButton || view == this.mMarginDecButton) {
            int iMax2 = Math.max(0, Math.min(this.mPrefs.getInt("margin", this.mDefaultMargin) + (view == this.mMarginIncButton ? 1 : -1), 10));
            this.mPrefs.edit().putInt("margin", iMax2).apply();
            this.mMarginDecButton.setEnabled(iMax2 > 0);
            this.mMarginIncButton.setEnabled(iMax2 < 10);
            this.mMarginValueView.setText(getString(R.string.display_settings_value_percentage, iMax2));
            this.mOnSettingChangedListener.onMarginChanged(iMax2);
            return;
        }
        if (view == this.mLineSpacingIncButton || view == this.mLineSpacingDecButton) {
            float fMax = Math.max(LINE_SPACING_MIN, Math.min(this.mPrefs.getFloat("line_spacing", 1.5f) + (view == this.mLineSpacingIncButton ? 0.1f : -0.1f), LINE_SPACING_MAX));
            this.mPrefs.edit().putFloat("line_spacing", fMax).apply();
            this.mLineSpacingDecButton.setEnabled(fMax > LINE_SPACING_MIN);
            this.mLineSpacingIncButton.setEnabled(fMax < LINE_SPACING_MAX);
            this.mLineSpacingValueView.setText(String.format(Locale.getDefault(), "%.1f", fMax));
            this.mOnSettingChangedListener.onLineSpacingChanged(fMax);
            return;
        }
        if (view == this.mTextAlignStartButton || view == this.mTextAlignJustifyButton) {
            int i2 = this.mPrefs.getInt("text_align", 0);
            SharedPreferences.Editor editorEdit = this.mPrefs.edit();
            if ((view != this.mTextAlignStartButton || i2 != 1) && (view != this.mTextAlignJustifyButton || i2 != 2)) {
                i = view == this.mTextAlignStartButton ? 1 : view == this.mTextAlignJustifyButton ? 2 : i2;
            }
            editorEdit.putInt("text_align", i).apply();
            updateTextAlign();
            this.mOnSettingChangedListener.onTextAlignChanged(i);
            return;
        }
        if (view == this.mFlowAutoButton || view == this.mFlowPagedButton || view == this.mFlowScrolledButton) {
            String str = AppThemeManager.VALUE_AUTO;
            String string = this.mPrefs.getString("page_flow", AppThemeManager.VALUE_AUTO);
            if (AppThemeManager.VALUE_AUTO.equals(string) && view == this.mFlowAutoButton) return;
            if ("paged".equals(string) && view == this.mFlowPagedButton) return;
            if ("scrolled".equals(string) && view == this.mFlowScrolledButton) return;

            SharedPreferences.Editor editorEdit2 = this.mPrefs.edit();
            if (view != this.mFlowAutoButton) {
                str = view == this.mFlowPagedButton ? "paged" : (view == this.mFlowScrolledButton ? "scrolled" : string);
            }
            editorEdit2.putString("page_flow", str).apply();
            updateFlowButtons();
            this.mOnSettingChangedListener.onFlowStyleChanged(str);
            return;
        }
        if (view == this.mBrightnessAutoButton) {
            saveAutoBrightness(!this.mPrefs.getBoolean("brightness_auto", true));
            updateBrightnessAuto();
            this.mOnSettingChangedListener.onBrightnessChanged();
        }
    }

    private void saveBrightnessValue(float f) {
        this.mPrefs.edit().putFloat("brightness", f).apply();
    }

    private void saveAutoBrightness(boolean z) {
        this.mPrefs.edit().putBoolean("brightness_auto", z).apply();
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int i, boolean z) {
        if (z) {
            this.mOnSettingChangedListener.onBrightnessPreview(i / 100.0f);
            if (this.mPrefs.getBoolean("brightness_auto", true)) {
                saveAutoBrightness(false);
                updateBrightnessAuto();
            }
        }
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        saveBrightnessValue(seekBar.getProgress() / 100.0f);
    }

    public void onThemeClick(long j) {
        this.mThemeManager.setTheme(j);
        this.mOnSettingChangedListener.onThemeChanged();
        this.mThemesAdapter.setCurrentThemeId(j);
        this.mThemesAdapter.notifyDataSetChanged();
    }

    public void onMoreThemesClick() {
        Context context = getContext();
        if (!ProManager.isUnlocked(context)) {
            new AlertDialog.Builder(context)
                    .setMessage(R.string.display_settings_theme_more_upgrade_required_message)
                    .setNegativeButton(R.string.display_settings_theme_more_upgrade_required_cancel, null)
                    .setPositiveButton(R.string.display_settings_theme_more_upgrade_required_upgrade,
                            (dialog, which) -> startActivity(ProManager.getUpgradeIntent(ThemesTable.TABLE_NAME)))
                    .show();
            return;
        }
        Intent intent = new Intent(context, ThemesActivity.class);
        long themeId = this.mThemeManager.getThemeId();
        if (themeId > 0) {
            intent.putExtra(ThemesActivity.EXTRA_THEME_ID, themeId);
        }
        startActivity(intent);
        this.mThemesLaunched = true;
    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long j) {
        Font font = (Font) this.mFontsAdapter.getItem(i);
        this.mOnSettingChangedListener.onFontChanged(font);
        this.mPrefs.edit().putString("font", font != null ? font.name : null).apply();
    }

    public class ThemesAdapter extends RecyclerView.Adapter<ThemesAdapter.ViewHolder> {
        private static final int TYPE_THEME = 0;
        private static final int TYPE_MENU = 1;
        private Context mContext;
        private long mCurrentThemeId;
        private List<Theme> mThemes = new ArrayList<>();

        public ThemesAdapter(Context context) {
            this.mContext = context;
            setHasStableIds(true);
        }

        public void setThemes(List<Theme> list) {
            this.mThemes = list;
        }

        public void setCurrentThemeId(long j) {
            this.mCurrentThemeId = j;
        }

        @Override
        public int getItemCount() {
            return this.mThemes.size() + 2;
        }

        @Override
        public long getItemId(int i) {
            if (i <= 0 || i > this.mThemes.size()) return 0L;
            return this.mThemes.get(i - 1).id;
        }

        @Override
        public int getItemViewType(int i) {
            return i <= this.mThemes.size() ? TYPE_THEME : TYPE_MENU;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
            return new ViewHolder(LayoutInflater.from(this.mContext)
                    .inflate(i == TYPE_THEME ? R.layout.display_theme_list_item : R.layout.display_theme_list_item_menu, viewGroup, false));
        }

        @Override
        public void onBindViewHolder(ViewHolder viewHolder, int i) {
            if (getItemViewType(i) == TYPE_MENU) return;
            Theme theme = i > 0 ? this.mThemes.get(i - 1) : null;
            long j = theme != null ? theme.id : 0L;
            int i3 = theme != null ? theme.backgroundColor | ViewCompat.MEASURED_STATE_MASK : -1;
            GradientDrawable gradientDrawable = (GradientDrawable) ContextCompat.getDrawable(this.mContext, R.drawable.display_theme_circle).mutate();
            gradientDrawable.setColor(i3);
            gradientDrawable.setStroke(Utils.dpToPx(this.mContext, 1), ColorUtils.compositeColors(ContextCompat.getColor(this.mContext, R.color.color_circle_stroke_overlay), i3));
            viewHolder.icon.setImageDrawable(gradientDrawable);
            viewHolder.icon.setContentDescription(theme != null ? theme.name : this.mContext.getString(R.string.display_settings_theme_none));
            viewHolder.checked.setVisibility(j == this.mCurrentThemeId ? View.VISIBLE : View.GONE);

            boolean z = ColorUtils.calculateLuminance(i3) > 0.5d;
            Drawable drawable = viewHolder.checked.getDrawable();
            DrawableCompat.setTint(drawable, z ? ViewCompat.MEASURED_STATE_MASK : -1);
        }

        protected class ViewHolder extends RecyclerView.ViewHolder {
            ImageView checked;
            ImageView icon;

            public ViewHolder(View view) {
                super(view);
                this.icon = (ImageView) view.findViewById(R.id.icon);
                if (this.icon == null) {
                    view.setOnClickListener(v -> DisplaySettingsFragment.this.onMoreThemesClick());
                    return;
                }
                this.checked = (ImageView) view.findViewById(R.id.checked);
                this.checked.setImageDrawable(DrawableCompat.wrap(this.checked.getDrawable().mutate()));
                view.setOnClickListener(v -> DisplaySettingsFragment.this.onThemeClick(getItemId()));
            }
        }
    }
}