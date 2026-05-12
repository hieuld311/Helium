package com.hieuld.helium;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.interpolator.view.animation.FastOutLinearInInterpolator;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hieuld.helium.annotations.AnnotationController;
import com.hieuld.helium.annotations.SelectionToolbarView;
import com.hieuld.helium.book.Book;
import com.hieuld.helium.book.EPubBook;
import com.hieuld.helium.book.SearchProvider;
import com.hieuld.helium.book.SearchResult;
import com.hieuld.helium.book.TocEntry;
import com.hieuld.helium.content.BookView;
import com.hieuld.helium.content.EPubBookView;
import com.hieuld.helium.content.FootNoteWebView;
import com.hieuld.helium.db.BooksTable;
import com.hieuld.helium.db.DatabaseProvider;
import com.hieuld.helium.exceptions.BookLoadException;
import com.hieuld.helium.exceptions.BookNotFoundException;
import com.hieuld.helium.exceptions.PermissionException;
import com.hieuld.helium.fonts.Font;
import com.hieuld.helium.fonts.Fonts;
import com.hieuld.helium.model.BookmarkManager;
import com.hieuld.helium.model.SearchHistoryManager;
import com.hieuld.helium.themes.AppThemeManager;
import com.hieuld.helium.themes.Theme;
import com.hieuld.helium.themes.ThemeManager;
import com.hieuld.helium.ui.RecentSearchesAdapter;
import com.hieuld.helium.util.AnimationUtils;
import com.hieuld.helium.util.AsyncHelper;
import com.hieuld.helium.util.HtmlCompat;
import com.hieuld.helium.util.ThemeUtils;
import com.hieuld.helium.util.Utils;
import com.hieuld.helium.widget.DisplayCutoutFrameLayout;
import com.hieuld.helium.widget.ScrimInsetsFrameLayout;
import com.hieuld.helium.widget.SearchBarView;
import com.hieuld.helium.widget.SystemBarsFrame;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

public class ReaderActivity extends BaseActivity implements View.OnClickListener, ReaderDrawerFragment.Listener, SeekBar.OnSeekBarChangeListener, View.OnSystemUiVisibilityChangeListener, SearchBarView.SearchBarListener, ReaderSearchResultsAdapter.OnClickListener, AdapterView.OnItemClickListener, SearchProvider.Callbacks {
    private static final int CHROME_HIDE_DELAY = 4000;
    private static final int CHROME_HIDE_DELAY_SEEK = 2000;
    public static final String EXTRA_BOOK_ID = "book_id";
    public static final String EXTRA_LAUNCH_SOURCE = "source";
    public static final String LAUNCH_SOURCE_APP_SHORTCUT = "app_shortcut";
    public static final String LAUNCH_SOURCE_INTENT = "intent";
    public static final String LAUNCH_SOURCE_WIDGET_SHORTCUT = "widget_shortcut";
    private static final long PAGE_REPEAT_MIN_INTERVAL = 200;
    private static final int REQUEST_SEARCH_VOICE = 1;
    private static final String STATE_BACK_STACK = "back_stack";
    private static final String STATE_DISPLAY_SETTINGS_OPEN = "display_settings_open";
    private static final String TAG = "ReaderActivity";
    private static final long UPDATE_BOOKMARK_STATUS_SCROLLING_THRESHOLD = 150;
    private static final boolean USE_LOCAL_NIGHT_MODE = Build.VERSION.SDK_INT > 16;

    private AnnotationController mAnnotationController;
    private Stack<BackStackEntry> mBackStack;
    private Book mBook;
    private int mBookColor;
    private DisplayCutoutFrameLayout mBookContainerView;
    private long mBookId;
    private String mBookTitle;
    private BookView mBookView;
    private boolean mBookmarkHere;
    private BookmarkManager mBookmarkManager;
    private boolean mBookmarkStatusUpdatePending;
    private TextView mChapterNameView;
    private boolean mChromeLocked;
    private boolean mChromeVisible;
    private boolean mDisableDrawerGesture;
    private DisplaySettingsFragment mDisplaySettingsFragment;
    private View mDisplaySettingsView;
    private boolean mDisplaySettingsVisible;
    private ReaderDrawerFragment mDrawer;
    private DrawerLayout mDrawerLayout;
    private View mFootNotePopupView;
    private TextView mFootNoteTitle;
    private float mFootNoteTitleDefaultSize;
    private FootNoteWebView mFootNoteWebView;
    private LinearLayout mFootNoteWebViewContainer;
    private Handler mHandler;
    private boolean mIsLoading;
    private long mLastPageChangeRepeat;
    private long mLoadStartTime;
    private ProgressBar mLoadingView;
    private LinearLayout mNavBarView;
    private View mOverlay;
    private boolean mOverlayVisible;
    private TextView mPageNumberView;
    private SeekBar mPageSeekView;
    private TextView mPaperPageNumberView;
    private Runnable mPendingDisplaySettingsFragmentUpdate;
    private boolean mPermissionsWorkaroundRequested;
    private boolean mPhotoLaunching;
    private boolean mPositionTouched;
    private SharedPreferences mPrefs;
    private RecentSearchesAdapter mRecentSearchesAdapter;
    private ListView mRecentSearchesListView;
    private SearchBarView mSearchBarView;
    private SearchHistoryManager mSearchHistoryManager;
    private SearchProvider mSearchProvider;
    private RecyclerView mSearchResultsView;
    private View mSearchUiView;
    private boolean mSearchUiVisible;
    private BackStackEntry mSeekBackEntry;
    private SelectionToolbarView mSelectionToolbarView;
    private ImageView mShowcasedImageView;
    private int mStatusBarColor;
    private Theme mTheme;
    private ThemeManager mThemeManager;
    private TocEntry mTocEntry;
    private SystemBarsFrame mToolbarContainer;
    private boolean mUseBookWidePages;
    private boolean mUserTrackingPage;
    private boolean mFullscreenEnabled = true;

    private Runnable mPreloadNextFootNoteRunnable = this::preloadNextFootNoteView;
    private Runnable mHideChromeRunnable = () -> setChromeVisible(false);
    private Runnable mUpdateBookmarkStatusRunnable = () -> {
        updateBookmarkStatus();
        mBookmarkStatusUpdatePending = false;
    };
    private AsyncHelper mAsync = new AsyncHelper();

