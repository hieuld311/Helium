package com.hieuld.helium.annotations;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Rect;
import android.os.Handler;
import androidx.preference.PreferenceManager;
import com.hieuld.helium.db.AnnotationsTable;
import com.hieuld.helium.db.SyncBaseColumns;
import java.util.UUID;

public class AnnotationController implements SelectionToolbarView.ToolbarListener {
    private static final String PREF_LAST_SELECTION_COLOR = "last_selection_color";
    private static final String PREF_LAST_SELECTION_TYPE = "last_selection_type";
    public static final int RESTORE_FLAG_UPGRADE_19 = 1;
    private static final long SCROLL_SETTLE_TIMEOUT = 300;
    private static final int STATE_ANNOTATION_SELECTED = 2;
    private static final int STATE_NONE = 0;
    private static final int STATE_TEXT_SELECTED = 1;
    private static final String TAG = "AnnotationController";
    public static final int TYPE_HIGHLIGHT = 0;
    public static final int TYPE_UNDERLINE = 1;

    private int mBoundsRequestIdPool;
    private Context mContext;
    private long mContextBookId;
    private String mContextUrl;
    private SQLiteDatabase mDatabase;
    private boolean mIgnoreSelection;
    private AnnotationListener mListener;
    private int mPendingBoundsRequestId;
    private AnnotationRenderer mRenderer;
    private String mSectionTitle;
    private int mSelectedAnnotationColor;
    private long mSelectedAnnotationId;
    private String mSelectedAnnotationNotes;
    private int mSelectedAnnotationType;
    private int mSelectionColor;
    private String mSelectionNotes;
    private SelectionToolbarView.State mSelectionToolbarState;
    private SelectionToolbarView mSelectionToolbarView;
    private int mSelectionType;
    private int mState;
    private final Runnable mScrollSettleRunnable = this::onScrollSettled;
    private final Handler mHandler = new Handler();

    public interface AnnotationListener {
        void onAnnotationsChanged();
    }

    public static class FinalizeResult {
        public boolean hasNotes;
        public long id;
        public int type;
    }

    public AnnotationController(Context context, SQLiteDatabase db, long bookId) {
        mContext = context;
        mDatabase = db;
        mContextBookId = bookId;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        mSelectionColor = prefs.getInt(PREF_LAST_SELECTION_COLOR, 0);
        mSelectionType = prefs.getInt(PREF_LAST_SELECTION_TYPE, 0);
        if (mSelectionColor == -1) {
            mSelectionColor = 16219260;
            mSelectionType = 1;
            prefs.edit().putInt(PREF_LAST_SELECTION_COLOR, mSelectionColor)
                    .putInt(PREF_LAST_SELECTION_TYPE, mSelectionType).apply();
        }
    }

    public void setContext(String url, AnnotationRenderer renderer) {
        cancel();
        mContextUrl = url;
        mRenderer = renderer;
    }

    public void setSectionTitle(String title) { mSectionTitle = title; }
    public void setListener(AnnotationListener listener) { mListener = listener; }

    public void setSelectionToolbarView(SelectionToolbarView view) {
        mSelectionToolbarView = view;
        view.setToolbarListener(this);
    }

    public void initRenderer() {
        restoreAnnotations();
        mRenderer.setSelectionColor(mSelectionColor);
    }

    public FinalizeResult finalizeAnnotation(String range, String text, int color) {
        if (mIgnoreSelection) return null;
        FinalizeResult result = new FinalizeResult();
        result.id = commitAnnotationToDatabase(mSelectionType, range, text, color, mSelectionNotes);
        result.type = mSelectionType;
        result.hasNotes = mSelectionNotes != null;
        mHandler.post(() -> {
            if (mListener != null) mListener.onAnnotationsChanged();
        });
        return result;
    }

    private long commitAnnotationToDatabase(int type, String range, String text, int color, String notes) {
        ContentValues values = new ContentValues();
        values.put("book_id", mContextBookId);
        values.put("url", mContextUrl);
        values.put(AnnotationsTable.COLUMN_RANGE, range);
        values.put(AnnotationsTable.COLUMN_TYPE, type);
        values.put(AnnotationsTable.COLUMN_TEXT, text);
        values.put("section_title", mSectionTitle);
        values.put(AnnotationsTable.COLUMN_COLOR, color);
        values.put(AnnotationsTable.COLUMN_NOTE, notes);
        values.put("created_date", System.currentTimeMillis());
        values.put(SyncBaseColumns._SYNC_ID, UUID.randomUUID().toString());
        return mDatabase.insert(AnnotationsTable.TABLE_NAME, null, values);
    }

