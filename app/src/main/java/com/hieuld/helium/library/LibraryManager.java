package com.hieuld.helium.library;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.util.Log;

import androidx.palette.graphics.Palette;

import com.hieuld.helium.book.Book;
import com.hieuld.helium.book.EPubBook;
import com.hieuld.helium.book.EPubPageMap;
import com.hieuld.helium.db.BooksTable;
import com.hieuld.helium.db.ScanFailuresTable;
import com.hieuld.helium.exceptions.BookLoadException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LibraryManager {
    public static final int FLAG_COPIED = 1;
    private static final String TAG = "LibraryManager";
    private final Context mContext;
    private final SQLiteDatabase mDb;

    public LibraryManager(Context context, SQLiteDatabase db) {
        this.mContext = context;
        this.mDb = db;
    }

    public ScanResult scanBook(String path, int flags, String hash, long groupDate) {
        ScanResult result = scanBookInternal(path, flags, hash, groupDate);
        if (result.exception != null && !(result.exception instanceof DatabaseError)) {
            reportScanFailed(path);
        } else {
            clearScanFailure(path);
        }
        return result;
    }

    private ScanResult scanBookInternal(String path, int flags, String hash, long groupDate) {
        EPubPageMap pageMap = null;
        try {
            Book book = Book.create(path);
            Bitmap coverBitmap = book.getCoverBitmap(this.mContext);

            ContentValues values = new ContentValues();
            values.put("file_path", path);
            values.put("folder", path.substring(0, path.lastIndexOf('/')));
            values.put(BooksTable.COLUMN_TITLE, book.getTitle());
            values.put(BooksTable.COLUMN_CREATOR, book.getCreator());

            if (coverBitmap != null) {
                String newCoverPath = getNewCoverPath(hash);
                saveBitmapToPath(coverBitmap, newCoverPath);
                values.put(BooksTable.COLUMN_COVER, newCoverPath);
                values.put(BooksTable.COLUMN_COVER_BACKGROUND, getCoverBackgroundColor(coverBitmap));
                coverBitmap.recycle();
            } else {
                values.put(BooksTable.COLUMN_COVER_BACKGROUND, 0);
            }

            values.put("added_date", System.currentTimeMillis());
            values.put(BooksTable.COLUMN_GROUP_ADDED_DATE, groupDate);
            values.put(BooksTable.COLUMN_COPIED, (flags & FLAG_COPIED) > 0);
            values.put(BooksTable.COLUMN_HASH, hash);

            if (book instanceof EPubBook) {
                pageMap = EPubPageMap.generate((EPubBook) book);
                if (pageMap == null) {
                    Log.e(TAG, "Page map generation failed!");
                    book.close();
                    return new ScanResult(new PageMapGenerationFailedException());
                }
            }
            book.close();

            long insertId = this.mDb.insert(BooksTable.TABLE_NAME, null, values);
            if (insertId > -1 && pageMap != null) {
                EPubPageMap.writeToCache(pageMap, insertId, this.mDb);
            }

            if (insertId == -1) {
                return new ScanResult(new DatabaseError());
            }
            return new ScanResult(insertId);

        } catch (BookLoadException e) {
            e.printStackTrace();
            return new ScanResult(e);
        }
    }

    public void updateBookCover(String path, String hash) {
        try {
            Book book = Book.create(path);
            Bitmap coverBitmap = book.getCoverBitmap(this.mContext);
            String newCoverPath = null;

            if (coverBitmap != null) {
                newCoverPath = getNewCoverPath(hash);
                saveBitmapToPath(coverBitmap, newCoverPath);
            }

            ContentValues values = new ContentValues();
            values.put(BooksTable.COLUMN_COVER, newCoverPath);
            values.put(BooksTable.COLUMN_COVER_BACKGROUND, coverBitmap != null ? getCoverBackgroundColor(coverBitmap) : 0);

            this.mDb.update(BooksTable.TABLE_NAME, values, "file_path=?", new String[]{path});

            if (coverBitmap != null) {
                coverBitmap.recycle();
            }
        } catch (BookLoadException e) {
            e.printStackTrace();
        }
    }

    private int getCoverBackgroundColor(Bitmap bitmap) {
        Palette palette = Palette.from(bitmap).generate();
        int darkVibrant = palette.getDarkVibrantColor(0);
        return darkVibrant == 0 ? palette.getDarkMutedColor(0) : darkVibrant;
    }

    public Map<String, Boolean> getScanFailureRetryMap() {
        Map<String, Boolean> map = new HashMap<>();
        try (Cursor cursor = this.mDb.query(ScanFailuresTable.TABLE_NAME,
                new String[]{"file_path", ScanFailuresTable.COLUMN_LAST_ATTEMPT_VER},
                null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                map.put(cursor.getString(0), cursor.getInt(1) < 99);
            }
        }
        return map;
    }

    public void reportScanFailed(String path) {
        try (Cursor cursor = this.mDb.query(ScanFailuresTable.TABLE_NAME,
                new String[]{"_id"}, "file_path=?", new String[]{path},
                null, null, null)) {

            ContentValues values = new ContentValues();
            values.put(ScanFailuresTable.COLUMN_LAST_ATTEMPT_VER, 99);

            if (cursor.moveToFirst()) {
                this.mDb.update(ScanFailuresTable.TABLE_NAME, values, "_id=?", new String[]{String.valueOf(cursor.getLong(0))});
            } else {
                values.put("file_path", path);
                this.mDb.insert(ScanFailuresTable.TABLE_NAME, null, values);
            }
        }
    }

    public void clearScanFailure(String path) {
        this.mDb.delete(ScanFailuresTable.TABLE_NAME, "file_path=?", new String[]{path});
    }

    private String getNewCoverPath(String hash) {
        String uniqueSuffix = UUID.randomUUID().toString() + "." + System.currentTimeMillis();
        return new File(this.mContext.getFilesDir(), hash + "_" + uniqueSuffix + ".jpg").getAbsolutePath();
    }

    private boolean saveBitmapToPath(Bitmap bitmap, String path) {
        try (FileOutputStream out = new FileOutputStream(path)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static class ScanResult {
        public BookLoadException exception;
        public long id;

        public ScanResult(long id) {
            this.id = id;
        }

        public ScanResult(BookLoadException exception) {
            this.exception = exception;
        }
    }

    private static class DatabaseError extends BookLoadException {}
    private static class PageMapGenerationFailedException extends BookLoadException {}
}