package com.hieuld.helium;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Toast;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import androidx.lifecycle.LifecycleOwner;
import com.hieuld.helium.db.BookCategoryLinksTable;
import com.hieuld.helium.db.BooksTable;
import com.hieuld.helium.db.DatabaseProvider;
import com.hieuld.helium.library.LibraryUpdate;
import com.hieuld.helium.library.LibraryUpdateControllerFragment;
import com.hieuld.helium.library.LibraryUpdateReportDialog;
import com.hieuld.helium.model.SearchHistoryManager;
import com.hieuld.helium.ui.RecentSearchesAdapter;
import com.hieuld.helium.util.AnimationUtils;
import com.hieuld.helium.util.AsyncHelper;
import com.hieuld.helium.util.FragmentComparison;
import com.hieuld.helium.util.SearchableFragment;
import com.hieuld.helium.util.ThemeUtils;
import com.hieuld.helium.util.Utils;
import com.hieuld.helium.widget.ScrimInsetsFrameLayout;
import com.hieuld.helium.widget.SearchBarView;
import com.google.android.material.snackbar.Snackbar;
import java.util.ArrayList;
import java.util.Collections;

public class MainActivity extends BaseActivity implements MainDrawerFragment.Listener, SearchBarView.SearchBarListener, LibraryUpdate.Listener, AdapterView.OnItemClickListener {
    private static final String FRAGMENT_UPDATE_CONTROLLER = "update_controller";
    private static final int REQUEST_SEARCH_VOICE = 0;
    private static final String TAG = "MainActivity";

    private View mContentView;
    private boolean mCreateShortcut;
    private MainDrawerFragment mDrawer;
    private DrawerLayout mDrawerLayout;
    private Fragment mFragment;
    private Handler mHandler;
    private boolean mNoticeShown;
    private SharedPreferences mPrefs;
    private RecentSearchesAdapter mRecentSearchesAdapter;
    private ListView mRecentSearchesListView;
    private Snackbar mRefreshSnackbar;
    private SearchBarView mSearchBarView;
    private SearchHistoryManager mSearchHistoryManager;
    private String mSearchQuery;
    private boolean mSearchShown;
    private boolean mSearchSubmitted;
    private LibraryUpdateControllerFragment mUpdateControllerFragment;
    private Runnable mHideNoticeRunnable = this::hideNotice;
    private AsyncHelper mAsync = new AsyncHelper();

