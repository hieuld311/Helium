package com.hieuld.helium;

import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import com.hieuld.helium.db.CategoriesTable;
import com.hieuld.helium.db.DatabaseProvider;
import com.hieuld.helium.db.SyncBaseColumns;
import com.hieuld.helium.util.DrawerAdapterHelper;
import com.hieuld.helium.util.adapters.DrawerFooterAdapter;
import com.hieuld.helium.util.adapters.MultiAdapter;
import com.hieuld.helium.util.adapters.SingleActionAdapter;
import com.hieuld.helium.util.adapters.SingleViewAdapter;
import java.util.UUID;

public class MainDrawerFragment extends Fragment implements AdapterView.OnItemClickListener {
    private MultiAdapter mAdapter;
    private CategoriesAdapter mCategoriesAdapter;
    private SQLiteDatabase mDatabase;
    private MultiAdapter mFolderSection;
    private FoldersAdapter mFoldersAdapter;
    private Fragment mFragment;
    private int mInset;
    private ListView mListView;
    private Listener mListener;
    private MainAdapter mMainAdapter;
    private SingleActionAdapter mNewCategoryAction;

    public interface Listener {
        void onDrawerItemClicked();
        void switchToFragment(Fragment fragment);
    }

    public interface OnNewCategoryAddedListener {
        void onNewCategoryAdded(long id);
    }

