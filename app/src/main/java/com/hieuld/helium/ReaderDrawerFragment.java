package com.hieuld.helium;

import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.ViewCompat;
import androidx.cursoradapter.widget.CursorAdapter;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import com.hieuld.helium.book.Book;
import com.hieuld.helium.book.TocEntry;
import com.hieuld.helium.db.AnnotationsTable;
import com.hieuld.helium.db.BookmarksTable;
import com.hieuld.helium.db.DatabaseProvider;
import com.hieuld.helium.model.BookmarkManager;
import com.hieuld.helium.ui.TocListAdapter;
import com.hieuld.helium.util.Utils;
import com.google.android.material.tabs.TabLayout;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class ReaderDrawerFragment extends Fragment implements AdapterView.OnItemClickListener, TabLayout.OnTabSelectedListener, TocListAdapter.OnClickListener {
    private AnnotationsListAdapter mAnnotationsListAdapter;
    private ListView mAnnotationsListView;
    private View mAnnotationsTabContainer;
    private Book mBook;
    private long mBookId;
    private BookmarksListAdapter mBookmarksListAdapter;
    private ListView mBookmarksListView;
    private View mBookmarksTabContainer;
    private TocEntry mCurrentEntry;
    private SQLiteDatabase mDatabase;
    private Listener mListener;
    private ViewPager mPager;
    private View mTocEmptyView;
    private TocListAdapter mTocListAdapter;
    private RecyclerView mTocListView;
    private View mTocTabContainer;

    public interface Listener {
        void onDrawerDeleteAnnotation(long j);
        void onDrawerInvalidatedBookmark();
        void onDrawerSelectedAnnotation(long j, String str, String str2);
        void onDrawerSelectedBookmark(String str);
        void onDrawerSelectedChapter(TocEntry tocEntry);
    }

    @Override
    public void onTabReselected(TabLayout.Tab tab) {}

    @Override
    public void onTabUnselected(TabLayout.Tab tab) {}

    @Override
    public View onCreateView(LayoutInflater layoutInflater, ViewGroup viewGroup, Bundle bundle) {
        View viewInflate = layoutInflater.inflate(R.layout.fragment_reader_drawer, viewGroup, false);
        Context context = layoutInflater.getContext();
        this.mDatabase = DatabaseProvider.getDatabase(context);
        ViewCompat.setElevation(viewInflate.findViewById(R.id.tabs_container), Utils.dpToPx(context, 1));
        TabLayout tabLayout = (TabLayout) viewInflate.findViewById(R.id.tabs);
        tabLayout.addOnTabSelectedListener((TabLayout.OnTabSelectedListener) this);
        this.mPager = (ViewPager) viewInflate.findViewById(R.id.pager);
        this.mPager.setAdapter(new TabsAdapter());
        tabLayout.setupWithViewPager(this.mPager);
        tabLayout.removeAllTabs();
        tabLayout.addTab(tabLayout.newTab().setIcon(getTabIcon(context, R.drawable.ic_tab_contents)));
        tabLayout.addTab(tabLayout.newTab().setIcon(getTabIcon(context, R.drawable.ic_tab_annotations)));
        tabLayout.addTab(tabLayout.newTab().setIcon(getTabIcon(context, R.drawable.ic_tab_bookmarks)));

        this.mTocTabContainer = layoutInflater.inflate(R.layout.fragment_reader_drawer_contents, (ViewGroup) null, false);
        this.mTocListView = (RecyclerView) this.mTocTabContainer.findViewById(R.id.contents_list);
        this.mTocListView.setHasFixedSize(true);
        this.mTocListView.setLayoutManager(new LinearLayoutManager(context, RecyclerView.VERTICAL, false));
        this.mTocEmptyView = this.mTocTabContainer.findViewById(R.id.empty);
        this.mTocListAdapter = new TocListAdapter(context, this);
        this.mTocListView.setAdapter(this.mTocListAdapter);

        this.mAnnotationsTabContainer = layoutInflater.inflate(R.layout.fragment_reader_drawer_annotations, (ViewGroup) null, false);
        this.mAnnotationsListView = (ListView) this.mAnnotationsTabContainer.findViewById(R.id.annotations_list);
        this.mAnnotationsListView.setEmptyView(this.mAnnotationsTabContainer.findViewById(R.id.empty));
        this.mAnnotationsListAdapter = new AnnotationsListAdapter(layoutInflater.getContext(), null);
        this.mAnnotationsListView.setAdapter((ListAdapter) this.mAnnotationsListAdapter);

        this.mBookmarksTabContainer = layoutInflater.inflate(R.layout.fragment_reader_drawer_bookmarks, (ViewGroup) null, false);
        this.mBookmarksListView = (ListView) this.mBookmarksTabContainer.findViewById(R.id.bookmarks_list);
        this.mBookmarksListView.setEmptyView(this.mBookmarksTabContainer.findViewById(R.id.empty));
        this.mBookmarksListAdapter = new BookmarksListAdapter(layoutInflater.getContext(), null);
        this.mBookmarksListView.setAdapter((ListAdapter) this.mBookmarksListAdapter);

        return viewInflate;
    }

    private Drawable getTabIcon(Context context, int i) {
        Drawable drawable = ContextCompat.getDrawable(context, i);
        return drawable == null ? null : DrawableCompat.wrap(drawable);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        this.mAnnotationsListAdapter.changeCursor(null);
        this.mBookmarksListAdapter.changeCursor(null);
    }

    public void setBook(Book book, long j) {
        this.mBook = book;
        this.mBookId = j;
        List<TocEntry> tocEntries = book.getTocEntries();
        this.mTocListAdapter.setEntries(tocEntries);
        this.mTocEmptyView.setVisibility(tocEntries.isEmpty() ? View.VISIBLE : View.GONE);
        updateAnnotations();
        updateBookmarks();
    }

    public void setListener(Listener listener) {
        this.mListener = listener;
    }

    public void updateAnnotations() {
        this.mAnnotationsListAdapter.changeCursor(this.mDatabase.query(AnnotationsTable.TABLE_NAME, new String[]{"_id", "created_date", AnnotationsTable.COLUMN_TEXT, "section_title", AnnotationsTable.COLUMN_COLOR, "url", AnnotationsTable.COLUMN_RANGE, AnnotationsTable.COLUMN_NOTE}, "book_id=?", new String[]{String.valueOf(this.mBookId)}, null, null, null));
    }

    public void updateBookmarks() {
        this.mBookmarksListAdapter.changeCursor(this.mDatabase.query(BookmarksTable.TABLE_NAME, new String[]{"_id", "section_title", "timestamp", BookmarksTable.COLUMN_PAGE, "position"}, "book_id=?", new String[]{String.valueOf(this.mBookId)}, null, null, null));
        this.mBookmarksListAdapter.notifyDataSetChanged();
    }

    public void setSelectedChapter(final TocEntry tocEntry) {
        if (tocEntry != this.mCurrentEntry) {
            this.mCurrentEntry = tocEntry;
            this.mTocListAdapter.setCurrentEntry(tocEntry);
            this.mTocListView.post(() -> {
                LinearLayoutManager linearLayoutManager = (LinearLayoutManager) mTocListView.getLayoutManager();
                int finalPosition = mTocListAdapter.getFinalPosition(tocEntry);
                if (finalPosition < linearLayoutManager.findFirstCompletelyVisibleItemPosition() || finalPosition > linearLayoutManager.findLastCompletelyVisibleItemPosition()) {
                    linearLayoutManager.scrollToPositionWithOffset(finalPosition, 0);
                }
            });
        }
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long j) {
        if (adapterView == this.mAnnotationsListView) {
            Cursor cursor = this.mAnnotationsListAdapter.getCursor();
            if (cursor.moveToPosition(i)) {
                this.mListener.onDrawerSelectedAnnotation(j, cursor.getString(5), cursor.getString(6));
            }
        } else if (adapterView == this.mBookmarksListView) {
            Cursor cursor2 = this.mBookmarksListAdapter.getCursor();
            if (cursor2.moveToPosition(i)) {
                this.mListener.onDrawerSelectedBookmark(cursor2.getString(4));
            }
        }
    }

    @Override
    public void onTocEntryClick(TocEntry tocEntry) {
        this.mListener.onDrawerSelectedChapter(tocEntry);
    }

    public void setInset(int i, int i2) {
        View view = getView();
        if (view != null) {
            view.findViewById(R.id.tabs_container).setPadding(0, i, 0, 0);
            this.mTocListView.setPadding(0, 0, 0, i2);
            this.mAnnotationsListView.setPadding(0, 0, 0, i2);
            this.mBookmarksListView.setPadding(0, 0, 0, i2);
        }
    }

    @Override
    public void onTabSelected(TabLayout.Tab tab) {
        if (this.mPager != null) {
            this.mPager.setCurrentItem(tab.getPosition());
        }
    }

    public void showBookmarkMenu(final long j, View view) {
        PopupMenu popupMenu = new PopupMenu(getActivity(), view);
        popupMenu.inflate(R.menu.reader_drawer_bookmark_more);
        popupMenu.setOnMenuItemClickListener(menuItem -> {
            if (menuItem.getItemId() == R.id.remove) {
                new BookmarkManager(mDatabase).deleteBookmark(j);
                updateBookmarks();
                mListener.onDrawerInvalidatedBookmark();
            }
            return true;
        });
        popupMenu.show();
    }

    public void showAnnotationMenu(final long j, View view, final String str) {
        PopupMenu popupMenu = new PopupMenu(getActivity(), view);
        popupMenu.inflate(R.menu.reader_drawer_annotation_more);
        MenuItem notesItem = popupMenu.getMenu().findItem(R.id.notes);
        if (notesItem != null && str == null) {
            notesItem.setVisible(false);
        }
        popupMenu.setOnMenuItemClickListener(menuItem -> {
            int itemId = menuItem.getItemId();
            if (itemId == R.id.notes) {
                showAnnotationNotes(getActivity(), str);
            } else if (itemId == R.id.remove) {
                mListener.onDrawerDeleteAnnotation(j);
            }
            return true;
        });
        popupMenu.show();
    }

    private void showAnnotationNotes(Context context, String str) {
        View viewInflate = LayoutInflater.from(context).inflate(R.layout.dialog_notes_view, (ViewGroup) null);
        ((TextView) viewInflate.findViewById(R.id.notes)).setText(str);
        new AlertDialog.Builder(context).setView(viewInflate).setPositiveButton(R.string.annotations_notes_dialog_ok, null).show();
    }

    class AnnotationsListAdapter extends CursorAdapter {
        public AnnotationsListAdapter(Context context, Cursor cursor) {
            super(context, cursor, 0);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup viewGroup) {
            return LayoutInflater.from(context).inflate(R.layout.annotations_list_item, viewGroup, false);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            TextView textView = (TextView) view.findViewById(R.id.title);
            TextView textView2 = (TextView) view.findViewById(R.id.desc);
            ImageButton imageButton = (ImageButton) view.findViewById(R.id.menu);
            View notesContainer = view.findViewById(R.id.notes_container);
            TextView notesText = (TextView) view.findViewById(R.id.notes);

            final int position = cursor.getPosition();
            final long j = cursor.getLong(0);
            String snippet = cursor.getString(2);
            String title = cursor.getString(3);
            int color = cursor.getInt(4);
            final String notes = cursor.getString(7);

            textView.setText(title != null ? title : mBook.getTitle());
            textView2.setText(snippet != null ? snippet : getString(R.string.annotations_text_unavailable));

            ImageView colorBlock = (ImageView) view.findViewById(R.id.color_block);
            int argbColor = 0xFF000000 | color;
            GradientDrawable gradientDrawable = (GradientDrawable) ContextCompat.getDrawable(context, R.drawable.annotation_color).mutate();
            gradientDrawable.setColor(argbColor);
            gradientDrawable.setStroke(Utils.dpToPx(context, 1), ColorUtils.compositeColors(ContextCompat.getColor(context, R.color.color_circle_stroke_overlay), argbColor));
            colorBlock.setImageDrawable(gradientDrawable);

            notesContainer.setVisibility(notes != null ? View.VISIBLE : View.GONE);
            notesText.setText(notes);

            imageButton.setOnClickListener(v -> showAnnotationMenu(j, v, notes));
            view.setOnClickListener(v -> onItemClick(mAnnotationsListView, v, position, j));
        }
    }

    class BookmarksListAdapter extends CursorAdapter {
        public BookmarksListAdapter(Context context, Cursor cursor) {
            super(context, cursor, 0);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup viewGroup) {
            return LayoutInflater.from(context).inflate(R.layout.bookmarks_list_item, viewGroup, false);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            TextView textView = (TextView) view.findViewById(R.id.title);
            TextView textView2 = (TextView) view.findViewById(R.id.desc);
            ImageButton imageButton = (ImageButton) view.findViewById(R.id.menu);

            final int position = cursor.getPosition();
            final long j = cursor.getLong(0);
            String title = cursor.getString(1);
            String date = SimpleDateFormat.getDateInstance(1).format(new Date(cursor.getLong(2)));
            int pageIndex = cursor.getInt(3) + 1;

            textView.setText(title);
            if (pageIndex != 0) { // 0 because -1 + 1 = 0
                textView2.setText(context.getString(R.string.bookmark_detail_line, pageIndex, date));
            } else {
                textView2.setText(date);
            }

            imageButton.setOnClickListener(v -> showBookmarkMenu(j, v));
            view.setOnClickListener(v -> onItemClick(mBookmarksListView, v, position, j));
        }
    }

    private class TabsAdapter extends PagerAdapter {
        @Override
        public int getCount() { return 3; }

        @Override
        public boolean isViewFromObject(View view, Object obj) { return view == obj; }

        private TabsAdapter() {}

        @Override
        public Object instantiateItem(ViewGroup viewGroup, int i) {
            BaseAdapter baseAdapter = null;
            View view = null;
            if (i == 0) {
                view = mTocTabContainer;
            } else if (i == 1) {
                view = mAnnotationsTabContainer;
                baseAdapter = mAnnotationsListAdapter;
            } else if (i == 2) {
                view = mBookmarksTabContainer;
                baseAdapter = mBookmarksListAdapter;
            }
            if (view != null) viewGroup.addView(view);
            if (baseAdapter != null) baseAdapter.notifyDataSetChanged();
            return view;
        }

        @Override
        public void destroyItem(ViewGroup viewGroup, int i, Object obj) {
            viewGroup.removeView((View) obj);
        }
    }
}