    @Override
    protected void onCreate(Bundle bundle) {
        setTheme(R.style.Theme_Helium_WithStatusBar);
        super.onCreate(bundle);
        setToolbarHasElevation(false);
        setContentView(R.layout.activity_main);
        setNoWindowBackground();
        setDisplayUpButton(true);
        getToolbar().setNavigationIcon(R.drawable.ic_action_hamburger);
        ViewCompat.setElevation(findViewById(R.id.toolbar_container), Utils.dpToPx(this, 4));
        this.mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        Intent intent = getIntent();
        this.mCreateShortcut = intent != null && "android.intent.action.CREATE_SHORTCUT".equals(intent.getAction());
        DrawerLayout drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        this.mDrawerLayout = drawerLayout;
        drawerLayout.setStatusBarBackgroundColor(ContextCompat.getColor(this, R.color.app_primary_dark));
        MainDrawerFragment mainDrawerFragment = (MainDrawerFragment) getSupportFragmentManager().findFragmentById(R.id.drawer);
        this.mDrawer = mainDrawerFragment;
        mainDrawerFragment.setListener(this);
        ((ScrimInsetsFrameLayout) findViewById(R.id.drawer_scrim)).setOnInsetsCallback(rect -> this.mDrawer.setInset(rect.top));
        this.mContentView = findViewById(R.id.content);
        SearchBarView searchBarView = (SearchBarView) findViewById(R.id.search_bar);
        this.mSearchBarView = searchBarView;
        searchBarView.setVisibility(View.GONE);
        this.mSearchBarView.setListener(this);
        this.mSearchBarView.addExtraMenuResource(R.menu.main_search, menuItem -> {
            if (menuItem.getItemId() != R.id.select_all) {
                return false;
            }
            searchSelectAll();
            return true;
        });
        ListView listView = (ListView) findViewById(R.id.recent_searches);
        this.mRecentSearchesListView = listView;
        listView.setOnItemClickListener(this);
        RecentSearchesAdapter recentSearchesAdapter = new RecentSearchesAdapter(this);
        this.mRecentSearchesAdapter = recentSearchesAdapter;
        this.mRecentSearchesListView.setAdapter((ListAdapter) recentSearchesAdapter);
        this.mSearchHistoryManager = new SearchHistoryManager(DatabaseProvider.getDatabase(this), "main");
        FragmentManager supportFragmentManager = getSupportFragmentManager();
        supportFragmentManager.addOnBackStackChangedListener(() -> {
            this.mFragment = getSupportFragmentManager().findFragmentById(R.id.content);
            updateFragmentInfo();
        });
        LibraryUpdateControllerFragment libraryUpdateControllerFragment = (LibraryUpdateControllerFragment) supportFragmentManager.findFragmentByTag(FRAGMENT_UPDATE_CONTROLLER);
        this.mUpdateControllerFragment = libraryUpdateControllerFragment;
        if (libraryUpdateControllerFragment == null) {
            this.mUpdateControllerFragment = new LibraryUpdateControllerFragment();
            supportFragmentManager.beginTransaction().add(this.mUpdateControllerFragment, FRAGMENT_UPDATE_CONTROLLER).commitNow();
        }
        this.mHandler = new Handler();
        if (bundle == null) {
            int i = this.mPrefs.getInt("filter", 0);
            if (i == 0) {
                switchToFragment(BooksFragment.newInstance(), false);
            } else if (i == 1) {
                switchToFragment(BooksFragment.newCategoryInstance(this.mPrefs.getLong(BookCategoryLinksTable.COLUMN_CATEGORY_ID, -1L)), false);
            } else if (i == 2) {
                switchToFragment(BooksFragment.newFolderInstance(this.mPrefs.getString("folder", null)), false);
            }
        } else {
            this.mFragment = getSupportFragmentManager().findFragmentById(R.id.content);
            updateFragmentInfo();
        }
        if (this.mPrefs.getBoolean("open_last_book", false) && bundle == null && !this.mCreateShortcut) {
            Cursor cursorQuery = DatabaseProvider.getDatabase(this).query(BooksTable.TABLE_NAME, new String[]{"_id"}, null, null, null, null, "last_open_date DESC", "1");
            if (cursorQuery.moveToFirst()) {
                startActivity(new Intent(this, ReaderActivity.class).putExtra("book_id", cursorQuery.getLong(0)));
            }
            cursorQuery.close();
        }
    }

    private void searchSelectAll() {
        Fragment fragment = this.mFragment;
        if (fragment instanceof BooksFragment) {
            ((BooksFragment) fragment).selectAll();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!this.mUpdateControllerFragment.isActiveOrHasResult()) {
            this.mHandler.postDelayed(() -> refreshBooks(false), 250L);
        } else {
            LibraryUpdate.Result result = this.mUpdateControllerFragment.getResult();
            if (result != null) {
                showNotice();
                onLibraryUpdateDone(result);
                this.mUpdateControllerFragment.clear();
            } else if (this.mUpdateControllerFragment.isForced()) {
                showNotice();
            } else if (this.mUpdateControllerFragment.hasFound()) {
                onLibraryUpdateFoundBook();
            }
        }
    }

    public void switchToFragment(Fragment fragment, boolean z) {
        this.mFragment = fragment;
        if (this.mCreateShortcut && (fragment instanceof BooksFragment)) {
            ((BooksFragment) fragment).enablePickMode(j -> createShortcutForBook(j));
        }
        FragmentTransaction fragmentTransactionReplace = getSupportFragmentManager().beginTransaction().replace(R.id.content, fragment);
        if (z) {
            fragmentTransactionReplace.addToBackStack("Entry");
        }
        fragmentTransactionReplace.commit();
        updateFragmentInfo();
    }

