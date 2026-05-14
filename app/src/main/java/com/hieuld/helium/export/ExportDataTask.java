package com.hieuld.helium.export;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.TextUtils;

import androidx.core.content.FileProvider;

import com.hieuld.helium.R;
import com.hieuld.helium.db.AnnotationsTable;
import com.hieuld.helium.db.BookmarksTable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;

public class ExportDataTask extends AsyncTask<Void, Void, ExportDataTask.Result> {
    public static final int RES_ERR_ANNOTATIONS_NEED_UPGRADE = 1;
    public static final int RES_ERR_OTHER = 2;

    private final long mBookId;
    private final String mBookTitle;
    private final boolean mBookmarks;
    private final Context mContext;
    private final WeakReference<Context> mContextToLaunch;
    private final SQLiteDatabase mDb;
    private final ExportDataFormatter mFormatter;
    private final boolean mHighlights;

    public ExportDataTask(Context context, ExportDataFormatter formatter, long bookId, String bookTitle,
                          SQLiteDatabase db, boolean highlights, boolean bookmarks) {
        mContext = context.getApplicationContext();
        mContextToLaunch = new WeakReference<>(context);
        mFormatter = formatter;
        mBookId = bookId;
        mBookTitle = bookTitle;
        mDb = db;
        mHighlights = highlights;
        mBookmarks = bookmarks;
    }

    @Override
    public Result doInBackground(Void... params) {
        Cursor annotations = null;
        Cursor bookmarks = null;
        try {
            if (mHighlights) {
                annotations = queryAnnotations();
                if (hasAnnotationMissingText(annotations)) {
                    return new Result(RES_ERR_ANNOTATIONS_NEED_UPGRADE);
                }
                annotations.moveToPosition(-1);
            }
            if (mBookmarks) {
                bookmarks = queryBookmarks();
            }

            File dir = new File(mContext.getFilesDir(), "export");
            if (!dir.exists() && !dir.mkdirs()) {
                return new Result(RES_ERR_OTHER);
            }
            File outFile = new File(dir, buildFileName());
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8)) {
                mFormatter.format(mContext, mBookTitle, annotations, bookmarks, writer);
            }
            Uri uri = FileProvider.getUriForFile(mContext, mContext.getPackageName() + ".fileprovider", outFile);
            return new Result(uri);
        } catch (Exception e) {
            return new Result(RES_ERR_OTHER);
        } finally {
            if (annotations != null) {
                annotations.close();
            }
            if (bookmarks != null) {
                bookmarks.close();
            }
        }
    }

    @Override
    public void onPostExecute(Result result) {
        Context context = mContextToLaunch.get();
        if (context == null) {
            return;
        }
        if (result.uri == null) {
            int message = result.error == RES_ERR_ANNOTATIONS_NEED_UPGRADE
                    ? R.string.export_failed_annotations_need_upgrade
                    : R.string.export_failed_unknown;
            new AlertDialog.Builder(context)
                    .setMessage(message)
                    .setPositiveButton(R.string.export_failed_ok, (DialogInterface.OnClickListener) null)
                    .show();
            return;
        }

        Intent intent = new Intent(Intent.ACTION_SEND)
                .setType(mFormatter.getMimeType())
                .putExtra(Intent.EXTRA_STREAM, result.uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.action_book_export_data)));
    }

    private Cursor queryAnnotations() {
        return mDb.query(
                AnnotationsTable.TABLE_NAME,
                null,
                AnnotationsTable.COLUMN_BOOK_ID + "=?",
                new String[]{Long.toString(mBookId)},
                null,
                null,
                AnnotationsTable.COLUMN_CREATED_DATE + " ASC");
    }

    private Cursor queryBookmarks() {
        return mDb.query(
                BookmarksTable.TABLE_NAME,
                null,
                BookmarksTable.COLUMN_BOOK_ID + "=?",
                new String[]{Long.toString(mBookId)},
                null,
                null,
                BookmarksTable.COLUMN_TIMESTAMP + " ASC");
    }

    private static boolean hasAnnotationMissingText(Cursor cursor) {
        int textIndex = cursor.getColumnIndexOrThrow(AnnotationsTable.COLUMN_TEXT);
        while (cursor.moveToNext()) {
            if (TextUtils.isEmpty(cursor.getString(textIndex))) {
                return true;
            }
        }
        return false;
    }

    private String buildFileName() {
        String base = mBookTitle == null ? "book" : mBookTitle.replaceAll("[\\\\/:*?\"<>|]+", "_").trim();
        if (base.isEmpty()) {
            base = "book";
        }
        return base + "." + mFormatter.getFileExtension();
    }

    public static class Result {
        final int error;
        final Uri uri;

        Result(Uri uri) {
            this.uri = uri;
            this.error = 0;
        }

        Result(int error) {
            this.error = error;
            this.uri = null;
        }
    }
}
