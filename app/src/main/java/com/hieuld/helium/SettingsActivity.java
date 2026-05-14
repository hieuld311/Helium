package com.hieuld.helium;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;
import com.hieuld.helium.library.LibraryFolderPreference;
import com.hieuld.helium.library.LibraryFolderPreferenceDialog;
import com.hieuld.helium.prefs.BasePreferenceFragment;
import com.hieuld.helium.themes.AppThemeManager;

public class SettingsActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_settings);
        setDisplayUpButton(true);
    }

    public static class PreferencesFragment extends BasePreferenceFragment {
        private static final String TAG_DIALOG = "dialog";
        private Preference mBuildVersionPref;

        @Override
        public void onCreatePreferences(Bundle bundle, String str) {
            addPreferencesFromResource(R.xml.preferences);

            // Build version summary
            mBuildVersionPref = findPreference("build_version");
            try {
                String versionName = getActivity().getPackageManager()
                        .getPackageInfo(getActivity().getPackageName(), 0).versionName;
                if (versionName != null) {
                    mBuildVersionPref.setSummary(versionName);
                }
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }

            // Open source licenses
//            findPreference("licenses").setOnPreferenceClickListener(preference -> {
//                startActivity(new Intent(getActivity(), SettingsHtmlActivity.class)
//                        .putExtra(SettingsHtmlActivity.EXTRA_FILE, "licenses")
//                        .putExtra(SettingsHtmlActivity.EXTRA_TITLE_ID, R.string.pref_licenses_title));
//                return true;
//            });

            // Privacy policy
//            findPreference("privacy").setOnPreferenceClickListener(preference -> {
//                startActivity(new Intent(Intent.ACTION_VIEW)
//                        .setData(Uri.parse("https://faultexception.github.io/lithium/privacy.html")));
//                return true;
//            });

            // App theme
            ListPreference listPreference = (ListPreference) findPreference("app_theme");
            final AppThemeManager appThemeManager = AppThemeManager.getInstance(requireContext());
            listPreference.setEntries(getAppThemeEntries());
            listPreference.setEntryValues(getAppThemeValues());
            listPreference.setValue(appThemeManager.getCurrentPrefValue());
            listPreference.setOnPreferenceChangeListener((preference, obj) -> {
                appThemeManager.setTheme((String) obj);
                return true;
            });
        }

        @Override
        public void onDisplayPreferenceDialog(Preference preference) {
            if (preference instanceof LibraryFolderPreference) {
                if (requireFragmentManager().findFragmentByTag(TAG_DIALOG) != null) {
                    return;
                }
                LibraryFolderPreferenceDialog dialog =
                        LibraryFolderPreferenceDialog.newInstance(preference.getKey());
                dialog.setTargetFragment(this, 0);
                dialog.show(requireFragmentManager(), TAG_DIALOG);
                return;
            }
            super.onDisplayPreferenceDialog(preference);
        }

        private CharSequence[] getAppThemeEntries() {
            return new CharSequence[]{
                    getString(R.string.pref_app_theme_light),
                    getString(R.string.pref_app_theme_dark),
                    getString(Build.VERSION.SDK_INT >= 29
                            ? R.string.pref_app_theme_system_default
                            : R.string.pref_app_theme_battery_saver)
            };
        }

        private CharSequence[] getAppThemeValues() {
            return new CharSequence[]{
                    AppThemeManager.VALUE_LIGHT,
                    AppThemeManager.VALUE_DARK,
                    AppThemeManager.VALUE_AUTO
            };
        }
    }
}