    private void deleteAnnotationFromDatabase(long id) {
        mDatabase.delete(AnnotationsTable.TABLE_NAME, "_id=?", new String[]{String.valueOf(id)});
    }

    private void updateAnnotationInDatabase(long id, ContentValues values) {
        values.put(AnnotationsTable.COLUMN_EDITED_DATE, System.currentTimeMillis());
        mDatabase.update(AnnotationsTable.TABLE_NAME, values, "_id=?", new String[]{String.valueOf(id)});
    }

    public void deleteAnnotation(long id) {
        Cursor cursor = mDatabase.query(AnnotationsTable.TABLE_NAME, new String[]{"url"},
                "_id=?", new String[]{String.valueOf(id)}, null, null, null);
        if (!cursor.moveToFirst()) { cursor.close(); return; }
        String url = cursor.getString(0);
        cursor.close();
        deleteAnnotationFromDatabase(id);
        if (mRenderer != null && url.equals(mContextUrl)) {
            mRenderer.deleteAnnotation(id);
        }
        if (mListener != null) mListener.onAnnotationsChanged();
    }

    public boolean isBusy() { return mState != 0; }

    public void restoreAnnotations() {
        Cursor cursor = mDatabase.query(AnnotationsTable.TABLE_NAME,
                new String[]{"_id", AnnotationsTable.COLUMN_TYPE, AnnotationsTable.COLUMN_RANGE,
                        AnnotationsTable.COLUMN_COLOR, AnnotationsTable.COLUMN_NOTE, AnnotationsTable.COLUMN_TEXT},
                "book_id=? AND url=?", new String[]{String.valueOf(mContextBookId), mContextUrl},
                null, null, null);
        while (cursor.moveToNext()) {
            mRenderer.restoreAnnotation(cursor.getLong(0), cursor.getInt(1), cursor.getString(2),
                    cursor.getInt(3), cursor.getString(4) != null, cursor.getString(5) == null ? 1 : 0);
        }
        cursor.close();
    }

    public void selectAnnotation(long id, Rect rect, String text) {
        if (mState == 2 && mSelectedAnnotationId == id) return;
        Cursor cursor = mDatabase.query(AnnotationsTable.TABLE_NAME,
                new String[]{AnnotationsTable.COLUMN_TYPE, AnnotationsTable.COLUMN_COLOR, AnnotationsTable.COLUMN_NOTE},
                "_id=?", new String[]{String.valueOf(id)}, null, null, null);
        if (!cursor.moveToFirst()) { cursor.close(); return; }
        mState = 2;
        mSelectedAnnotationId = id;
        mSelectedAnnotationType = cursor.getInt(0);
        mSelectedAnnotationColor = cursor.getInt(1);
        mSelectedAnnotationNotes = cursor.getString(2);
        cursor.close();
        if (mSelectionToolbarView != null) {
            SelectionToolbarView.State state = new SelectionToolbarView.State();
            state.stateType = 1;
            state.annotationType = mSelectedAnnotationType;
            state.text = text;
            state.color = mSelectedAnnotationColor;
            state.notes = mSelectedAnnotationNotes;
            showSelectionToolbar(state, rect);
        }
        mRenderer.hideNoteMarkers();
    }

    public void deselectAnnotation() {
        mState = 0;
        if (mSelectionToolbarView != null) mSelectionToolbarView.hide();
        mRenderer.showNoteMarkers();
    }

    public boolean isAnnotationSelected() { return mState == 2; }

    public void deleteSelectedAnnotation() {
        mRenderer.deleteAnnotation(mSelectedAnnotationId);
        deleteAnnotationFromDatabase(mSelectedAnnotationId);
        deselectAnnotation();
        if (mListener != null) mListener.onAnnotationsChanged();
    }

    public void onSelectionCreated() {
        if (mState == 1) return;
        mState = 1;
        mSelectionNotes = null;
        mIgnoreSelection = false;
        mRenderer.hideNoteMarkers();
    }

    public void onSelectionStarted() {
        if (mSelectionToolbarView != null) mSelectionToolbarView.hide();
    }

    public void onSelectionStopped(Rect rect, String text) {
        if (mState != 1) return;
        mPendingBoundsRequestId = 0;
        mHandler.removeCallbacks(mScrollSettleRunnable);
        if (mSelectionToolbarView != null) {
            SelectionToolbarView.State state = new SelectionToolbarView.State();
            state.stateType = 0;
            state.annotationType = mSelectionType;
            state.text = text;
            state.color = mSelectionColor;
            state.notes = mSelectionNotes;
            showSelectionToolbar(state, rect);
        }
    }

