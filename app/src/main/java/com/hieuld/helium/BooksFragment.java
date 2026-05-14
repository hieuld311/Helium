package com.hieuld.helium;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.media.MediaScannerConnection;

import android.os.Bundle;
import androidx.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.core.view.ViewCompat;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;
import androidx.interpolator.view.animation.FastOutLinearInInterpolator;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hieuld.helium.BooksAdapter;
import com.hieuld.helium.db.BookCategoryLinksTable;
import com.hieuld.helium.db.BooksTable;
import com.hieuld.helium.db.CategoriesTable;
import com.hieuld.helium.db.DatabaseProvider;
import com.hieuld.helium.db.SyncBaseColumns;
import com.hieuld.helium.export.ExportDataDialog;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Executor;
import android.os.Handler;
import android.os.Looper;
import com.hieuld.helium.util.ActionModeMultiCallback;
import com.hieuld.helium.util.FragmentComparison;
import com.hieuld.helium.util.SearchableFragment;
import com.hieuld.helium.util.Utils;
import com.hieuld.helium.widget.AutoFitRecyclerView;
import com.google.android.material.snackbar.Snackbar;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class BooksFragment extends Fragment implements BooksAdapter.OnItemClickListener, View.OnClickListener, SearchableFragment, FragmentComparison {

    public static final String EXTRA_CATEGORY_ID = "categoryId";
    public static final String EXTRA_FOLDER = "folder";
    public static final int FILTER_CATEGORY = 1;
    public static final int FILTER_FOLDER = 2;
    public static final int FILTER_NONE = 0;
    private static final int PRELOAD_COVER_COUNT = 4;
    private static final String EXPORT_DIALOG_TAG = "export_dialog";
    private static final String STATE_SELECTED_ITEM_IDS = "selected_item_ids";
    private static final String TAG = "BooksFragment";

    private BooksAdapter mAdapter;
    private AutoFitRecyclerView mBookshelfView;
    private long mCategoryId;
    private String mCategoryName;
    private Context mContext;
    private SQLiteDatabase mDatabase;
    private boolean mEmptyCategory;
    private Button mEmptyGotoAllButton;
    private Button mEmptyRefreshButton;
    private ViewGroup mEmptyView;
    private int mFilter;
    private String mFolder;
    private int mLayoutMode = 0;
    private TextView mNewCategoryBannerTitle;
    private View mNewCategoryBannerView;
    private Button mNewCategoryDoneButton;
    private OnBookPickedListener mOnBookPickedListener;
    private SharedPreferences mPrefs;
    private TextView mSearchEmptyTextView;
    private String mSearchQuery;
    private String mTitle;

    public interface OnBookPickedListener {
        void onBookPicked(long j);
    }

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        Context contextRequireContext = requireContext();
        this.mContext = contextRequireContext;
        this.mDatabase = DatabaseProvider.getDatabase(contextRequireContext);
        Bundle arguments = getArguments();
        if (arguments != null) {
            if (arguments.containsKey(EXTRA_CATEGORY_ID)) {
                loadCategory(arguments.getLong(EXTRA_CATEGORY_ID));
                return;
            } else if (arguments.containsKey(EXTRA_FOLDER)) {
                loadFolder(arguments.getString(EXTRA_FOLDER));
                return;
            }
        }
        loadNone();
    }

    @Override
    public View onCreateView(LayoutInflater layoutInflater, ViewGroup viewGroup, Bundle bundle) {
        View viewInflate = layoutInflater.inflate(R.layout.fragment_books, viewGroup, false);
        this.mPrefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        this.mEmptyView = (ViewGroup) viewInflate.findViewById(R.id.empty_container);
        inflateEmptyView(layoutInflater, this.mEmptyView);

        this.mSearchEmptyTextView = (TextView) viewInflate.findViewById(R.id.search_empty_text);

        this.mNewCategoryBannerView = viewInflate.findViewById(R.id.new_category_banner);
        ViewCompat.setElevation(this.mNewCategoryBannerView, Utils.dpToPx(layoutInflater.getContext(), 1));
        this.mNewCategoryBannerTitle = (TextView) viewInflate.findViewById(R.id.new_category_banner_title);

        this.mNewCategoryDoneButton = (Button) viewInflate.findViewById(R.id.new_category_banner_done);
        this.mNewCategoryDoneButton.setOnClickListener(view -> {
            addToCategory_3(this.mAdapter.getCheckedItemIds(), this.mCategoryId, false, null);
            refresh();
        });

        this.mBookshelfView = (AutoFitRecyclerView) viewInflate.findViewById(R.id.books);
        this.mBookshelfView.setHasFixedSize(true);
        this.mBookshelfView.setSpanWidth(((int) getResources().getDimension(R.dimen.bookshelf_cover_width)) + Utils.dpToPx(this.mContext, 10));

        this.mAdapter = new BooksAdapter((AppCompatActivity) getActivity(), this, new BooksMultiChoiceListener());
        this.mBookshelfView.setAdapter(this.mAdapter);

        if (bundle != null) {
            @SuppressWarnings("unchecked")
            ArrayList<Long> arrayList = (ArrayList<Long>) bundle.getSerializable(STATE_SELECTED_ITEM_IDS);
            if (arrayList != null && !arrayList.isEmpty()) {
                this.mAdapter.restoreSelectionState(arrayList);
            }
        }

        fetchLayoutModeFromPrefs();
        applyLayoutMode();
        runQuery(true);
        updateTitle();
        setHasOptionsMenu(true);
        getActivity().invalidateOptionsMenu();
        return viewInflate;
    }

    @Override
    public void onSaveInstanceState(Bundle bundle) {
        super.onSaveInstanceState(bundle);
        if (this.mAdapter != null) {
            bundle.putSerializable(STATE_SELECTED_ITEM_IDS, this.mAdapter.getCheckedItemIds());
        }
    }

    private void inflateEmptyView(LayoutInflater layoutInflater, ViewGroup viewGroup) {
        int i = this.mFilter;
        if (i == 0) {
            this.mEmptyRefreshButton = (Button) layoutInflater.inflate(R.layout.empty_my_books, viewGroup).findViewById(R.id.empty_refresh);
            this.mEmptyRefreshButton.setOnClickListener(this);
        } else if (i == 1 || i == 2) {
            int layoutRes = i == 1 ? R.layout.empty_category : R.layout.empty_folder;
            this.mEmptyGotoAllButton = (Button) layoutInflater.inflate(layoutRes, viewGroup).findViewById(R.id.empty_goto_all);
            this.mEmptyGotoAllButton.setOnClickListener(this);
        }
    }

    private void fetchLayoutModeFromPrefs() {
        String string = this.mPrefs.getString("booksLayout", "grid");
        if ("grid".equals(string)) {
            this.mLayoutMode = 0;
        } else if ("list".equals(string)) {
            this.mLayoutMode = 1;
        }
    }

    private void setLayoutMode(int i) {
        if (this.mLayoutMode != i) {
            this.mLayoutMode = i;
            applyLayoutMode();
        }
    }

    private void applyLayoutMode() {
        if (this.mLayoutMode == 0 && this.mSearchQuery == null) {
            this.mBookshelfView.setLayoutManager(new GridLayoutManager(this.mContext, 1));
            this.mAdapter.setLayoutMode(0);
        } else {
            this.mBookshelfView.setLayoutManager(new LinearLayoutManager(this.mContext, RecyclerView.VERTICAL, false));
            this.mAdapter.setLayoutMode(1);
        }
    }

    /**
     * Phục dựng lại method đã bị JADX bỏ qua do quá phức tạp (Method dump skipped, 424 instructions)
     * Dựa trên các state có sẵn và cách thức filter, search.
     */
    public void runQuery(boolean isRefresh) {
        if (this.mDatabase == null || this.mAdapter == null) return;

        String sortPref = this.mPrefs.getString("bookSort", "last_read");
        String orderBy;
        if (BooksTable.COLUMN_TITLE.equals(sortPref)) {
            orderBy = "title ASC";
        } else if ("author".equals(sortPref)) {
            orderBy = "creator ASC";
        } else if ("added_date".equals(sortPref)) {
            orderBy = "added_date DESC";
        } else {
            orderBy = "last_open_date DESC"; // Default last_read
        }

        StringBuilder selection = new StringBuilder("hidden=0");
        List<String> selectionArgs = new ArrayList<>();

        if (this.mFilter == FILTER_CATEGORY) {
            selection.append(" AND _id IN (SELECT book_id FROM ").append(BookCategoryLinksTable.TABLE_NAME).append(" WHERE category_id=?)");
            selectionArgs.add(String.valueOf(this.mCategoryId));
        } else if (this.mFilter == FILTER_FOLDER) {
            selection.append(" AND folder=?");
            selectionArgs.add(this.mFolder);
        }

        if (!TextUtils.isEmpty(this.mSearchQuery)) {
            selection.append(" AND (title LIKE ? OR creator LIKE ?)");
            String likeQuery = "%" + this.mSearchQuery + "%";
            selectionArgs.add(likeQuery);
            selectionArgs.add(likeQuery);
        }

        Cursor cursor = this.mDatabase.query(BooksTable.TABLE_NAME, null,
                selection.toString(), selectionArgs.toArray(new String[0]), null, null, orderBy);

        this.mAdapter.swapCursor(cursor);
        this.mAdapter.setSearchQuery(this.mSearchQuery); // Cập nhật query để adapter tự highlight chữ

        boolean isEmpty = cursor.getCount() == 0;

        if (this.mSearchQuery != null && !this.mSearchQuery.trim().isEmpty() && isEmpty) {
            this.mSearchEmptyTextView.setVisibility(View.VISIBLE);
            this.mSearchEmptyTextView.setText(getString(R.string.no_books_search, this.mSearchQuery));
            this.mEmptyView.setVisibility(View.GONE);
            this.mBookshelfView.setVisibility(View.GONE);
        } else {
            this.mSearchEmptyTextView.setVisibility(View.GONE);
            this.mEmptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            this.mBookshelfView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        }

        this.mEmptyCategory = (this.mFilter == FILTER_CATEGORY && isEmpty);
    }

    private void loadNone() {
        this.mTitle = getString(R.string.my_books);
    }

    public void loadCategory(long j) {
        Cursor cursorQuery = this.mDatabase.query(CategoriesTable.TABLE_NAME, new String[]{"name"}, "_id=?", new String[]{String.valueOf(j)}, null, null, null);
        if (!cursorQuery.moveToFirst()) {
            Log.d(TAG, "Category " + j + " doesn't exist.");
            cursorQuery.close();
            ((MainActivity) getActivity()).onFragmentInvalid();
            return;
        }
        this.mFilter = FILTER_CATEGORY;
        this.mCategoryId = j;
        this.mCategoryName = cursorQuery.getString(0);
        this.mTitle = this.mCategoryName;
        cursorQuery.close();
    }

    private void loadFolder(String str) {
        this.mFilter = FILTER_FOLDER;
        this.mFolder = str;
        this.mTitle = str.substring(str.lastIndexOf("/") + 1);
    }

    public void updateTitle() {
        if (this.mOnBookPickedListener == null) {
            getActivity().setTitle(this.mTitle);
        } else {
            getActivity().setTitle(R.string.shortcut_select_book_title);
        }
    }

    private void hideNewCategoryBanner() {
        this.mNewCategoryBannerView.measure(0, 0);
        ValueAnimator valueAnimatorOfInt = ValueAnimator.ofInt(this.mNewCategoryBannerView.getMeasuredHeight(), 0);
        valueAnimatorOfInt.addUpdateListener(anim -> {
            ViewGroup.LayoutParams layoutParams = this.mNewCategoryBannerView.getLayoutParams();
            layoutParams.height = (Integer) anim.getAnimatedValue();
            this.mNewCategoryBannerView.setLayoutParams(layoutParams);
        });
        valueAnimatorOfInt.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                ViewGroup.LayoutParams layoutParams = BooksFragment.this.mNewCategoryBannerView.getLayoutParams();
                layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                BooksFragment.this.mNewCategoryBannerView.setLayoutParams(layoutParams);
                BooksFragment.this.mNewCategoryBannerView.setVisibility(View.GONE);
                BooksFragment.this.mNewCategoryBannerView.setTranslationX(0.0f);
            }
        });
        valueAnimatorOfInt.setDuration(180L);
        valueAnimatorOfInt.setInterpolator(new FastOutLinearInInterpolator());
        valueAnimatorOfInt.start();
    }

    public int getFilter() {
        Bundle arguments = getArguments();
        if (arguments == null) return FILTER_NONE;
        if (arguments.containsKey(EXTRA_CATEGORY_ID)) return FILTER_CATEGORY;
        return arguments.containsKey(EXTRA_FOLDER) ? FILTER_FOLDER : FILTER_NONE;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (this.mAdapter != null) {
            this.mAdapter.swapCursor(null);
        }
        this.mBookshelfView.setAdapter(null);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater menuInflater) {
        menuInflater.inflate(R.menu.books, menu);
        super.onCreateOptionsMenu(menu, menuInflater);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        String string = this.mPrefs.getString("bookSort", "last_read");
        if (BooksTable.COLUMN_TITLE.equals(string)) menu.findItem(R.id.sort_title).setChecked(true);
        if ("author".equals(string)) menu.findItem(R.id.sort_author).setChecked(true);
        if ("added_date".equals(string)) menu.findItem(R.id.sort_added_date).setChecked(true);
        if ("last_read".equals(string)) menu.findItem(R.id.sort_last_read).setChecked(true);

        String string2 = this.mPrefs.getString("booksLayout", "grid");
        if ("grid".equals(string2)) menu.findItem(R.id.layout_grid).setChecked(true);
        if ("list".equals(string2)) menu.findItem(R.id.layout_list).setChecked(true);

        menu.setGroupVisible(R.id.group_category_actions, this.mFilter == FILTER_CATEGORY);
        menu.findItem(R.id.select_all).setVisible(this.mAdapter.getItemCount() > 0);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        int id = menuItem.getItemId(); // Giả sử bạn đang lấy ID từ biến item

        if (id == R.id.delete_category) {
            showDeleteCategoryDialog();
        }
        else if (id == R.id.layout_grid) {
            this.mPrefs.edit().putString("booksLayout", "grid").apply();
            menuItem.setChecked(true);
            setLayoutMode(0);
        }
        else if (id == R.id.layout_list) {
            this.mPrefs.edit().putString("booksLayout", "list").apply();
            menuItem.setChecked(true);
            setLayoutMode(1);
        }
        else if (id == R.id.rename_category) {
            showRenameCategoryDialog();
        }
        else if (id == R.id.select_all) {
            this.mAdapter.selectAll();
        }
        else if (id == R.id.sort_added_date) {
            this.mPrefs.edit().putString("bookSort", "added_date").apply();
            menuItem.setChecked(true);
            runQuery(false);
        }
        else if (id == R.id.sort_author) {
            this.mPrefs.edit().putString("bookSort", "author").apply();
            menuItem.setChecked(true);
            runQuery(false);
        }
        else if (id == R.id.sort_last_read) {
            this.mPrefs.edit().putString("bookSort", "last_read").apply();
            menuItem.setChecked(true);
            runQuery(false);
        }
        else if (id == R.id.sort_title) {
            this.mPrefs.edit().putString("bookSort", BooksTable.COLUMN_TITLE).apply();
            menuItem.setChecked(true);
            runQuery(false);
        }
        return super.onOptionsItemSelected(menuItem);
    }

    @Override
    public void onItemClick(View view, long j) {
        if (this.mEmptyCategory) return;

        if (this.mOnBookPickedListener != null) {
            this.mOnBookPickedListener.onBookPicked(j);
            return;
        }
        if (this.mSearchQuery != null) {
            ((MainActivity) getActivity()).onFragmentSubmitSearch();
        }
        this.mContext.startActivity(new Intent(this.mContext, ReaderActivity.class).putExtra("book_id", j));
    }

    public void showDeleteBooksDialog(final List<Long> list) {
        if (!isAdded()) return;

        View viewInflate = LayoutInflater.from(this.mContext).inflate(R.layout.dialog_delete_book, null);
        final CheckBox checkBox = (CheckBox) viewInflate.findViewById(R.id.delete_file);

        final AlertDialog.Builder view = new AlertDialog.Builder(this.mContext)
                .setNegativeButton(R.string.book_delete_dialog_cancel, null)
                .setPositiveButton(R.string.book_delete_dialog_ok, (dialog, which) -> deleteBooks(list, checkBox.isChecked()))
                .setView(viewInflate);

        if (list.size() == 1) {
            Executor executor = Executors.newSingleThreadExecutor();
            Handler handler = new Handler(Looper.getMainLooper());
            executor.execute(() -> {
                Cursor cursorQuery = BooksFragment.this.mDatabase.query(BooksTable.TABLE_NAME, new String[]{BooksTable.COLUMN_TITLE}, "_id=?", new String[]{String.valueOf(list.get(0))}, null, null, null, null);
                String string = cursorQuery.moveToFirst() ? cursorQuery.getString(0) : null;
                cursorQuery.close();
                handler.post(() -> {
                    if (BooksFragment.this.isAdded()) {
                        view.setTitle(BooksFragment.this.getString(R.string.book_delete_dialog_title_one, string));
                        view.show();
                    }
                });
            });
        } else {
            view.setTitle(getResources().getQuantityString(R.plurals.book_delete_dialog_title_multi, list.size(), list.size()));
            view.show();
        }
    }

    private void deleteBooks(List<Long> list, boolean deleteFile) {
        for (Long jLongValue : list) {
            Cursor cursorQuery = this.mDatabase.query(BooksTable.TABLE_NAME, new String[]{"file_path", BooksTable.COLUMN_COVER}, "_id=?", new String[]{String.valueOf(jLongValue)}, null, null, null, null);
            if (cursorQuery.moveToFirst()) {
                String string = cursorQuery.getString(0);
                String string2 = cursorQuery.getString(1);
                if (string2 != null) {
                    File file = new File(string2);
                    if (file.exists() && !file.delete()) {
                        Log.e(TAG, "Failed to delete cover: " + string2);
                    }
                }

                boolean fileDeleted = deleteFile;
                File file2 = new File(string);
                if (fileDeleted && file2.exists()) {
                    if (file2.delete()) {
                        MediaScannerConnection.scanFile(this.mContext, new String[]{string}, null, null);
                    } else {
                        fileDeleted = false;
                    }
                }

                if (fileDeleted) {
                    this.mDatabase.delete(BooksTable.TABLE_NAME, "_id=?", new String[]{String.valueOf(jLongValue)});
                } else {
                    ContentValues contentValues = new ContentValues();
                    contentValues.put("hidden", true);
                    this.mDatabase.update(BooksTable.TABLE_NAME, contentValues, "_id=?", new String[]{String.valueOf(jLongValue)});
                }
            }
            cursorQuery.close();
        }
        runQuery(false);
        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity != null) {
            mainActivity.refreshDrawer();
            App.disableShortcutsForBooks(mainActivity, list);
            App.refreshAppShortcuts(mainActivity);
        }
        Snackbar.make(this.mBookshelfView, getResources().getQuantityString(R.plurals.book_deleted, list.size(), list.size()), Snackbar.LENGTH_SHORT).show();
    }

    public void showAddToCategoryDialog(final List<Long> list, final ActionMode actionMode) {
        Executor executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());
        executor.execute(() -> {
            Cursor cursor = BooksFragment.this.mDatabase.query(CategoriesTable.TABLE_NAME, new String[]{"_id", "name"}, null, null, null, null, null);
            handler.post(() -> {
                if (BooksFragment.this.isAdded()) {
                    final AlertDialog alertDialogCreate = new AlertDialog.Builder(BooksFragment.this.mContext)
                            .setTitle(R.string.book_add_to_category_dialog_title)
                            .setSingleChoiceItems(new CategoryListAdapter(BooksFragment.this.requireContext(), cursor), -1, null)
                            .setNegativeButton(R.string.book_add_to_category_dialog_cancel, null)
                            .create();

                    alertDialogCreate.getListView().setOnItemClickListener((adapterView, view, i, j) -> {
                        alertDialogCreate.dismiss();
                        BooksFragment.this.addToCategory_3(list, j, true, actionMode);
                    });
                    alertDialogCreate.show();
                }
            });
        });
    }

    public void addToCategory_3(final List<Long> list, long j, final boolean z, final ActionMode actionMode) {
        if (!isAdded()) return;

        if (j == -2) { // Tùy chọn tạo category mới (-2 là ID quy ước trong Adapter)
            MainActivity mainActivity = (MainActivity) getActivity();
            if (mainActivity != null) {
                mainActivity.getDrawerFragment().showNewCategoryDialog(newId -> addToCategory_3(list, newId, z, actionMode));
            }
            return;
        }

        if (actionMode != null) {
            actionMode.finish();
        }

        Cursor cursorQuery = this.mDatabase.query(CategoriesTable.TABLE_NAME, new String[]{"_id", "name"}, "_id=?", new String[]{String.valueOf(j)}, null, null, null);
        if (!cursorQuery.moveToFirst()) {
            cursorQuery.close();
            return;
        }
        String categoryName = cursorQuery.getString(1);
        cursorQuery.close();

        for (Long jLongValue : list) {
            ContentValues contentValues = new ContentValues();
            contentValues.put("book_id", jLongValue);
            contentValues.put(BookCategoryLinksTable.COLUMN_CATEGORY_ID, j);
            contentValues.put(SyncBaseColumns._SYNC_ID, UUID.randomUUID().toString());
            this.mDatabase.insert(BookCategoryLinksTable.TABLE_NAME, null, contentValues);
        }

        if (z) {
            Snackbar.make(this.mBookshelfView, getResources().getQuantityString(R.plurals.book_add_to_category_done, list.size(), list.size(), categoryName), Snackbar.LENGTH_SHORT).show();
        }
    }

    public void showRemoveFromCategoryDialog(final List<Long> list) {
        if (!isAdded()) return;
        new AlertDialog.Builder(this.mContext)
                .setMessage(getResources().getQuantityString(R.plurals.book_remove_from_category_dialog_message, list.size(), list.size(), this.mCategoryName))
                .setNegativeButton(R.string.book_remove_from_category_dialog_cancel, null)
                .setPositiveButton(R.string.book_remove_from_category_dialog_ok, (dialog, which) -> removeFromCategory(list, this.mCategoryId))
                .show();
    }

    private void removeFromCategory(List<Long> list, long j) {
        for (Long bookId : list) {
            this.mDatabase.delete(BookCategoryLinksTable.TABLE_NAME, "book_id=? AND category_id=?", new String[]{String.valueOf(bookId), String.valueOf(j)});
        }
        runQuery(false);
    }

    private void showRenameCategoryDialog() {
        View viewInflate = LayoutInflater.from(this.mContext).inflate(R.layout.dialog_category_name, null);
        final EditText editText = (EditText) viewInflate.findViewById(R.id.name);
        editText.setText(this.mCategoryName);
        editText.selectAll();

        final AlertDialog alertDialogCreate = new AlertDialog.Builder(this.mContext)
                .setTitle(R.string.rename_category_dialog_title)
                .setNegativeButton(R.string.rename_category_dialog_cancel, null)
                .setPositiveButton(R.string.rename_category_dialog_ok, (dialog, which) -> {
                    String strTrim = editText.getText().toString().trim();
                    if (!TextUtils.isEmpty(strTrim)) renameCategory(this.mCategoryId, strTrim);
                })
                .setView(viewInflate)
                .create();

        alertDialogCreate.getWindow().setSoftInputMode(4); // STATE_VISIBLE
        editText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {}
            @Override public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {}
            @Override
            public void afterTextChanged(Editable editable) {
                alertDialogCreate.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(!editText.getText().toString().trim().isEmpty());
            }
        });

        editText.setOnEditorActionListener((textView, i, keyEvent) -> {
            alertDialogCreate.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
            return true;
        });

        alertDialogCreate.show();
    }

    private void renameCategory(final long j, final String str) {
        Executor executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());
        executor.execute(() -> {
            ContentValues contentValues = new ContentValues();
            contentValues.put("name", str);
            contentValues.put("modified_date", System.currentTimeMillis());
            BooksFragment.this.mDatabase.update(CategoriesTable.TABLE_NAME, contentValues, "_id=?", new String[]{String.valueOf(j)});
            handler.post(() -> {
                if (isAdded()) {
                    BooksFragment.this.loadCategory(j);
                    BooksFragment.this.updateTitle();
                    MainActivity activity = (MainActivity) BooksFragment.this.getActivity();
                    if (activity != null) activity.refreshDrawer();
                }
            });
        });
    }

    private void showDeleteCategoryDialog() {
        new AlertDialog.Builder(this.mContext)
                .setMessage(getString(R.string.delete_category_dialog_message, this.mCategoryName))
                .setNegativeButton(R.string.delete_category_dialog_cancel, null)
                .setPositiveButton(R.string.delete_category_dialog_ok, (dialog, which) -> deleteCategory(this.mCategoryId))
                .show();
    }

    private void deleteCategory(final long j) {
        Executor executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());
        executor.execute(() -> {
            BooksFragment.this.mDatabase.delete(CategoriesTable.TABLE_NAME, "_id=?", new String[]{String.valueOf(j)});
            handler.post(() -> {
                if (BooksFragment.this.isAdded()) {
                    MainActivity mainActivity = (MainActivity) BooksFragment.this.getActivity();
                    if (mainActivity != null) {
                        mainActivity.onFragmentInvalid();
                        mainActivity.onFragmentInvalidatedDrawer();
                    }
                }
            });
        });
    }

    @Override
    public void onClick(View view) {
        if (view == this.mEmptyGotoAllButton) {
            ((MainActivity) getActivity()).onFragmentRedirect(newInstance());
        } else if (view == this.mEmptyRefreshButton) {
            ((MainActivity) getActivity()).onFragmentForceRefresh();
        }
    }

    public void refresh() {
        if (getView() == null) return;
        runQuery(false);
    }

    private void showExportDialog(long bookId) {
        ExportDataDialog.newInstance(bookId).show(getChildFragmentManager(), EXPORT_DIALOG_TAG);
    }

    public static Fragment newInstance() {
        return new BooksFragment();
    }

    public static BooksFragment newCategoryInstance(long j) {
        BooksFragment booksFragment = new BooksFragment();
        Bundle bundle = new Bundle();
        bundle.putLong(EXTRA_CATEGORY_ID, j);
        booksFragment.setArguments(bundle);
        return booksFragment;
    }

    public static Fragment newFolderInstance(String str) {
        BooksFragment booksFragment = new BooksFragment();
        Bundle bundle = new Bundle();
        bundle.putString(EXTRA_FOLDER, str);
        booksFragment.setArguments(bundle);
        return booksFragment;
    }

    @Override
    public void onSearchQueryChanged(String str) {
        boolean wasEmpty = this.mSearchQuery == null && str != null;
        boolean isNowEmpty = this.mSearchQuery != null && str == null;
        this.mSearchQuery = str;
        if (wasEmpty || isNowEmpty) {
            applyLayoutMode();
        }
        runQuery(false);
    }

    @Override
    public boolean equalsFragment(Fragment fragment) {
        if (!(fragment instanceof BooksFragment)) return false;
        BooksFragment other = (BooksFragment) fragment;
        Bundle args1 = other.getArguments();
        Bundle args2 = getArguments();
        if (args1 == null || args2 == null) return args1 == args2;

        return other.getFilter() == getFilter()
                && args1.getLong(EXTRA_CATEGORY_ID) == args2.getLong(EXTRA_CATEGORY_ID)
                && TextUtils.equals(args1.getString(EXTRA_FOLDER), args2.getString(EXTRA_FOLDER));
    }

    public void enablePickMode(OnBookPickedListener onBookPickedListener) {
        this.mOnBookPickedListener = onBookPickedListener;
    }

    public void selectAll() {
        this.mAdapter.selectAll();
    }

    private class BooksMultiChoiceListener implements ActionModeMultiCallback {
        private BooksMultiChoiceListener() {}

        @Override
        public void onCheckedItemsChanged(ActionMode actionMode) {
            int size = BooksFragment.this.mAdapter.getCheckedItemIds().size();
            if (actionMode != null) {
                if (size == 0) return;
                actionMode.setTitle(String.valueOf(size));
            } else if (size <= 0) {
                BooksFragment.this.mNewCategoryBannerTitle.setText(R.string.new_category_banner_title);
                BooksFragment.this.mNewCategoryDoneButton.setEnabled(false);
            } else {
                BooksFragment.this.mNewCategoryBannerTitle.setText(BooksFragment.this.getString(R.string.new_category_banner_title_selected, size));
                BooksFragment.this.mNewCategoryDoneButton.setEnabled(true);
            }
        }

        @Override
        public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
            BooksFragment.this.getActivity().getMenuInflater().inflate(R.menu.main_book_selection, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
            menu.findItem(R.id.remove_from_category).setVisible(BooksFragment.this.mFilter == FILTER_CATEGORY);
            menu.findItem(R.id.export).setVisible(BooksFragment.this.mAdapter.getCheckedItemIds().size() == 1);
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
            ArrayList<Long> checkedItemIds = BooksFragment.this.mAdapter.getCheckedItemIds();
            if (checkedItemIds.isEmpty()) return true;

            int itemId = menuItem.getItemId();

            if (itemId == R.id.add_to_category) {
                BooksFragment.this.showAddToCategoryDialog(checkedItemIds, actionMode);
                return true;
            }
            else if (itemId == R.id.delete) {
                BooksFragment.this.showDeleteBooksDialog(checkedItemIds);
                return true;
            }
            else if (itemId == R.id.export) {
                BooksFragment.this.showExportDialog(checkedItemIds.get(0));
                return true;
            }
            else if (itemId == R.id.remove_from_category) {
                BooksFragment.this.showRemoveFromCategoryDialog(checkedItemIds);
                return true;
            }
            else if (itemId == R.id.select_all) {
                BooksFragment.this.mAdapter.selectAll();
                return true;
            }
            else {
                return false;
            }
        }

        @Override
        public void onDestroyActionMode(ActionMode actionMode) {
            BooksFragment.this.mAdapter.endMultiSelect();
        }
    }

    private static class CategoryListAdapter extends BaseAdapter {
        private final List<Category> mCategories = new ArrayList<>();
        private final Context mContext;

        public CategoryListAdapter(Context context, Cursor cursor) {
            this.mContext = context;
            while (cursor.moveToNext()) {
                Category category = new Category();
                category.id = cursor.getLong(0);
                category.name = cursor.getString(1);
                this.mCategories.add(category);
            }
            cursor.close();
        }

        @Override
        public int getCount() {
            return this.mCategories.size() + 1; // +1 cho tuỳ chọn tạo mới
        }

        @Override
        public Object getItem(int i) { return null; }

        @Override
        public long getItemId(int i) {
            if (i < this.mCategories.size()) {
                return this.mCategories.get(i).id;
            }
            return -2L; // ID quy ước cho việc "Tạo category mới"
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            if (view == null) {
                view = LayoutInflater.from(this.mContext).inflate(R.layout.dialog_add_to_category_item, viewGroup, false);
            }
            TextView textView = (TextView) view;
            if (i == this.mCategories.size()) {
                TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(textView, R.drawable.ic_add_black, 0, 0, 0);
                textView.setText(R.string.book_add_to_category_dialog_new_category);
            } else {
                TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(textView, R.drawable.ic_label, 0, 0, 0);
                textView.setText(this.mCategories.get(i).name);
            }
            return view;
        }

        private static class Category {
            long id;
            String name;
            private Category() {}
        }
    }
}
