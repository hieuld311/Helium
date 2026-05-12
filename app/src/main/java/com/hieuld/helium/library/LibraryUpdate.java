package com.hieuld.helium.library;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.provider.MediaStore;
import android.util.Log;

import androidx.core.content.ContextCompat;
import androidx.core.os.EnvironmentCompat;

import com.hieuld.helium.db.BooksTable;
import com.hieuld.helium.db.DatabaseProvider;
import com.hieuld.helium.exceptions.BookLoadException;
import com.hieuld.helium.util.Utils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class LibraryUpdate extends Thread {
    private static final String TAG = "LibraryUpdate";
    private static LibraryUpdate sCurrentUpdate;
    private static final Object sCurrentUpdateLock = new Object();

    private volatile boolean mCancelled;
    private final Context mContext;
    private final boolean mForced;
    private long mGroupTime;
    private final Handler mHandler;
    private com.hieuld.helium.library.LibraryManager mLibrary;
    private File mLibraryFolder;
    private final Listener mListener;
    private Result mResult;
    private Map<String, Boolean> mScanFailureRetryMap;
    private boolean mUseMediaStore;

    public interface Listener {
        void onLibraryUpdateAddedBook();
        void onLibraryUpdateDone(Result result);
        void onLibraryUpdateFoundBook();
    }

    private LibraryUpdate(Context context, Listener listener, boolean force) {
        super(TAG);
        this.mContext = context;
        this.mHandler = new Handler(Looper.getMainLooper());
        this.mUseMediaStore = !force;
        this.mForced = force;
        this.mListener = listener;
    }

    @Override
    public void run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
        Result finalResult;
        try {
            finalResult = runUpdate();
        } catch (Throwable t) {
            Log.e(TAG, "Update failed", t);
            finalResult = new Result();
        }

        final Result resultToReturn = finalResult;
        this.mHandler.post(() -> {
            if (mListener != null) {
                mListener.onLibraryUpdateDone(resultToReturn);
            }
        });

        synchronized (sCurrentUpdateLock) {
            sCurrentUpdate = null;
        }
    }

    private Result runUpdate() throws Throwable {
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageLegacy()) {
            if (!Environment.isExternalStorageManager()) {
                return new Result();
            }
        } else if (ContextCompat.checkSelfPermission(this.mContext, "android.permission.READ_EXTERNAL_STORAGE") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return new Result();
        }

        SQLiteDatabase db = DatabaseProvider.getDatabase(this.mContext);
        long startTime = System.currentTimeMillis();
        this.mGroupTime = startTime;

        this.mLibrary = new LibraryManager(this.mContext, db);
        this.mScanFailureRetryMap = this.mLibrary.getScanFailureRetryMap();

        Iterator<String> it = this.mScanFailureRetryMap.keySet().iterator();
        while (it.hasNext()) {
            String path = it.next();
            if (!new File(path).exists()) {
                it.remove();
                this.mLibrary.clearScanFailure(path);
            }
        }

        String libraryPath = getLibraryPath(db);
        if (libraryPath != null) {
            try {
                this.mLibraryFolder = new File(libraryPath).getCanonicalFile();
            } catch (IOException e) {
                e.printStackTrace();
                return new Result();
            }
        }

        this.mResult = new Result();
        if (this.mUseMediaStore) {
            addBooksFromStore("external", db);
        }

        if (!this.mUseMediaStore) {
            if (this.mLibraryFolder != null) {
                addBooksFromFolder(this.mLibraryFolder, db);
            } else {
                String state = Environment.getExternalStorageState();
                if (Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
                    addBooksFromFolder(Environment.getExternalStorageDirectory(), db);
                } else {
                    Log.d(TAG, "External storage is not available.");
                }
            }
        }

        try (Cursor cursor = db.query(BooksTable.TABLE_NAME,
                new String[]{"_id", "file_path", BooksTable.COLUMN_COVER, BooksTable.COLUMN_TITLE, BooksTable.COLUMN_HASH, "hidden", BooksTable.COLUMN_LIBRARY_VERSION},
                null, null, null, null, null)) {

            while (cursor.moveToNext()) {
                if (this.mCancelled) {
                    Log.d(TAG, "Library update cancelled.");
                    return new Result();
                }

                long id = cursor.getLong(0);
                String path = cursor.getString(1);
                int version = cursor.getInt(6);

                File bookFile = new File(path);
                String storageState = EnvironmentCompat.getStorageState(bookFile);

                if (Environment.MEDIA_MOUNTED.equals(storageState) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(storageState)) {
                    if (!this.mForced || bookFile.exists()) {
                        if (version < 3) {
                            Log.d(TAG, "Upgrading book " + id + " from library version " + version);
                            upgradeIfNecessary(db, version, cursor);
                            ContentValues values = new ContentValues();
                            values.put(BooksTable.COLUMN_LIBRARY_VERSION, 3);
                            db.update(BooksTable.TABLE_NAME, values, "_id=?", new String[]{String.valueOf(id)});
                        }
                    } else {
                        Log.d(TAG, "Deleting " + id + " due to forced refresh and missing book.");
                        db.delete(BooksTable.TABLE_NAME, "_id=?", new String[]{String.valueOf(id)});
                        this.mResult.removedIds.add(id);
                    }
                }
            }
        }

        Log.d(TAG, "Library update from " + (this.mUseMediaStore ? "media store" : "filesystem") +
                " took " + (System.currentTimeMillis() - startTime) + "ms, " + this.mResult);

        return this.mResult;
    }

    private String getLibraryPath(SQLiteDatabase db) {
        try (Cursor cursor = db.rawQuery("SELECT folder FROM library_folders", null)) {
            if (cursor.moveToFirst()) {
                return cursor.getString(0);
            }
            return null;
        }
    }

    private void upgradeIfNecessary(SQLiteDatabase db, int version, Cursor cursor) throws Throwable {
        long id = cursor.getLong(0);
        String path = cursor.getString(1);
        String coverPath = cursor.getString(2);
        String hash = cursor.getString(4);

        if (version < 1) {
            if (hash == null) {
                Log.d(TAG, "Computing missing hash for book '" + cursor.getString(3) + "'");
                hash = getBookHash(path);
                if (hash != null) {
                    ContentValues values = new ContentValues();
                    values.put(BooksTable.COLUMN_HASH, hash);
                    db.update(BooksTable.TABLE_NAME, values, "_id=?", new String[]{String.valueOf(id)});
                }
            }
            if (coverPath != null) {
                if (!new File(coverPath).exists()) {
                    Log.d(TAG, "Missing cover for book '" + cursor.getString(3) + "', regenerating...");
                    this.mLibrary.updateBookCover(path, hash);
                } else {
                    String cacheDir = this.mContext.getCacheDir().getAbsolutePath();
                    if (coverPath.startsWith(cacheDir)) {
                        String suffix = coverPath.substring(cacheDir.length() + 1);
                        File oldFile = new File(coverPath);
                        File newFile = new File(this.mContext.getFilesDir(), suffix);

                        if (Utils.copyFile(oldFile, newFile)) {
                            ContentValues values = new ContentValues();
                            values.put(BooksTable.COLUMN_COVER, newFile.getAbsolutePath());
                            db.update(BooksTable.TABLE_NAME, values, "_id=?", new String[]{String.valueOf(id)});
                            oldFile.delete();
                        } else {
                            Log.e(TAG, "Failed to copy " + oldFile.getAbsolutePath() + " to " + newFile.getAbsolutePath());
                        }
                    }
                }
            }
        }
        if (version < 3 && coverPath == null) {
            this.mLibrary.updateBookCover(path, hash);
        }
    }

    public void cancel() {
        this.mCancelled = true;
    }

    private void addBooksFromStore(String volumeName, SQLiteDatabase db) throws Throwable {
        try (Cursor cursor = this.mContext.getContentResolver().query(
                MediaStore.Files.getContentUri(volumeName),
                new String[]{"_data"},
                "_data LIKE ?", new String[]{"%.epub"}, null)) {

            if (cursor == null) {
                Log.e(TAG, "Media store query returned null. Reverting to filesystem.");
                this.mUseMediaStore = false;
                return;
            }
            while (cursor.moveToNext()) {
                if (this.mCancelled) return;
                String path = cursor.getString(0);
                File file = new File(path);
                if (file.exists() && !file.isDirectory()) {
                    addBookIfMissing(path, db);
                }
            }
        }
    }

    private void addBooksFromFolder(File folder, SQLiteDatabase db) throws Throwable {
        File[] files = folder.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (this.mCancelled) return;
            if (file.canRead()) {
                if (file.isDirectory()) {
                    addBooksFromFolder(file, db);
                } else if (file.getAbsolutePath().toLowerCase().endsWith(".epub")) {
                    addBookIfMissing(file.getAbsolutePath(), db);
                }
            }
        }
    }

    private void addBookIfMissing(String path, SQLiteDatabase db) throws Throwable {
        if (hasFilePathBeenAdded(path, db)) return;

        if (this.mLibraryFolder != null) {
            try {
                String canonicalPath = new File(path).getCanonicalPath();
                String folderPath = this.mLibraryFolder.getAbsolutePath();
                if (!folderPath.endsWith(File.separator)) {
                    folderPath += File.separator;
                }
                if (!canonicalPath.startsWith(folderPath)) return;
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }

        boolean isRetry = false;
        if (this.mScanFailureRetryMap.containsKey(path) && !this.mForced) {
            if (!this.mScanFailureRetryMap.get(path)) return;
            Log.d(TAG, "Silently retrying " + path + ".");
            isRetry = true;
        }

        String hash = getBookHash(path);
        if (hash == null) {
            Log.e(TAG, "Hash failed for path: " + path);
            return;
        }

        try (Cursor cursor = db.query(BooksTable.TABLE_NAME,
                new String[]{"_id", BooksTable.COLUMN_COPIED, "file_path"},
                "hash=?", new String[]{hash}, null, null, null)) {

            if (cursor.moveToFirst()) {
                long id = cursor.getLong(0);
                boolean isCopied = cursor.getInt(1) > 0;
                String oldPath = cursor.getString(2);
                File oldFile = new File(oldPath);

                if (isCopied || !oldFile.exists()) {
                    ContentValues values = new ContentValues();
                    values.put("file_path", path);
                    values.put("folder", path.substring(0, path.lastIndexOf('/')));
                    if (isCopied) values.put(BooksTable.COLUMN_COPIED, false);

                    db.update(BooksTable.TABLE_NAME, values, "_id=?", new String[]{String.valueOf(id)});
                    if (isCopied && !oldFile.delete()) {
                        Log.e(TAG, "Failed to delete copied book " + oldPath);
                    }
                    return;
                }
            }
        }

        if (!isRetry && this.mListener != null) {
            this.mHandler.post(this.mListener::onLibraryUpdateFoundBook);
        }

        if (addBookFromFile(path, hash)) {
            if (this.mListener != null) {
                this.mHandler.post(this.mListener::onLibraryUpdateAddedBook);
            }
            this.mResult.successfulCount++;
        } else {
            this.mResult.failedCount++;
        }
    }

    private boolean hasFilePathBeenAdded(String path, SQLiteDatabase db) {
        try (Cursor cursor = db.query(BooksTable.TABLE_NAME, new String[]{"_id"}, "file_path=?", new String[]{path}, null, null, null)) {
            return cursor.getCount() > 0;
        }
    }

    private String getBookHash(String path) {
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(path))) {
            return Utils.md5Hex(bis);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private boolean addBookFromFile(String path, String hash) throws Exception {
        try {
            LibraryManager.ScanResult result = this.mLibrary.scanBook(path, 0, hash, this.mGroupTime);
            if (result.exception == null) return true;
            this.mResult.failedBooks.add(new FailedBook(path, result.exception));
            return false;
        } catch (Exception e) {
            this.mLibrary.reportScanFailed(path);
            throw e;
        }
    }

    public static LibraryUpdate run(Context context, Listener listener, boolean force) {
        LibraryUpdate update = new LibraryUpdate(context, listener, force);
        synchronized (sCurrentUpdateLock) {
            sCurrentUpdate = update;
        }
        update.start();
        return update;
    }

    public static void cancelCurrentUpdate() {
        synchronized (sCurrentUpdateLock) {
            if (sCurrentUpdate != null) {
                sCurrentUpdate.cancel();
            }
        }
    }

    public static class Result implements Serializable {
        public int failedCount;
        public int successfulCount;
        public List<Long> removedIds = new ArrayList<>();
        public List<FailedBook> failedBooks = new ArrayList<>();

        @Override
        public String toString() {
            return this.successfulCount + " added, " + this.failedCount + " failed";
        }
    }

    public static class FailedBook implements Serializable {
        public BookLoadException exception;
        public String filename;

        public FailedBook(String filename, BookLoadException exception) {
            this.filename = filename;
            this.exception = exception;
        }
    }
}