    public void onSelectionDestroyed() {
        if (mState != 1) return;
        mState = 0;
        mPendingBoundsRequestId = 0;
        mHandler.removeCallbacks(mScrollSettleRunnable);
        if (mSelectionToolbarView != null) mSelectionToolbarView.hide();
        mSelectionToolbarState = null;
        mRenderer.showNoteMarkers();
        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                .putInt(PREF_LAST_SELECTION_COLOR, mSelectionColor)
                .putInt(PREF_LAST_SELECTION_TYPE, mSelectionType).apply();
    }

    private void showSelectionToolbar(SelectionToolbarView.State state, Rect rect) {
        if (state != null) mSelectionToolbarState = state;
        mSelectionToolbarView.show(state, rect);
    }

    public void cancelTextSelection() {
        if (mState != 1) return;
        mRenderer.clearSelection();
        onSelectionDestroyed();
    }

    public void cancel() {
        if (mState == 1) cancelTextSelection();
        else if (mState == 2) deselectAnnotation();
    }

    public void cancelMaybe() {
        if (mSelectionToolbarView.isInNotesDialog()) return;
        cancel();
    }

    public void notifyScroll() {
        int s = mState;
        if (s == 1 || s == 2) {
            if (mSelectionToolbarView != null) mSelectionToolbarView.hide();
            mHandler.removeCallbacks(mScrollSettleRunnable);
            mHandler.postDelayed(mScrollSettleRunnable, 300L);
        }
    }

    private void onScrollSettled() {
        int s = mState;
        if (s == 1 || s == 2) {
            int id = ++mBoundsRequestIdPool;
            mPendingBoundsRequestId = id;
            mRenderer.requestUpdatedBounds(id, s == 2 ? mSelectedAnnotationId : -1L);
        }
    }

    public void onBoundsUpdated(int requestId, Rect rect) {
        int s = mState;
        if ((s == 1 || s == 2) && requestId == mPendingBoundsRequestId && mPendingBoundsRequestId != 0) {
            if (mSelectionToolbarView != null && mSelectionToolbarState != null) {
                showSelectionToolbar(mSelectionToolbarState, rect);
            }
            mPendingBoundsRequestId = 0;
        }
    }

    @Override public void onDismiss() { mIgnoreSelection = true; cancel(); }

    @Override
    public void onTypeSet(int type) {
        if (mState == 1) { mSelectionType = type; return; }
        if (mState != 2) return;
        mSelectedAnnotationType = type;
        mRenderer.updateAnnotation(mSelectedAnnotationId, type, mSelectedAnnotationColor, mSelectedAnnotationNotes != null);
        ContentValues values = new ContentValues();
        values.put(AnnotationsTable.COLUMN_TYPE, type);
        values.put(AnnotationsTable.COLUMN_TYPE_TIMESTAMP, System.currentTimeMillis());
        updateAnnotationInDatabase(mSelectedAnnotationId, values);
    }

    @Override
    public void onColorPicked(int color) {
        if (mState == 1) { mSelectionColor = color; mRenderer.setSelectionColor(color); return; }
        if (mState != 2) return;
        mSelectedAnnotationColor = color;
        mRenderer.updateAnnotation(mSelectedAnnotationId, mSelectedAnnotationType, color, mSelectedAnnotationNotes != null);
        ContentValues values = new ContentValues();
        values.put(AnnotationsTable.COLUMN_COLOR, color);
        values.put(AnnotationsTable.COLUMN_COLOR_TIMESTAMP, System.currentTimeMillis());
        updateAnnotationInDatabase(mSelectedAnnotationId, values);
        if (mListener != null) mListener.onAnnotationsChanged();
    }

    @Override
    public void onNotesUpdated(String notes) {
        if (mState == 1) { mSelectionNotes = notes; return; }
        if (mState != 2) return;
        mSelectedAnnotationNotes = notes;
        mRenderer.updateAnnotation(mSelectedAnnotationId, mSelectedAnnotationType, mSelectedAnnotationColor, notes != null);
        ContentValues values = new ContentValues();
        values.put(AnnotationsTable.COLUMN_NOTE, notes);
        values.put(AnnotationsTable.COLUMN_NOTE_TIMESTAMP, System.currentTimeMillis());
        updateAnnotationInDatabase(mSelectedAnnotationId, values);
        if (mListener != null) mListener.onAnnotationsChanged();
    }

    @Override public void onAnnotationDelete() { deleteSelectedAnnotation(); }

    public boolean shouldInsertAnnotation() { return !mIgnoreSelection; }

    public void upgradeAnnotation19(long id, String text) {
        ContentValues values = new ContentValues();
        values.put(AnnotationsTable.COLUMN_TEXT, text);
        mDatabase.update(AnnotationsTable.TABLE_NAME, values, "_id=?", new String[]{String.valueOf(id)});
        if (mListener != null) mListener.onAnnotationsChanged();
    }
}