    private void updateFragmentInfo() {
        invalidateOptionsMenu();
        this.mDrawer.setCurrentFragment(this.mFragment);
        Fragment fragment = this.mFragment;
        if (fragment instanceof BooksFragment) {
            BooksFragment booksFragment = (BooksFragment) fragment;
            Bundle arguments = booksFragment.getArguments();
            SharedPreferences.Editor editorEdit = this.mPrefs.edit();
            editorEdit.putInt("filter", booksFragment.getFilter());
            if (arguments != null) {
                if (booksFragment.getFilter() == 1) {
                    editorEdit.putLong(BookCategoryLinksTable.COLUMN_CATEGORY_ID, arguments.getLong(BooksFragment.EXTRA_CATEGORY_ID));
                } else if (booksFragment.getFilter() == 2) {
                    editorEdit.putString("folder", arguments.getString("folder"));
                }
            }
            editorEdit.apply();
        }
    }

    public void refreshBooks(boolean z) {
        if (z && !this.mNoticeShown) {
            showNotice();
        }
        this.mUpdateControllerFragment.startUpdate(z);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem menuItemFindItem = menu.findItem(R.id.search);
        Fragment fragment = this.mFragment;
        menuItemFindItem.setVisible(fragment != null && (fragment instanceof SearchableFragment));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        int itemId = menuItem.getItemId();
        if (itemId == android.R.id.home) {
            this.mDrawerLayout.openDrawer(GravityCompat.START);
            return true;
        }
        if (itemId == R.id.refresh) {
            refreshBooks(true);
            return true;
        }
        if (itemId == R.id.search) {
            showSearchBar();
            return true;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    @Override
    protected void onSaveInstanceState(Bundle bundle) {
        super.onSaveInstanceState(bundle);
        bundle.putBoolean("searchShown", this.mSearchShown);
    }

    @Override
    protected void onRestoreInstanceState(Bundle bundle) {
        if (bundle.getBoolean("searchShown")) {
            this.mSearchShown = true;
            this.mSearchBarView.setVisibility(View.VISIBLE);
            this.mDrawerLayout.setStatusBarBackgroundColor(ContextCompat.getColor(this, R.color.search_status_bar_color));
        }
        super.onRestoreInstanceState(bundle);
    }

    private void showSearchBar() {
        if (this.mSearchShown) {
            return;
        }
        FastOutSlowInInterpolator fastOutSlowInInterpolator = new FastOutSlowInInterpolator();
        View viewFindViewById = findViewById(R.id.search);
        if (viewFindViewById != null) {
            View viewFindViewById2 = findViewById(R.id.toolbar_container);
            int[] iArr = new int[2];
            viewFindViewById.getLocationInWindow(iArr);
            int width = iArr[0] + (viewFindViewById.getWidth() / 2);
            Animator animatorCreateCircularReveal = AnimationUtils.createCircularReveal(this.mSearchBarView, width, viewFindViewById2.getHeight() / 2, 0.0f, (float) Math.hypot(width, viewFindViewById2.getHeight() - (viewFindViewById2.getHeight() / 2f)));
            animatorCreateCircularReveal.setInterpolator(fastOutSlowInInterpolator);
            animatorCreateCircularReveal.setDuration(275L);
            animatorCreateCircularReveal.start();
        }
        this.mSearchBarView.setVisibility(View.VISIBLE);
        this.mSearchShown = true;
        this.mSearchBarView.activate();
        showRecentSearches();
        if (Build.VERSION.SDK_INT >= 21) {
            ObjectAnimator objectAnimatorOfArgb = ObjectAnimator.ofArgb(this.mDrawerLayout, "statusBarBackgroundColor", ContextCompat.getColor(this, R.color.app_primary_dark), ContextCompat.getColor(this, R.color.search_status_bar_color));
            objectAnimatorOfArgb.setInterpolator(fastOutSlowInInterpolator);
            objectAnimatorOfArgb.setDuration(275L);
            objectAnimatorOfArgb.start();
        }
        if (Build.VERSION.SDK_INT < 23 || ThemeUtils.isInDarkMode(this)) {
            return;
        }
        this.mSearchBarView.setSystemUiVisibility(8192);
    }

    private void hideSearchBar() {
        if (this.mSearchShown) {
            FastOutSlowInInterpolator fastOutSlowInInterpolator = new FastOutSlowInInterpolator();
            View viewFindViewById = findViewById(R.id.search);
            if (viewFindViewById != null) {
                View viewFindViewById2 = findViewById(R.id.toolbar_container);
                int[] iArr = new int[2];
                viewFindViewById.getLocationInWindow(iArr);
                int width = iArr[0] + (viewFindViewById.getWidth() / 2);
                Animator animatorCreateCircularReveal = AnimationUtils.createCircularReveal(this.mSearchBarView, width, viewFindViewById2.getHeight() / 2, (float) Math.hypot(width, viewFindViewById2.getHeight() - (viewFindViewById2.getHeight() / 2f)), 0.0f);
                animatorCreateCircularReveal.setInterpolator(fastOutSlowInInterpolator);
                animatorCreateCircularReveal.setDuration(275L);
                animatorCreateCircularReveal.addListener(AnimationUtils.goneAfter(this.mSearchBarView));
                animatorCreateCircularReveal.start();
            } else {
                this.mSearchBarView.setVisibility(View.GONE);
            }
            this.mSearchShown = false;
            LifecycleOwner lifecycleOwner = this.mFragment;
            if (lifecycleOwner instanceof SearchableFragment) {
                ((SearchableFragment) lifecycleOwner).onSearchQueryChanged(null);
            }
            this.mSearchBarView.deactivate();
            hideRecentSearches();
            if (Build.VERSION.SDK_INT >= 21) {
                ObjectAnimator objectAnimatorOfArgb = ObjectAnimator.ofArgb(this.mDrawerLayout, "statusBarBackgroundColor", ContextCompat.getColor(this, R.color.search_status_bar_color), ContextCompat.getColor(this, R.color.app_primary_dark));
                objectAnimatorOfArgb.setInterpolator(fastOutSlowInInterpolator);
                objectAnimatorOfArgb.setDuration(275L);
                objectAnimatorOfArgb.start();
            }
            if (Build.VERSION.SDK_INT < 23 || ThemeUtils.isInDarkMode(this)) {
                return;
            }
            this.mSearchBarView.setSystemUiVisibility(0);
        }
    }

    private void showRecentSearches() {
        this.mRecentSearchesListView.animate().cancel();
        this.mRecentSearchesListView.setVisibility(View.VISIBLE);
        this.mRecentSearchesListView.setAlpha(0.0f);
        this.mRecentSearchesListView.animate().alpha(1.0f).setDuration(200L).setListener(null).start();
        this.mRecentSearchesAdapter.setQueries(this.mSearchHistoryManager.getQueries());
    }

    private void hideRecentSearches() {
        this.mRecentSearchesListView.animate().alpha(0.0f).setDuration(200L).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                MainActivity.this.mRecentSearchesListView.setVisibility(View.GONE);
            }
        }).start();
    }

    @Override
    public void onDrawerItemClicked() {
        this.mDrawerLayout.closeDrawer(GravityCompat.START);
    }

    @Override
    public void switchToFragment(Fragment fragment) {
        if (fragment.getClass() == this.mFragment.getClass()) {
            if (!(fragment instanceof FragmentComparison) || ((FragmentComparison) fragment).equalsFragment(this.mFragment)) {
                return;
            }
        }
        switchToFragment(fragment, true);
    }

    private void showNotice() {
        Snackbar snackbarMake = Snackbar.make(this.mContentView, R.string.library_updating, Snackbar.LENGTH_INDEFINITE);
        this.mRefreshSnackbar = snackbarMake;
        snackbarMake.show();
        this.mNoticeShown = true;
        this.mHandler.removeCallbacks(this.mHideNoticeRunnable);
    }

    public void hideNotice() {
        if (this.mRefreshSnackbar != null) {
            this.mRefreshSnackbar.dismiss();
        }
        this.mNoticeShown = false;
    }

    @Override
    public void onBackPressed() {
        if (this.mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            this.mDrawerLayout.closeDrawer(GravityCompat.START);
        } else if (this.mSearchShown) {
            hideSearchBar();
        } else {
            if (getSupportFragmentManager().popBackStackImmediate((String) null, 1)) {
                return;
            }
            super.onBackPressed();
        }
    }

    @Override
    public void onSearchBack() {
        hideSearchBar();
    }

    @Override
    public void onSearchQueryChanged(String str) {
        if (this.mSearchShown) {
            LifecycleOwner lifecycleOwner = this.mFragment;
            if (lifecycleOwner instanceof SearchableFragment) {
                ((SearchableFragment) lifecycleOwner).onSearchQueryChanged(str);
            }
            this.mSearchSubmitted = false;
            this.mSearchQuery = str;
            if (str.isEmpty()) {
                showRecentSearches();
            } else {
                hideRecentSearches();
            }
        }
    }

    @Override
    public void onSearchVoiceClicked() {
        Intent intent = new Intent("android.speech.action.RECOGNIZE_SPEECH");
        if (intent.resolveActivity(getPackageManager()) == null) {
            Toast.makeText(this, R.string.no_voice_input_app, Toast.LENGTH_SHORT).show();
        } else {
            intent.putExtra("android.speech.extra.LANGUAGE_MODEL", "free_form");
            startActivityForResult(intent, 0);
        }
    }

    @Override
    public void onSearchSubmitted(String str) {
        if (this.mSearchSubmitted) {
            return;
        }
        this.mSearchSubmitted = true;
        this.mSearchHistoryManager.submitQuery(str);
    }

    @Override
    public void onSearchClearHistoryClicked() {
        if (this.mSearchHistoryManager.clearHistory()) {
            this.mRecentSearchesAdapter.setQueries(Collections.emptyList());
            this.mRecentSearchesAdapter.notifyDataSetChanged();
        }
    }

    @Override
    protected void onActivityResult(int i, int i2, Intent intent) {
        ArrayList<String> stringArrayListExtra;
        super.onActivityResult(i, i2, intent);
        if (i != 0 || i2 != -1 || (stringArrayListExtra = intent.getStringArrayListExtra("android.speech.extra.RESULTS")) == null || stringArrayListExtra.size() <= 0) {
            return;
        }
        this.mSearchBarView.setQueryAndSubmit(stringArrayListExtra.get(0));
    }

    private void refreshBooksList() {
        Fragment fragment = this.mFragment;
        if (fragment instanceof BooksFragment) {
            ((BooksFragment) fragment).refresh();
        }
    }

    public void refreshDrawer() {
        this.mDrawer.refresh();
    }

    public MainDrawerFragment getDrawerFragment() {
        return this.mDrawer;
    }

    @Override
    public void onSupportActionModeStarted(ActionMode actionMode) {
        super.onSupportActionModeStarted(actionMode);
        this.mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        if (!this.mSearchShown || Build.VERSION.SDK_INT < 23 || ThemeUtils.isInDarkMode(this)) {
            return;
        }
        this.mSearchBarView.setSystemUiVisibility(0);
    }

    @Override
    public void onSupportActionModeFinished(ActionMode actionMode) {
        super.onSupportActionModeFinished(actionMode);
        this.mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
        if (!this.mSearchShown || Build.VERSION.SDK_INT < 23 || ThemeUtils.isInDarkMode(this)) {
            return;
        }
        this.mSearchBarView.setSystemUiVisibility(8192);
    }

    public void createShortcutForBook(final long j) {
        this.mAsync.run(new PrepareBookShortcutDetailsTask(this, j, Utils.dpToPx(this, 48)), bookShortcutDetails -> {
            if (!bookShortcutDetails.success) {
                Log.e(TAG, "Shortcut not added.");
                return;
            }
            Intent intentPutExtra = new Intent(this, ReaderActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP).setAction("android.intent.action.VIEW").putExtra("book_id", j).putExtra(ReaderActivity.EXTRA_LAUNCH_SOURCE, ReaderActivity.LAUNCH_SOURCE_WIDGET_SHORTCUT);
            ShortcutInfoCompat.Builder builder = new ShortcutInfoCompat.Builder(this, "book_" + j);
            builder.setLongLabel(bookShortcutDetails.title);
            builder.setShortLabel(bookShortcutDetails.title);
            if (bookShortcutDetails.cover != null) {
                builder.setIcon(IconCompat.createWithBitmap(bookShortcutDetails.cover));
            } else {
                builder.setIcon(IconCompat.createWithResource(this, R.drawable.ic_app_shortcut_no_cover));
            }
            builder.setIntent(intentPutExtra);
            setResult(-1, ShortcutManagerCompat.createShortcutResultIntent(this, builder.build()));
            finish();
        });
    }

    public void onFragmentInvalid() {
        this.mHandler.post(() -> {
            Fragment fragmentNewInstance = BooksFragment.newInstance();
            getSupportFragmentManager().popBackStackImmediate((String) null, 1);
            switchToFragment(fragmentNewInstance, false);
        });
    }

    public void onFragmentInvalidatedDrawer() {
        this.mDrawer.refresh();
    }

    public void onFragmentRedirect(Fragment fragment) {
        switchToFragment(fragment);
    }

    public void onFragmentForceRefresh() {
        refreshBooks(true);
    }

    public void onFragmentSubmitSearch() {
        if (this.mSearchSubmitted) {
            return;
        }
        this.mSearchSubmitted = true;
        this.mSearchHistoryManager.submitQuery(this.mSearchQuery);
    }

    @Override
    public void onLibraryUpdateFoundBook() {
        if (this.mNoticeShown) {
            return;
        }
        showNotice();
    }

    @Override
    public void onLibraryUpdateAddedBook() {
        if (!this.mNoticeShown) {
            showNotice();
        }
        refreshBooksList();
        refreshDrawer();
    }

    @Override
    public void onLibraryUpdateDone(final LibraryUpdate.Result result) {
        Snackbar snackbar = this.mRefreshSnackbar;
        if (snackbar != null && this.mNoticeShown) {
            if (!snackbar.isShown()) {
                showNotice();
            }
            if (result.failedCount == 0) {
                if (result.successfulCount > 0) {
                    this.mRefreshSnackbar.setText(getResources().getQuantityString(R.plurals.library_updated_new, result.successfulCount, result.successfulCount));
                } else {
                    this.mRefreshSnackbar.setText(R.string.library_updated_none);
                }
            } else {
                this.mRefreshSnackbar.setText(R.string.library_update_failed);
                this.mRefreshSnackbar.setAction(R.string.library_update_failed_details, view -> LibraryUpdateReportDialog.newInstance(result).show(getSupportFragmentManager(), "library_update_report"));
            }
        }
        refreshBooksList();
        refreshDrawer();
        if (result.removedIds.size() > 0) {
            App.disableShortcutsForBooks(this, result.removedIds);
            App.refreshAppShortcuts(this);
        }
        if (this.mNoticeShown) {
            this.mHandler.postDelayed(this.mHideNoticeRunnable, 2000L);
        }
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long j) {
        if (adapterView == this.mRecentSearchesListView) {
            this.mSearchBarView.setQueryAndSubmit(this.mRecentSearchesAdapter.getQuery(i));
        }
    }

    private static class PrepareBookShortcutDetailsTask extends AsyncHelper.Task<BookShortcutDetails> {
        private final long mBookId;
        private final Context mContext;
        private final int mSize;

        public PrepareBookShortcutDetailsTask(Context context, long j, int i) {
            this.mContext = context;
            this.mBookId = j;
            this.mSize = i;
        }

        @Override
        public BookShortcutDetails run() {
            int iRound;
            int iRound2;
            BookShortcutDetails bookShortcutDetails = new BookShortcutDetails();
            Cursor cursorQuery = DatabaseProvider.getDatabase(this.mContext).query(BooksTable.TABLE_NAME, new String[]{BooksTable.COLUMN_TITLE, BooksTable.COLUMN_COVER}, "_id=?", new String[]{String.valueOf(this.mBookId)}, null, null, null);
            bookShortcutDetails.success = false;
            if (cursorQuery.moveToNext()) {
                bookShortcutDetails.title = cursorQuery.getString(0);
                String string = cursorQuery.getString(1);
                if (string != null) {
                    bookShortcutDetails.cover = BitmapFactory.decodeFile(string);
                    if (bookShortcutDetails.cover != null) {
                        int width = bookShortcutDetails.cover.getWidth();
                        int height = bookShortcutDetails.cover.getHeight();
                        if (width > height) {
                            float f = (float) height / width;
                            iRound2 = this.mSize;
                            iRound = Math.round(iRound2 * f);
                        } else {
                            float f2 = (float) width / height;
                            iRound = this.mSize;
                            iRound2 = Math.round(iRound * f2);
                        }
                        bookShortcutDetails.cover = Bitmap.createScaledBitmap(bookShortcutDetails.cover, iRound2, iRound, false);
                    }
                }
                bookShortcutDetails.success = true;
            }
            cursorQuery.close();
            return bookShortcutDetails;
        }
    }

    static class BookShortcutDetails {
        Bitmap cover;
        boolean success;
        String title;

        private BookShortcutDetails() {
        }
    }
}