    // The callbacks implementation extracted from the decompiled AnonymousClass6
    private final BookView.Callbacks mCallbacks = new BookView.Callbacks() {
        @Override
        public void onReadingProgressChanged() {
            if (mAnnotationController.isBusy() && mBookView.getFlow() == 1) mAnnotationController.cancelMaybe();
            if (mChromeVisible) {
                updateReadingProgress();
                if (mBookView.getFlow() == 1) updateBookmarkStatus();
                else if (!mBookmarkStatusUpdatePending) {
                    mHandler.postDelayed(mUpdateBookmarkStatusRunnable, 150L);
                    mBookmarkStatusUpdatePending = true;
                }
            }
        }

        @Override public void onPositionTouched() { mPositionTouched = true; }
        @Override public void onTocEntryChanged(TocEntry tocEntry) { updateTocEntry(tocEntry); }
        @Override public void onInitDone() { mAnnotationController.initRenderer(); }

        @Override
        public void onLoadStart(boolean z) {
            mIsLoading = true;
            mLoadStartTime = System.currentTimeMillis();
            mLoadingView.animate().cancel();
            mBookView.animate().cancel();
            mLoadingView.setAlpha(1.0f);
            mBookView.setVisibility(View.INVISIBLE);
            mLoadingView.setVisibility(View.VISIBLE);
            mShowcasedImageView.setVisibility(View.GONE);
            if (mFootNotePopupView.getVisibility() == View.VISIBLE) {
                mOverlay.setVisibility(View.GONE);
                mOverlayVisible = false;
                mFootNotePopupView.setVisibility(View.GONE);
                preloadNextFootNoteView();
            }
        }

        @Override
        public void onLoadDone() {
            updateReadingProgress();
            mBookView.setVisibility(View.VISIBLE);
            mBookView.setAlpha(0.0f);
            mLoadingView.animate().alpha(0.0f).setDuration(80L).setInterpolator(new FastOutLinearInInterpolator()).withEndAction(() -> {
                mLoadingView.setVisibility(View.GONE);
                mLoadingView.setAlpha(1.0f);
                mBookView.animate().alpha(1.0f).setDuration(300L).setInterpolator(new LinearOutSlowInInterpolator());
            });
            mIsLoading = false;
            if (!mUserTrackingPage) setChromeVisible(false);
        }

        @Override public void onLoadFailed() { showBookFailedToLoadDialog(); }
        @Override public void onScroll() { mAnnotationController.notifyScroll(); }

        @Override
        public void showFootNote(String str, String str2, final int i, final int i2) {
            if (mFootNoteWebView == null) preloadNextFootNoteView();
            setChromeVisible(false);
            mAnnotationController.cancel();
            mHandler.removeCallbacks(mPreloadNextFootNoteRunnable);
            mFootNoteTitle.setText(str);
            ((CardView) mFootNotePopupView.findViewById(R.id.footNoteCard)).setCardBackgroundColor(mTheme != null ? mTheme.backgroundColor | ViewCompat.MEASURED_STATE_MASK : -1);
            mFootNoteTitle.setTextColor(mTheme != null ? mTheme.textColor | ViewCompat.MEASURED_STATE_MASK : ContextCompat.getColor(ReaderActivity.this, R.color.black_high));
            mFootNoteWebView.load(str2);
            mFootNoteWebView.getSettings().setTextZoom(mPrefs.getInt("textSize", 100));
            mHandler.postDelayed(() -> {
                showOverlay();
                boolean z = i > mBookView.getHeight() / 2;
                RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) mFootNotePopupView.getLayoutParams();
                lp.leftMargin = Math.max(0, Math.min(mBookView.getWidth() - mFootNotePopupView.getWidth(), i2 - (mFootNotePopupView.getWidth() / 2)));
                if (z) { lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM); lp.bottomMargin = mBookView.getHeight() - i; lp.topMargin = 0; }
                else { lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0); lp.topMargin = i; lp.bottomMargin = 0; }
                mFootNotePopupView.setLayoutParams(lp);
                mFootNotePopupView.setAlpha(0.0f);
                mFootNotePopupView.setScaleX(1.0f); mFootNotePopupView.setScaleY(1.0f);
                mFootNotePopupView.setTranslationY(Utils.dpToPx(ReaderActivity.this, z ? 50 : -50));
                mFootNotePopupView.animate().alpha(1.0f).translationY(0.0f).setListener(null).setInterpolator(new LinearOutSlowInInterpolator()).setDuration(225L);
                mFootNotePopupView.setVisibility(View.VISIBLE);
            }, 100L);
        }

        @Override
        public void onPagePress(int i, int i2) {
            if (mAnnotationController.isBusy()) { mAnnotationController.cancel(); return; }
            if (mBookView.getFlow() == 1 && !mIsLoading && mPrefs.getBoolean("tap_pagination", false)) {
                long now = System.currentTimeMillis();
                if (now - mLastPageChangeRepeat < PAGE_REPEAT_MIN_INTERVAL) return;
                mLastPageChangeRepeat = now;
                float wRatio = (float) i / mBookView.getWidth();
                if (wRatio <= 0.3f) { advancePage(-1); return; }
                else if (wRatio >= 0.7f) { advancePage(1); return; }
            }
            setChromeVisible(!mChromeVisible);
        }

        @Override
        public void showPicture(Bitmap bitmap, int i, int i2, int i3, int i4) {
            PhotoActivity.sPhotoCarry = bitmap;
            Intent intent = new Intent(ReaderActivity.this, PhotoActivity.class);
            if (Build.VERSION.SDK_INT >= 21) {
                mShowcasedImageView.setImageBitmap(bitmap);
                mShowcasedImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                mShowcasedImageView.setVisibility(View.VISIBLE);
                mShowcasedImageView.setTranslationX(i);
                mShowcasedImageView.setTranslationY(i2);
                ViewGroup.LayoutParams lp = mShowcasedImageView.getLayoutParams();
                lp.width = i3; lp.height = i4;
                mShowcasedImageView.setLayoutParams(lp);
                mShowcasedImageView.getParent().requestLayout();
                mHandler.post(() -> startActivity(intent, ActivityOptions.makeSceneTransitionAnimation(ReaderActivity.this, mShowcasedImageView, "showcased_image").toBundle()));
            } else {
                mBookView.restorePictureShowcase(null);
                startActivity(intent, ActivityOptions.makeScaleUpAnimation(mShowcasedImageView, i, i2, i3, i4).toBundle());
            }
            mPhotoLaunching = true;
        }

        @Override public void loadExternalUrl(String str) { handleExternalUrl(str); }
        @Override public void pushBackStack() { ReaderActivity.this.pushBackStack(); }
        @Override public void onFeaturesChanged() { updateFeaturesForBookView(); }
    };

    private DisplaySettingsFragment.OnSettingChangedListener mDisplaySettingListener = new DisplaySettingsFragment.OnSettingChangedListener() {
        @Override
        public void onTextSizeChanged(int i) {
            if (mBookView != null) mBookView.setTextSize(i);
            mFootNoteTitle.setTextSize(0, mFootNoteTitleDefaultSize * (i / 100.0f));
        }

        @Override
        public void onMarginChanged(int i) {
            if (mBookView != null) mBookView.setMargin(i);
        }

        @Override
        public void onFontChanged(Font font) {
            if (mBookView != null) mBookView.setFont(font);
        }

        @Override
        public void onLineSpacingChanged(float f) {
            if (mBookView != null) mBookView.setLineSpacing(f);
        }

        @Override
        public void onTextAlignChanged(int i) {
            if (mBookView != null) mBookView.setTextAlign(i);
        }

        @Override
        public void onBrightnessChanged() {
            updateScreenSettings();
        }

        @Override
        public void onBrightnessPreview(float f) {
            setScreenBrightness(f, false);
        }

        @Override
        public void onThemeChanged() {
            mTheme = mThemeManager.getTheme();
            setTheme(mTheme);
            if (USE_LOCAL_NIGHT_MODE) {
                getDelegate().setLocalNightMode(mTheme != null && mTheme.darkChrome ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
            }
        }

        @Override
        public void onFlowStyleChanged(String str) {
            if (mBookView != null) {
                switch (str) {
                    case "scrolled": mBookView.setPreferredFlow(2); break;
                    case "paged": mBookView.setPreferredFlow(1); break;
                    case "auto": default: mBookView.setPreferredFlow(0); break;
                }
            }
        }
    };

    private BookmarkManager.BookmarkMatcher mBookmarkMatcher = str -> mBookView.isAtPosition(str);

    @Override
    public void onSearchQueryChanged(String str) { }

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        if (checkCriticalPermissions()) {
            setContentView(R.layout.activity_reader);
            setNoWindowBackground();
            setUseScreenSettings(true);
            setDisplayUpButton(true);
            this.mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
            this.mFullscreenEnabled = this.mPrefs.getBoolean("fullscreen", true) && Build.VERSION.SDK_INT >= 19;
            this.mUseBookWidePages = this.mPrefs.getBoolean("book_wide_pages", true);
            this.mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
            this.mDrawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
                @Override
                public void onDrawerSlide(View view, float f) {
                    super.onDrawerSlide(view, f);
                    if (mFullscreenEnabled) {
                        setChromeLocked(f > 0.0f);
                    }
                }
                @Override
                public void onDrawerClosed(View view) {
                    super.onDrawerClosed(view);
                    mDrawerLayout.setDrawerLockMode(mDisableDrawerGesture ? DrawerLayout.LOCK_MODE_LOCKED_CLOSED : DrawerLayout.LOCK_MODE_UNLOCKED);
                }
            });
            this.mDrawerLayout.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            Window window = getWindow();
            if (this.mFullscreenEnabled) {
                this.mDrawerLayout.setOnSystemUiVisibilityChangeListener(this);
                if (Build.VERSION.SDK_INT >= 28) {
                    WindowManager.LayoutParams attributes = window.getAttributes();
                    attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                    window.setAttributes(attributes);
                }
            } else {
                findViewById(R.id.content_frame).setFitsSystemWindows(true);
                findViewById(R.id.content_high_frame).setFitsSystemWindows(true);
            }
            if (Build.VERSION.SDK_INT >= 21) {
                window.setNavigationBarColor(0);
            }
            if (Build.VERSION.SDK_INT >= 27) {
                View decorView = window.getDecorView();
                decorView.setSystemUiVisibility(decorView.getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
            }
            if (Build.VERSION.SDK_INT >= 29) {
                window.setStatusBarContrastEnforced(false);
                window.setNavigationBarContrastEnforced(false);
            }
            if (!this.mFullscreenEnabled) {
                this.mDrawerLayout.setStatusBarBackgroundColor(ViewCompat.MEASURED_STATE_MASK);
            }
            this.mDrawer = (ReaderDrawerFragment) getSupportFragmentManager().findFragmentById(R.id.drawer);
            this.mDrawer.setListener(this);
            this.mToolbarContainer = (SystemBarsFrame) findViewById(R.id.toolbar_container);
            this.mStatusBarColor = ContextCompat.getColor(this, R.color.app_primary_dark);
            this.mToolbarContainer.setSystemBarsBackgroundColor(this.mStatusBarColor);
            this.mBookContainerView = (DisplayCutoutFrameLayout) findViewById(R.id.book_container);
            this.mBookContainerView.setInsetCutout(this.mFullscreenEnabled ? 2 : 0);
            ((DisplayCutoutFrameLayout) findViewById(R.id.content_high_cutout_frame)).setInsetCutout(this.mFullscreenEnabled ? 1 : 0);
            this.mOverlay = findViewById(R.id.overlay);
            this.mOverlay.setVisibility(View.GONE);
            this.mOverlay.setOnClickListener(this);
            this.mOverlay.setSoundEffectsEnabled(false);
            ViewCompat.setTranslationZ(findViewById(R.id.selection_toolbar_layout), 100.0f);
            ViewCompat.setTranslationZ(findViewById(R.id.popup_layout), 100.0f);
            this.mSearchUiView = findViewById(R.id.search_ui);
            this.mSearchUiView.setVisibility(View.INVISIBLE);
            ViewCompat.setTranslationZ(this.mSearchUiView, 100.0f);
            this.mSearchBarView = (SearchBarView) findViewById(R.id.search_bar);
            this.mSearchBarView.setListener(this);
            ViewCompat.setElevation(this.mSearchBarView, Utils.dpToPx(this, 4));
            this.mSearchResultsView = (RecyclerView) findViewById(R.id.search_results);
            this.mSearchResultsView.setHasFixedSize(true);
            this.mSearchResultsView.setLayoutManager(new LinearLayoutManager(this, RecyclerView.VERTICAL, false));
            this.mRecentSearchesListView = (ListView) findViewById(R.id.recent_searches);
            this.mRecentSearchesListView.setOnItemClickListener(this);
            this.mRecentSearchesAdapter = new RecentSearchesAdapter(this);
            this.mRecentSearchesListView.setAdapter(this.mRecentSearchesAdapter);
            this.mSearchHistoryManager = new SearchHistoryManager(DatabaseProvider.getDatabase(this), "book");
            this.mFootNotePopupView = findViewById(R.id.footNotePopup);
            this.mFootNotePopupView.setVisibility(View.INVISIBLE);
            this.mFootNoteTitle = (TextView) findViewById(R.id.footNoteTitle);
            this.mFootNoteTitleDefaultSize = this.mFootNoteTitle.getTextSize();
            this.mFootNoteWebViewContainer = (LinearLayout) findViewById(R.id.footNoteWebViewContainer);
            this.mLoadingView = (ProgressBar) findViewById(R.id.loading);
            this.mLoadingView.setVisibility(View.VISIBLE);
            this.mNavBarView = (LinearLayout) findViewById(R.id.navBar);
            this.mNavBarView.setVisibility(View.INVISIBLE);
            ViewCompat.setElevation(this.mNavBarView, Utils.dpToPx(this, 10));
            this.mChapterNameView = (TextView) findViewById(R.id.chapterName);
            this.mPageNumberView = (TextView) findViewById(R.id.pageNumber);
            this.mPaperPageNumberView = (TextView) findViewById(R.id.paperPageNumber);
            this.mPageSeekView = (SeekBar) findViewById(R.id.pageSeek);
            this.mPageSeekView.setOnSeekBarChangeListener(this);
            this.mShowcasedImageView = (ImageView) findViewById(R.id.showcasedImage);
            this.mDisplaySettingsView = findViewById(R.id.displaySettingsDropView);
            this.mDisplaySettingsView.setVisibility(View.INVISIBLE);
            this.mDisplaySettingsFragment = (DisplaySettingsFragment) getSupportFragmentManager().findFragmentById(R.id.displaySettingsDropView);
            if (this.mDisplaySettingsFragment != null) {
                this.mDisplaySettingsFragment.setOnSettingChangedListener(this.mDisplaySettingListener);
            }
            final ScrimInsetsFrameLayout scrimInsetsFrameLayout = (ScrimInsetsFrameLayout) findViewById(R.id.drawer_scrim);
            final int topMargin = ((ViewGroup.MarginLayoutParams) this.mDisplaySettingsView.getLayoutParams()).topMargin;
            final int rightMargin = ((ViewGroup.MarginLayoutParams) this.mDisplaySettingsView.getLayoutParams()).rightMargin;
            scrimInsetsFrameLayout.setOnInsetsCallback(rect -> {
                if (ViewCompat.getLayoutDirection(mDrawerLayout) == ViewCompat.LAYOUT_DIRECTION_LTR) rect.left = 0; else rect.right = 0;
                mDrawer.setInset(rect.top, rect.bottom);
                scrimInsetsFrameLayout.setPadding(0, 0, rect.right, 0);
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) mDisplaySettingsView.getLayoutParams();
                params.topMargin = topMargin + rect.top;
                params.rightMargin = rightMargin + rect.right;
                mDisplaySettingsView.setLayoutParams(params);
            });
            this.mHandler = new Handler();
            this.mHandler.postDelayed(this.mPreloadNextFootNoteRunnable, 100L);
            this.mBookId = getIntent().getLongExtra("book_id", -1L);
            SQLiteDatabase database = DatabaseProvider.getDatabase(this);
            this.mAnnotationController = new AnnotationController(this, database, this.mBookId);
            this.mAnnotationController.setListener(() -> mDrawer.updateAnnotations());
            setTheme(this.mTheme);
            this.mBookmarkManager = new BookmarkManager(database);
            this.mChromeVisible = true;
            if (bundle != null) {
                restoreSavedInstanceState(bundle);
            } else {
                this.mBackStack = new Stack<>();
            }
            Cursor cursorQuery = database.query(BooksTable.TABLE_NAME, new String[]{BooksTable.COLUMN_TITLE, "file_path", BooksTable.COLUMN_CURRENT_POSITION, BooksTable.COLUMN_COVER_BACKGROUND, "hidden"}, "_id=?", new String[]{String.valueOf(this.mBookId)}, null, null, null);
            if (cursorQuery.moveToFirst()) {
                this.mBookTitle = cursorQuery.getString(0);
                String filePath = cursorQuery.getString(1);
                final String position = cursorQuery.getString(2);
                if (cursorQuery.getInt(4) > 0) {
                    cursorQuery.close();
                    finish();
                    return;
                }
                setTitle(this.mBookTitle);
                int color = cursorQuery.getInt(3);
                this.mBookColor = color != 0 ? color : -1;
                applyChromeColor();
                this.mAsync.run(new BookLoadTask(filePath), obj -> {
                    BookLoadResult result = (BookLoadResult) obj;
                    if (result.exception != null) {
                        if (isFinishing()) return;
                        if (result.exception instanceof BookNotFoundException) {
                            showBookNotFoundDialog(mBookTitle);
                        } else if (result.exception instanceof PermissionException && Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageLegacy() && !Environment.isExternalStorageManager()) {
                            showPermissionBugDialog();
                        } else {
                            showBookFailedToLoadDialog();
                        }
                        return;
                    }
                    mBook = result.book;
                    mSearchProvider = mBook.getSearchProvider();
                    mDrawer.setBook(mBook, mBookId);
                    supportInvalidateOptionsMenu();
                    loadBookView(position);
                    initSelectionToolbarView();
                });
                cursorQuery.close();
                if (bundle == null) {
                    App.reportAppShortcutUsed(this, "book_" + this.mBookId);
                }
                return;
            }
            showNoSuchBookDialog();
            cursorQuery.close();
        }
    }

    private void applyChromeColor() {
        int color;
        if (ThemeUtils.isInDarkMode(this) || (this.mTheme != null && this.mTheme.darkChrome)) {
            color = ContextCompat.getColor(this, R.color.reader_dark_chrome_color);
        } else {
            color = this.mBookColor == -1 ? ContextCompat.getColor(this, R.color.app_primary) : this.mBookColor;
        }
        getToolbar().setBackgroundColor(color);
        this.mNavBarView.setBackgroundColor(color);
        int darkRgb = Color.rgb((int) (Color.red(color) * 0.8f), (int) (Color.green(color) * 0.8f), (int) (Color.blue(color) * 0.8f));
        this.mStatusBarColor = darkRgb;
        this.mToolbarContainer.setSystemBarsBackgroundColor(darkRgb);
        if (Build.VERSION.SDK_INT >= 21) {
            setTaskDescription(new ActivityManager.TaskDescription(this.mBookTitle, null, color));
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle bundle) {
        super.onSaveInstanceState(bundle);
        BackStackTransport transport = new BackStackTransport();
        transport.entries = this.mBackStack;
        bundle.putSerializable(STATE_BACK_STACK, transport);
        bundle.putBoolean(STATE_DISPLAY_SETTINGS_OPEN, this.mDisplaySettingsVisible);
    }

    @Override
    protected void onRestoreInstanceState(Bundle bundle) {
        super.onRestoreInstanceState(bundle);
        if (this.mDrawerLayout.isDrawerOpen(GravityCompat.END)) {
            setChromeLocked(true);
        }
    }

    private void restoreSavedInstanceState(Bundle bundle) {
        BackStackTransport transport = (BackStackTransport) bundle.getSerializable(STATE_BACK_STACK);
        if (transport != null) this.mBackStack = transport.entries;
        if (bundle.getBoolean(STATE_DISPLAY_SETTINGS_OPEN)) {
            this.mDisplaySettingsVisible = true;
            this.mDisplaySettingsView.setVisibility(View.VISIBLE);
            showOverlayInstantly();
            this.mChromeVisible = false;
            this.mToolbarContainer.setAlpha(0.0f);
            setToolbarVisible(false);
        }
    }

    private void showBookNotFoundDialog(String str) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.book_not_found_error_title)
                .setMessage(HtmlCompat.fromHtml(getString(R.string.book_not_found_error_message, str)))
                .setPositiveButton(R.string.book_not_found_ok, (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    private void showNoSuchBookDialog() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.no_such_book_error_message)
                .setPositiveButton(R.string.no_such_book_error_ok, (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    public void showBookFailedToLoadDialog() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.book_failed_error_message)
                .setNegativeButton(R.string.book_failed_report, (dialog, which) -> startFeedback(this::finish))
                .setPositiveButton(R.string.book_failed_ok, (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    private void showPermissionBugDialog() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.permissions_title)
                .setNegativeButton(R.string.permissions_cancel, (dialog, which) -> finish())
                .setPositiveButton(R.string.permissions_continue, (dialog, which) -> {
                    try {
                        startActivity(new Intent("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION").setData(Uri.parse("package:" + getPackageName())));
                    } catch (ActivityNotFoundException unused) {
                        startActivity(new Intent("android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION"));
                    }
                    mPermissionsWorkaroundRequested = true;
                })
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    private void loadBookView(String str) {
        if (this.mBook instanceof EPubBook) {
            this.mBookView = new EPubBookView(this, this.mBookId, this.mBook, this.mCallbacks);
            this.mBookContainerView.addView(this.mBookView);
            String flow = this.mPrefs.getString("page_flow", AppThemeManager.VALUE_AUTO);
            this.mBookView.setPreferredFlow(flow.equals("scrolled") ? 2 : (flow.equals("paged") ? 1 : 0));
            this.mBookView.setPosition(str);
            this.mLoadStartTime = System.currentTimeMillis();
            this.mIsLoading = true;
            if (this.mFootNoteWebView != null && this.mBookView instanceof BookView.FootNoteProvider) {
                this.mFootNoteWebView.setProvider((BookView.FootNoteProvider) this.mBookView);
            }
            Log.d(TAG, "Begin loading book");
            return;
        }
        throw new RuntimeException("Unknown book class.");
    }

    public void updateFeaturesForBookView() {
        if (this.mBookView.supportsFeature(BookView.FEATURE_CUSTOM_TEXT_SIZE)) {
            int size = this.mPrefs.getInt("textSize", 100);
            this.mBookView.setTextSize(size);
            this.mFootNoteTitle.setTextSize(0, this.mFootNoteTitleDefaultSize * (size / 100.0f));
        } else {
            this.mFootNoteTitle.setTextSize(0, this.mFootNoteTitleDefaultSize);
        }
        this.mBookView.setMargin(this.mPrefs.getInt("margin", getResources().getInteger(R.integer.default_margin_pc)));
        setTheme(this.mTheme);
        if (this.mBookView.supportsFeature(BookView.FEATURE_CUSTOM_LINE_SPACING)) {
            this.mBookView.setLineSpacing(this.mPrefs.getFloat("line_spacing", 1.5f));
        }
        if (this.mBookView.supportsFeature(BookView.FEATURE_TEXT_ALIGN)) {
            this.mBookView.setTextAlign(this.mPrefs.getInt("text_align", 0));
        }
        String language = this.mBook.getLanguage();
        if (language == null) language = "en";
        List<Font> compatibleFonts = Fonts.getCompatibleFonts(language);
        Font fontByName = Fonts.getFontByName(this.mPrefs.getString("font", null));
        if (compatibleFonts.contains(fontByName)) {
            this.mBookView.setFont(fontByName);
        }
        this.mAnnotationController.setContext(this.mBookView.getContextString(), this.mBookView.createAnnotationRenderer(this.mAnnotationController));

        final String fLang = language;
        Runnable updateDisplaySettings = () -> {
            mDisplaySettingsFragment.setFixedLayout(mBookView.isFixedLayout());
            mDisplaySettingsFragment.setLanguage(fLang);
            mDisplaySettingsFragment.setCurrentFont(fontByName);
            mDisplaySettingsFragment.setFeatures(mBookView.getFeatures());
        };

        if (this.mDisplaySettingsFragment != null) {
            updateDisplaySettings.run();
        } else {
            this.mPendingDisplaySettingsFragmentUpdate = updateDisplaySettings;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.reader, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.search).setVisible(this.mBook == null || this.mSearchProvider != null);
        menu.findItem(R.id.goto_paper_page).setVisible(this.mBook != null && this.mBook.getPaperPages().size() > 0);
        MenuItem bookmarkItem = menu.findItem(R.id.bookmark);
        bookmarkItem.setIcon(this.mBookmarkHere ? R.drawable.ic_action_remove_bookmark : R.drawable.ic_action_add_bookmark);
        bookmarkItem.setTitle(this.mBookmarkHere ? R.string.action_remove_bookmark : R.string.action_add_bookmark);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        int itemId = menuItem.getItemId();
        if (itemId == R.id.bookmark) {
            if (this.mBookView == null) return true;
            if (this.mBookmarkManager.findBookmark(this.mBookId, this.mBookmarkMatcher) == BookmarkManager.NOT_FOUND) {
                this.mBookmarkManager.insertBookmark(this.mBookId, this.mBookView.getPosition(), getCurrentSectionTitle().trim(), this.mBookView.getPage());
                this.mBookmarkHere = true;
            } else {
                this.mBookmarkManager.deleteBookmark(this.mBookId, this.mBookmarkMatcher);
                this.mBookmarkHere = false;
            }
            this.mDrawer.updateBookmarks();
            invalidateOptionsMenu();
            return true;
        } else if (itemId == R.id.displaySettings) {
            setDisplaySettingVisible(true);
            return true;
        } else if (itemId == R.id.goto_paper_page) {
            showPaperPageDialog();
            return true;
        } else if (itemId == R.id.search) {
            showSearchUi();
            return true;
        } else if (itemId == R.id.toc) {
            if (this.mDisableDrawerGesture)
                this.mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
            this.mDrawerLayout.openDrawer(GravityCompat.END);
            return true;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= 30 && this.mPermissionsWorkaroundRequested) {
            if (Environment.isExternalStorageManager()) { recreate(); return; }
            else { finish(); return; }
        }
        if (Build.VERSION.SDK_INT >= 19 && this.mFullscreenEnabled != this.mPrefs.getBoolean("fullscreen", true)) {
            recreate(); return;
        }
        boolean widePages = this.mPrefs.getBoolean("book_wide_pages", true);
        if (this.mUseBookWidePages != widePages) {
            this.mUseBookWidePages = widePages;
            updateReadingProgress();
        }
        this.mDisableDrawerGesture = this.mPrefs.getBoolean("disable_drawer_gesture", false);
        this.mDrawerLayout.setDrawerLockMode(this.mDisableDrawerGesture ? 1 : 0);
        if (this.mPhotoLaunching) {
            this.mBookView.restorePictureShowcase(() -> {
                mShowcasedImageView.setVisibility(View.GONE);
                mShowcasedImageView.setImageBitmap(null);
            });
            this.mPhotoLaunching = false;
        }
        if (!this.mFullscreenEnabled || this.mChromeVisible || isSystemUiLocked()) return;
        this.mHandler.post(() -> setSystemUiVisible(false));
    }

    @Override
    protected void onPause() {
        super.onPause();
        SQLiteDatabase database = DatabaseProvider.getDatabase(this);
        ContentValues contentValues = new ContentValues();
        if (this.mBookView != null && !this.mIsLoading) {
            contentValues.put(BooksTable.COLUMN_CURRENT_POSITION, this.mBookView.getPosition());
            if (this.mPositionTouched) {
                contentValues.put(BooksTable.COLUMN_CURRENT_POSITION_TIMESTAMP, System.currentTimeMillis());
            }
        }
        contentValues.put(BooksTable.COLUMN_LAST_OPEN_DATE, System.currentTimeMillis());
        database.update(BooksTable.TABLE_NAME, contentValues, "_id=?", new String[]{String.valueOf(this.mBookId)});
        App.refreshAppShortcuts(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (this.mHandler != null) this.mHandler.removeCallbacksAndMessages(null);
        if (this.mSearchProvider != null) this.mSearchProvider.cancelSearch();
        if (this.mBookView != null) {
            this.mBookContainerView.removeView(this.mBookView);
            this.mBookView.release();
        }
        if (this.mFootNoteWebView != null) {
            this.mFootNoteWebViewContainer.removeView(this.mFootNoteWebView);
            this.mFootNoteWebView.destroy();
        }
    }

    private void inflateDisplaySettings() {
        if (this.mDisplaySettingsFragment != null) return;
        this.mDisplaySettingsFragment = new DisplaySettingsFragment();
        this.mDisplaySettingsFragment.setOnSettingChangedListener(this.mDisplaySettingListener);
        getSupportFragmentManager().beginTransaction().add(R.id.displaySettingsDropView, this.mDisplaySettingsFragment).commitNow();
        if (this.mPendingDisplaySettingsFragmentUpdate != null) {
            this.mPendingDisplaySettingsFragmentUpdate.run();
            this.mPendingDisplaySettingsFragmentUpdate = null;
        }
    }

    private void setDisplaySettingVisible(boolean z) {
        if (z == this.mDisplaySettingsVisible) return;
        this.mDisplaySettingsVisible = z;
        if (z) inflateDisplaySettings();
        this.mDisplaySettingsView.setAlpha(z ? 0.0f : 1.0f);
        ViewPropertyAnimator animator = this.mDisplaySettingsView.animate().alpha(z ? 1.0f : 0.0f).setDuration(PAGE_REPEAT_MIN_INTERVAL).setInterpolator(new FastOutSlowInInterpolator());
        animator.setListener(z ? AnimationUtils.visibleBefore(this.mDisplaySettingsView) : AnimationUtils.invisibleAfter(this.mDisplaySettingsView));
        if (z) {
            showOverlay();
            if (this.mFullscreenEnabled && !this.mChromeVisible) setSystemUiVisible(true);
            setChromeVisible(false);
        } else {
            hideOverlay();
            if (this.mFullscreenEnabled) setSystemUiVisible(false);
        }
    }

    public void setChromeVisible(final boolean z) {
        if (z != this.mChromeVisible && (!this.mChromeLocked || z)) {
            if (z && this.mBookView != null && !this.mIsLoading) {
                updateReadingProgress();
                updateBookmarkStatus();
            }
            this.mToolbarContainer.animate().alpha(z ? 1.0f : 0.0f).setDuration(PAGE_REPEAT_MIN_INTERVAL).setInterpolator(new DecelerateInterpolator()).setListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationStart(Animator animator) { if (z) setToolbarVisible(true); }
                @Override public void onAnimationEnd(Animator animator) { setToolbarVisible(z); }
            });
            this.mNavBarView.animate().translationY(z ? 0.0f : Utils.dpToPx(this, 16)).setDuration(PAGE_REPEAT_MIN_INTERVAL).setInterpolator(new DecelerateInterpolator())
                    .setListener(z ? AnimationUtils.visibleBefore(this.mNavBarView) : AnimationUtils.invisibleAfter(this.mNavBarView));
            if (this.mFullscreenEnabled && !isSystemUiLocked()) {
                setSystemUiVisible(z);
            }
            this.mChromeVisible = z;
            if (z) {
                this.mHandler.removeCallbacks(this.mHideChromeRunnable);
                this.mHandler.postDelayed(this.mHideChromeRunnable, CHROME_HIDE_DELAY);
            }
        }
    }

    private boolean isSystemUiLocked() {
        return this.mDisplaySettingsVisible || this.mSearchUiVisible;
    }

    public void setChromeLocked(boolean z) {
        this.mChromeLocked = z;
        if (!this.mChromeVisible && z) {
            setChromeVisible(true);
            this.mHandler.removeCallbacks(this.mHideChromeRunnable);
        } else if (this.mChromeVisible && !z) {
            setChromeVisible(false);
        }
    }

    private void setSystemUiVisible(boolean z) {
        this.mDrawerLayout.setSystemUiVisibility(!z ? 3846 : 1792);
    }

    public void showOverlay() {
        Utils.animateBackgroundToColor(this.mOverlay, 1996488704);
        this.mOverlay.setVisibility(View.VISIBLE);
        this.mOverlayVisible = true;
    }

    private void showOverlayInstantly() {
        this.mOverlay.setBackgroundColor(1996488704);
        this.mOverlay.setVisibility(View.VISIBLE);
        this.mOverlayVisible = true;
    }

    private void hideOverlay() {
        Utils.animateBackgroundToColor(this.mOverlay, 0);
        this.mHandler.postDelayed(() -> mOverlay.setVisibility(View.GONE), PAGE_REPEAT_MIN_INTERVAL);
        this.mOverlayVisible = false;
    }

    private void showSearchUi() {
        if (this.mSearchUiVisible || this.mSearchProvider == null) return;
        this.mSearchUiVisible = true;
        Animator animator = buildSearchUiAnimation(true);
        animator.setDuration(500L);
        animator.setInterpolator(new FastOutSlowInInterpolator());
        animator.addListener(AnimationUtils.visibleBefore(this.mSearchUiView));
        animator.start();
        this.mSearchBarView.activate();
        this.mSearchResultsView.setAdapter(null);
        this.mRecentSearchesListView.animate().cancel();
        this.mRecentSearchesListView.setVisibility(View.VISIBLE);
        this.mRecentSearchesListView.setAlpha(1.0f);
        this.mRecentSearchesAdapter.setQueries(this.mSearchHistoryManager.getQueries());
        if (this.mFullscreenEnabled && !this.mChromeVisible) setSystemUiVisible(true);
        if (Build.VERSION.SDK_INT >= 23 && !ThemeUtils.isInDarkMode(this)) {
            this.mSearchUiView.setSystemUiVisibility(Build.VERSION.SDK_INT >= 26 ? 8208 : 8192);
        }
    }

    private void hideSearchUi() {
        if (!this.mSearchUiVisible) return;
        this.mSearchUiVisible = false;
        Animator animator = buildSearchUiAnimation(false);
        animator.setDuration(300L);
        animator.setInterpolator(new FastOutSlowInInterpolator());
        animator.addListener(AnimationUtils.goneAfter(this.mSearchUiView));
        animator.start();
        this.mSearchBarView.deactivate();
        this.mSearchProvider.cancelSearch();
        if (this.mFullscreenEnabled && !this.mChromeVisible) setSystemUiVisible(false);
        if (Build.VERSION.SDK_INT >= 23 && !ThemeUtils.isInDarkMode(this)) {
            this.mSearchUiView.setSystemUiVisibility(0);
        }
    }

    private Animator buildSearchUiAnimation(boolean z) {
        int height;
        int width;
        Toolbar toolbar = getToolbar();
        int[] iArr = new int[2];
        toolbar.getLocationInWindow(iArr);
        View searchIcon = toolbar.findViewById(R.id.search);
        if (searchIcon != null) {
            int[] iArr2 = new int[2];
            searchIcon.getLocationInWindow(iArr2);
            width = iArr2[0] + (searchIcon.getWidth() / 2);
            height = iArr2[1] + (searchIcon.getHeight() / 2);
        } else {
            width = toolbar.getWidth();
            height = (toolbar.getHeight() / 2) + iArr[1];
        }
        float maxRadius = (float) Math.hypot(width, this.mSearchUiView.getHeight() - height);
        return AnimationUtils.createCircularReveal(this.mSearchUiView, width, height, z ? 0.0f : maxRadius, z ? maxRadius : 0.0f);
    }

    private void showRecentSearches() {
        this.mRecentSearchesListView.animate().cancel();
        this.mRecentSearchesListView.setVisibility(View.VISIBLE);
        this.mRecentSearchesListView.setAlpha(0.0f);
        this.mRecentSearchesListView.animate().alpha(1.0f).setDuration(PAGE_REPEAT_MIN_INTERVAL).setListener(null).start();
        this.mRecentSearchesAdapter.setQueries(this.mSearchHistoryManager.getQueries());
    }

    private void hideRecentSearches() {
        this.mRecentSearchesListView.animate().alpha(0.0f).setDuration(PAGE_REPEAT_MIN_INTERVAL).setListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animator) { mRecentSearchesListView.setVisibility(View.GONE); }
        }).start();
    }

    public void preloadNextFootNoteView() {
        if (this.mFootNoteWebView != null) {
            this.mFootNoteWebViewContainer.removeView(this.mFootNoteWebView);
            this.mFootNoteWebView.destroy();
        }
        this.mFootNoteWebView = new FootNoteWebView(this, str -> handleFootnoteLink(str));
        if (this.mBookView instanceof BookView.FootNoteProvider) {
            this.mFootNoteWebView.setProvider((BookView.FootNoteProvider) this.mBookView);
        }
        this.mFootNoteWebView.setBackgroundColor(0);
        this.mFootNoteWebViewContainer.addView(this.mFootNoteWebView, new ViewGroup.LayoutParams(-1, -2));
    }

    public void handleFootnoteLink(String str) {
        if (!SettingsHtmlActivity.EXTRA_FILE.equals(Uri.parse(str).getScheme())) {
            handleExternalUrl(str);
        } else {
            ((BookView.FootNoteProvider) this.mBookView).onFootNoteNavigation(str);
            onClick(this.mOverlay);
        }
    }

    @Override
    public void onClick(View view) {
        if (view == this.mOverlay && this.mOverlayVisible) {
            hideOverlay();
            if (this.mFootNotePopupView.getVisibility() == View.VISIBLE) {
                this.mHandler.removeCallbacks(this.mPreloadNextFootNoteRunnable);
                this.mHandler.postDelayed(this.mPreloadNextFootNoteRunnable, 300L);
                this.mFootNotePopupView.animate().alpha(0.0f).scaleX(0.75f).scaleY(0.75f).setInterpolator(new FastOutLinearInInterpolator()).setDuration(195L)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override public void onAnimationEnd(Animator animator) { mFootNotePopupView.setVisibility(View.INVISIBLE); }
                        });
            }
            if (this.mDisplaySettingsVisible) {
                setDisplaySettingVisible(false);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (this.mDrawerLayout.isDrawerOpen(GravityCompat.END)) {
            this.mDrawerLayout.closeDrawer(GravityCompat.END);
        } else if (this.mSearchUiVisible) {
            hideSearchUi();
        } else if (this.mOverlayVisible) {
            onClick(this.mOverlay);
        } else if (!popBackStack()) {
            super.onBackPressed();
        }
    }

    @Override
    public void onDrawerSelectedChapter(TocEntry tocEntry) {
        this.mDrawerLayout.closeDrawer(GravityCompat.END);
        this.mBookView.jumpToToc(tocEntry.url);
    }

    @Override
    public void onDrawerSelectedAnnotation(long j, String str, String str2) {
        this.mDrawerLayout.closeDrawer(GravityCompat.END);
        pushBackStack();
        this.mBookView.jumpToAnnotation(j, str, str2);
    }

    @Override
    public void onDrawerSelectedBookmark(String str) {
        this.mDrawerLayout.closeDrawer(GravityCompat.END);
        pushBackStack();
        this.mBookView.setPosition(str);
    }

    @Override
    public void onDrawerInvalidatedBookmark() {
        updateBookmarkStatus();
    }

    @Override
    public void onDrawerDeleteAnnotation(long j) {
        this.mAnnotationController.deleteAnnotation(j);
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int i, boolean z) {
        if (z && seekBar == this.mPageSeekView && this.mBookView != null) {
            if (this.mBookView.getFlow() == 1 || (this.mBookView.getFlow() == 2 && this.mUseBookWidePages)) {
                if (this.mUseBookWidePages) this.mBookView.setPage(i, true);
                else this.mBookView.setLegacyPage(i, true);
            } else {
                this.mBookView.setScrollPosition(i / 100.0f, false);
            }
            updateReadingProgress();
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        if (seekBar != this.mPageSeekView || this.mBookView == null) return;
        if (!this.mChromeVisible) setChromeVisible(true);
        this.mHandler.removeCallbacks(this.mHideChromeRunnable);
        this.mUserTrackingPage = true;
        this.mSeekBackEntry = buildBackStackEntry();
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        if (seekBar != this.mPageSeekView || this.mBookView == null) return;
        this.mHandler.postDelayed(this.mHideChromeRunnable, CHROME_HIDE_DELAY_SEEK);
        this.mUserTrackingPage = false;
        if (this.mSeekBackEntry != null) {
            this.mBackStack.push(this.mSeekBackEntry);
            this.mSeekBackEntry = null;
        }
    }

    private String getCurrentSectionTitle() {
        if (this.mTocEntry != null) return this.mTocEntry.title;
        String title = this.mBookView.getTitle();
        return (title == null || title.trim().isEmpty()) ? this.mBook.getTitle() : title;
    }

    public void updateTocEntry(TocEntry tocEntry) {
        this.mTocEntry = tocEntry;
        String strTrim = getCurrentSectionTitle().trim();
        this.mAnnotationController.setSectionTitle(strTrim);
        this.mChapterNameView.setText(strTrim);
        this.mDrawer.setSelectedChapter(tocEntry);
    }

    public void pushBackStack() {
        if (this.mBookView != null) this.mBackStack.push(buildBackStackEntry());
    }

    private BackStackEntry buildBackStackEntry() {
        BackStackEntry entry = new BackStackEntry();
        entry.position = this.mBookView.getPosition();
        return entry;
    }

    private boolean popBackStack() {
        if (this.mBackStack.isEmpty() || this.mBookView == null) return false;
        this.mBookView.setPosition(this.mBackStack.pop().position);
        return true;
    }

    private void showPaperPageDialog() {
        View viewInflate = LayoutInflater.from(this).inflate(R.layout.dialog_paper_page_number, null);
        final EditText editText = (EditText) viewInflate.findViewById(R.id.page);
        ((TextView) viewInflate.findViewById(R.id.page_count)).setText(getString(R.string.goto_paper_page_dialog_count, this.mBook.getMaxPaperPage()));
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.goto_paper_page_dialog_title)
                .setNegativeButton(R.string.goto_paper_page_dialog_cancel, null)
                .setPositiveButton(R.string.goto_paper_page_dialog_go, (d, w) -> {
                    int page = -1;
                    try { page = Integer.parseInt(editText.getText().toString()); } catch (NumberFormatException ignored) {}
                    if (page > -1) {
                        if (this.mBook.getPaperPages().contains(page)) this.mBookView.jumpToPaperPage(page);
                        else Toast.makeText(this, R.string.goto_paper_page_missing, Toast.LENGTH_SHORT).show();
                    }
                })
                .setView(viewInflate).create();

        if (dialog.getWindow() != null) dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        editText.setOnEditorActionListener((v, actionId, event) -> { dialog.getButton(DialogInterface.BUTTON_POSITIVE).callOnClick(); return true; });
        dialog.show();
    }

    @Override
    public void onSystemUiVisibilityChange(int i) {
        if ((i & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
            if (!this.mPhotoLaunching && !isSystemUiLocked()) setChromeVisible(true);
        } else {
            setChromeVisible(false);
        }
    }

    public void setTheme(Theme theme) {
        int color = theme != null ? theme.backgroundColor | ViewCompat.MEASURED_STATE_MASK : -1;
        if (this.mBookView != null) {
            this.mBookView.setTheme(theme);
            this.mBookView.setBackgroundColor(color);
        }
        int iRgb = Color.rgb((int) (Color.red(color) * 0.97f), (int) (Color.green(color) * 0.97f), (int) (Color.blue(color) * 0.97f));
        this.mBookContainerView.setBackgroundColor(iRgb);
        if (this.mBookView != null) this.mBookView.setNonContentBackgroundColor(iRgb);
        if (Build.VERSION.SDK_INT >= 21) {
            this.mLoadingView.getIndeterminateDrawable().setTint(theme != null ? theme.textColor | ViewCompat.MEASURED_STATE_MASK : ContextCompat.getColor(this, R.color.black_high));
        }
        applyChromeColor();
    }

    @Override
    public boolean onKeyDown(int i, KeyEvent keyEvent) {
        String pagination = this.mPrefs.getString("volume_pagination", "disabled");
        if (this.mBookView != null && this.mBookView.getFlow() == 1 && !"disabled".equals(pagination)) {
            int forwardKey = "reversed".equals(pagination) ? KeyEvent.KEYCODE_VOLUME_DOWN : KeyEvent.KEYCODE_VOLUME_UP;
            if (i == KeyEvent.KEYCODE_VOLUME_UP || i == KeyEvent.KEYCODE_VOLUME_DOWN) {
                if (keyEvent.getRepeatCount() > 0) {
                    long now = System.currentTimeMillis();
                    if (now - this.mLastPageChangeRepeat < PAGE_REPEAT_MIN_INTERVAL) return true;
                    this.mLastPageChangeRepeat = now;
                }
                advancePage(i == forwardKey ? 1 : -1);
                return true;
            }
        }
        return super.onKeyDown(i, keyEvent);
    }

    @Override
    public boolean onKeyUp(int i, KeyEvent keyEvent) {
        String pagination = this.mPrefs.getString("volume_pagination", "disabled");
        if (this.mBookView != null && this.mBookView.getFlow() == 1 && !"disabled".equals(pagination) && (i == KeyEvent.KEYCODE_VOLUME_UP || i == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            return true;
        }
        return super.onKeyUp(i, keyEvent);
    }

    public void advancePage(int i) {
        if (!this.mIsLoading) this.mBookView.advancePage(i);
    }

    public void updateReadingProgress() {
        int flow = this.mBookView.getFlow();
        if (flow == 1 || (flow == 2 && this.mUseBookWidePages)) {
            int page = this.mUseBookWidePages ? this.mBookView.getPage() : this.mBookView.getLegacyPage();
            int pageCount = this.mUseBookWidePages ? this.mBookView.getPageCount() : this.mBookView.getLegacyPageCount();
            if (page == -1) page = pageCount - 1;
            this.mPageNumberView.setText(getString(R.string.page_number_label, page + 1, pageCount));
            this.mPageSeekView.setMax(pageCount - 1);
            this.mPageSeekView.setProgress(page);
        } else {
            int iRound = Math.round(this.mBookView.getScrollPosition() * 100.0f);
            this.mPageNumberView.setText(getString(R.string.percent_number_label, iRound));
            this.mPageSeekView.setMax(100);
            this.mPageSeekView.setProgress(iRound);
        }
        int paperPage = this.mBookView.getPaperPage();
        if (paperPage != -1) {
            this.mPaperPageNumberView.setVisibility(View.VISIBLE);
            this.mPaperPageNumberView.setText(getString(R.string.paper_page_number_label, paperPage));
        } else {
            this.mPaperPageNumberView.setVisibility(View.GONE);
        }
    }

    public void updateBookmarkStatus() {
        this.mBookmarkHere = this.mBookmarkManager.findBookmark(this.mBookId, this.mBookmarkMatcher) != BookmarkManager.NOT_FOUND;
        invalidateOptionsMenu();
    }

    @Override
    public void onSearchBack() {
        hideSearchUi();
    }

    @Override
    public void onSearchVoiceClicked() {
        Intent intent = new Intent("android.speech.action.RECOGNIZE_SPEECH");
        if (intent.resolveActivity(getPackageManager()) == null) {
            Toast.makeText(this, R.string.no_voice_input_app, Toast.LENGTH_SHORT).show();
        } else {
            intent.putExtra("android.speech.extra.LANGUAGE_MODEL", "free_form");
            startActivityForResult(intent, REQUEST_SEARCH_VOICE);
        }
    }

    @Override
    public void onSearchSubmitted(String str) {
        ReaderSearchResultsAdapter adapter = new ReaderSearchResultsAdapter(this);
        adapter.setOnClickListener(this);
        this.mSearchResultsView.setAdapter(adapter);
        this.mSearchProvider.startSearch(str, this);
        this.mSearchHistoryManager.submitQuery(str);
        hideRecentSearches();
    }

    @Override
    public void onSearchFinished(List<SearchResult> list) {
        if (this.mSearchUiVisible) {
            ((ReaderSearchResultsAdapter) this.mSearchResultsView.getAdapter()).finished(list);
        }
    }

    @Override
    public void onSearchClearHistoryClicked() {
        if (this.mSearchHistoryManager.clearHistory()) {
            this.mRecentSearchesAdapter.setQueries(Collections.emptyList());
            this.mRecentSearchesAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onSearchResultClick(SearchResult searchResult) {
        hideSearchUi();
        pushBackStack();
        this.mBookView.jumpToSearchResult(searchResult);
    }

    @Override
    protected void onActivityResult(int i, int i2, Intent intent) {
        super.onActivityResult(i, i2, intent);
        if (i == REQUEST_SEARCH_VOICE && i2 == RESULT_OK) {
            ArrayList<String> results = intent.getStringArrayListExtra("android.speech.extra.RESULTS");
            if (results != null && !results.isEmpty()) {
                this.mSearchBarView.setQueryAndSubmit(results.get(0));
            }
        }
    }

    public void handleExternalUrl(String str) {
        final Uri uri = Uri.parse(str);
        String scheme = uri.getScheme();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            try { startActivity(new Intent("android.intent.action.VIEW").setData(uri)); }
            catch (ActivityNotFoundException unused) {
                new AlertDialog.Builder(this).setMessage(getString(R.string.book_external_link_no_app, uri.toString())).setPositiveButton(R.string.book_external_link_no_app_ok, null).show();
            }
        } else if (this.mPrefs.getBoolean("warn_external_links", true)) {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.book_external_link_warning_title, uri.getHost()))
                    .setMessage(R.string.book_external_link_warning_message)
                    .setNegativeButton(R.string.book_external_link_warning_cancel, null)
                    .setPositiveButton(R.string.book_external_link_warning_open, (dialog, which) -> startActivity(new Intent("android.intent.action.VIEW").setData(uri)))
                    .show();
        } else {
            startActivity(new Intent("android.intent.action.VIEW").setData(uri));
        }
    }

    private void initSelectionToolbarView() {
        this.mSelectionToolbarView = new SelectionToolbarView(this);
        ViewGroup group = (ViewGroup) findViewById(R.id.selection_toolbar_layout);
        this.mSelectionToolbarView.setVisibility(View.INVISIBLE);
        group.addView(this.mSelectionToolbarView);
        this.mAnnotationController.setSelectionToolbarView(this.mSelectionToolbarView);
    }

    @Override
    protected void onProStateChanged() {
        super.onProStateChanged();
        if (this.mDisplaySettingsFragment != null) this.mDisplaySettingsFragment.onProStateChanged();
        if (this.mSelectionToolbarView != null) this.mSelectionToolbarView.onProStateChanged();
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long j) {
        if (adapterView == this.mRecentSearchesListView) {
            this.mSearchBarView.setQueryAndSubmit(this.mRecentSearchesAdapter.getQuery(i));
        }
    }

    @Override
    protected void attachBaseContext(Context context) {
        this.mThemeManager = ThemeManager.getInstance(context);
        this.mTheme = this.mThemeManager.getTheme();
        if (USE_LOCAL_NIGHT_MODE) {
            getDelegate().setLocalNightMode(this.mTheme != null && this.mTheme.darkChrome ? 2 : -100);
        }
        super.attachBaseContext(context);
    }

    private static class BackStackEntry implements Serializable { String position; }
    private static class BackStackTransport implements Serializable { Stack<BackStackEntry> entries; }

    private static class BookLoadTask extends AsyncHelper.Task<BookLoadResult> {
        private String mFilePath;
        public BookLoadTask(String str) { this.mFilePath = str; }
        @Override public BookLoadResult run() {
            BookLoadResult result = new BookLoadResult();
            try { result.book = Book.create(this.mFilePath); }
            catch (BookLoadException e) { e.printStackTrace(); result.exception = e; }
            return result;
        }
    }

    static class BookLoadResult { Book book; BookLoadException exception; }
}