    @Override
    public View onCreateView(LayoutInflater layoutInflater, ViewGroup viewGroup, Bundle bundle) {
        View view = layoutInflater.inflate(R.layout.fragment_main_drawer, viewGroup, false);
        ListView listView = (ListView) view.findViewById(R.id.list);
        mListView = listView;
        listView.setOnItemClickListener(this);
        Context ctx = requireContext();
        mDatabase = DatabaseProvider.getDatabase(ctx);
        mMainAdapter = new MainAdapter();
        mCategoriesAdapter = new CategoriesAdapter(ctx);
        mFoldersAdapter = new FoldersAdapter(ctx);
        mAdapter = new MultiAdapter();

        // Header – always show app name (Pro removed)
        mAdapter.addAdapter(new SingleViewAdapter(ctx, R.layout.drawer_header) {
            @Override
            public void bindView(View v) {
                super.bindView(v);
                v.setPadding(0, mInset, 0, 0);
                ((TextView) v.findViewById(R.id.title)).setText(R.string.app_name);
            }
        });
        mAdapter.addAdapter(new SingleViewAdapter(ctx, R.layout.drawer_content_spacing));
        mAdapter.addAdapter(mMainAdapter);
        mAdapter.addAdapter(new SingleViewAdapter(ctx, R.layout.categories_list_header));
        mAdapter.addAdapter(mCategoriesAdapter);

        mNewCategoryAction = new SingleActionAdapter(ctx, R.drawable.ic_create_category, R.string.categories_new);
        mAdapter.addAdapter(mNewCategoryAction);

        MultiAdapter folderSection = new MultiAdapter();
        mFolderSection = folderSection;
        folderSection.addAdapter(new SingleViewAdapter(ctx, R.layout.folders_list_header));
        mFolderSection.addAdapter(mFoldersAdapter);
        mAdapter.addAdapter(mFolderSection);

        mAdapter.addAdapter(new SingleViewAdapter(ctx, R.layout.drawer_divider));
        mAdapter.addAdapter(new DrawerFooterAdapter(ctx));

        mListView.setAdapter((ListAdapter) mAdapter);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    public void refresh() {
        mCategoriesAdapter.changeCursor(mDatabase.query(
                CategoriesTable.TABLE_NAME, new String[]{"_id", "name"}, null, null, null, null, null));
        if (isAdded()) {
            boolean showFolders = PreferenceManager.getDefaultSharedPreferences(getActivity())
                    .getBoolean("folders_in_drawer", false);
            mAdapter.setEnabled(mFolderSection, showFolders);
            if (showFolders) {
                mFoldersAdapter.changeCursor(mDatabase.rawQuery(
                        "SELECT _id, folder FROM books WHERE hidden=0 GROUP BY folder", null));
            }
        }
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mCategoriesAdapter.changeCursor(null);
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
        MultiAdapter.Projection projection = mAdapter.getProjection(position);
        if (projection.adapter == mMainAdapter) {
            mListener.switchToFragment(new BooksFragment());
        } else if (projection.adapter == mCategoriesAdapter) {
            mListener.switchToFragment(BooksFragment.newCategoryInstance(id));
        } else if (projection.adapter == mNewCategoryAction) {
            showNewCategoryDialog(newId -> {
                if (isAdded()) {
                    mListener.switchToFragment(BooksFragment.newCategoryInstance(newId));
                }
            });
        } else {
            BaseAdapter baseAdapter = projection.adapter;
            if (baseAdapter == mFolderSection) {
                MultiAdapter.Projection p2 = mFolderSection.getProjection(projection.index);
                if (p2.adapter == mFoldersAdapter) {
                    mListener.switchToFragment(
                            BooksFragment.newFolderInstance(mFoldersAdapter.getFolderAtIndex(p2.index)));
                }
            } else if (projection.adapter instanceof DrawerFooterAdapter) {
                BaseActivity activity = (BaseActivity) getActivity();
                int idx = projection.index;
                if (idx == 0) {
                    startActivity(new Intent(activity, SettingsActivity.class));
                } else if (idx == 1) {
                    activity.showFeedbackDialog();
                }
            }
        }
        mListener.onDrawerItemClicked();
    }

    public void setCurrentFragment(Fragment fragment) {
        mFragment = fragment;
        mAdapter.notifyDataSetChanged();
    }

    public void setInset(int inset) {
        mInset = inset;
    }

    public void showNewCategoryDialog(final OnNewCategoryAddedListener listener) {
        View dialogView = ((LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE))
                .inflate(R.layout.dialog_category_name, (ViewGroup) null);
        final EditText editText = (EditText) dialogView.findViewById(R.id.name);
        final AlertDialog dialog = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.new_category_dialog_title)
                .setNegativeButton(R.string.new_category_dialog_cancel, null)
                .setPositiveButton(R.string.new_category_dialog_ok, (d, which) -> {
                    String name = editText.getText().toString().trim();
                    if (!TextUtils.isEmpty(name)) {
                        insertNewCategory(name, listener);
                    }
                })
                .setView(dialogView)
                .create();
        dialog.getWindow().setSoftInputMode(4);
        editText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                dialog.getButton(-1).setEnabled(!editText.getText().toString().trim().isEmpty());
            }
        });
        editText.setOnEditorActionListener((v, actionId, event) -> {
            dialog.getButton(-1).callOnClick();
            return true;
        });
        dialog.show();
        dialog.getButton(-1).setEnabled(false);
    }

    private void insertNewCategory(final String name, final OnNewCategoryAddedListener listener) {
        final Context appContext = requireContext().getApplicationContext();
        new AsyncTask<Void, Void, Long>() {
            @Override
            protected Long doInBackground(Void... v) {
                ContentValues values = new ContentValues();
                values.put("name", name);
                values.put("created_date", System.currentTimeMillis());
                values.put(SyncBaseColumns._SYNC_ID, UUID.randomUUID().toString());
                return mDatabase.insert(CategoriesTable.TABLE_NAME, null, values);
            }

            @Override
            protected void onPostExecute(Long newId) {
                refresh();
                if (listener != null) {
                    listener.onNewCategoryAdded(newId);
                }
            }
        }.execute();
    }

    // ---- Inner adapters ----

    private class CategoriesAdapter extends CursorAdapter {
        public CategoriesAdapter(Context context) {
            super(context, null, 0);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            return DrawerAdapterHelper.inflateView(context, parent);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            long catId = cursor.getLong(0);
            String name = cursor.getString(1);
            BooksFragment bf = mFragment instanceof BooksFragment ? (BooksFragment) mFragment : null;
            boolean selected = bf != null && bf.getFilter() == 1
                    && bf.getArguments().getLong(BooksFragment.EXTRA_CATEGORY_ID) == catId;
            DrawerAdapterHelper.bindView(context, view, R.drawable.ic_drawer_label, name, selected);
        }
    }

    private class MainAdapter extends BaseAdapter {
        @Override public int getCount() { return 1; }
        @Override public Object getItem(int i) { return null; }
        @Override public long getItemId(int i) { return 0L; }

        @Override
        public View getView(int i, View view, ViewGroup parent) {
            FragmentActivity activity = getActivity();
            if (view == null) {
                view = DrawerAdapterHelper.inflateView(activity, parent);
            }
            BooksFragment bf = mFragment instanceof BooksFragment ? (BooksFragment) mFragment : null;
            DrawerAdapterHelper.bindView(activity, view, R.drawable.ic_drawer_book,
                    getString(R.string.my_books), bf != null && bf.getFilter() == 0);
            return view;
        }
    }

    private class FoldersAdapter extends CursorAdapter {
        public FoldersAdapter(Context context) {
            super(context, null, 0);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            return DrawerAdapterHelper.inflateView(context, parent);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            String folder = cursor.getString(1);
            String label = folder.substring(folder.lastIndexOf("/") + 1);
            BooksFragment bf = mFragment instanceof BooksFragment ? (BooksFragment) mFragment : null;
            DrawerAdapterHelper.bindView(context, view, R.drawable.ic_drawer_folder, label,
                    bf != null && bf.getFilter() == 2
                            && folder.equals(bf.getArguments().getString("folder")));
        }

        public String getFolderAtIndex(int index) {
            Cursor cursor = getCursor();
            if (cursor.moveToPosition(index)) {
                return cursor.getString(1);
            }
            return null;
        }
    }
}
