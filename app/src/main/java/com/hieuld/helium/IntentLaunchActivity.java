package com.hieuld.helium;

import android.app.ProgressDialog;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.hieuld.helium.db.BooksTable;
import com.hieuld.helium.db.DatabaseProvider;
import com.hieuld.helium.library.LibraryManager;
import com.hieuld.helium.util.Utils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class IntentLaunchActivity extends BaseActivity {
    private static final String TAG = "IntentLaunchActivity";
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setRequiresPermissions(false);
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            finish();
            return;
        }

        Intent intent = getIntent();
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) {
            finish();
            return;
        }

        Uri data = intent.getData();
        if (data == null) {
            finish();
            return;
        }

        String filePath = getFilePathFromUri(data);
        if (filePath != null && startWithFilePath(filePath)) {
            finish();
            return;
        }

        mExecutor.execute(() -> processUriStream(data, filePath));
    }

    private void processUriStream(Uri data, String filePath) {
        String hash = null;
        boolean success = false;

        try (InputStream in = getContentResolver().openInputStream(data)) {
            if (in == null) {
                showToastErrorAndFinish(R.string.intent_launch_cant_open_book_invalid_stream);
                return;
            }
            try (BufferedInputStream bis = new BufferedInputStream(in)) {
                hash = Utils.md5Hex(bis);
                success = true;
            }
        } catch (IOException | SecurityException e) {
            e.printStackTrace();
            showToastErrorAndFinish(R.string.intent_launch_cant_open_book_invalid_stream);
            return;
        }

        if (success && hash != null) {
            final String finalHash = hash;
            mHandler.post(() -> {
                if (startWithHash(finalHash)) {
                    finish();
                } else {
                    scanAndStart(filePath, data, finalHash);
                }
            });
        }
    }

    private void showToastErrorAndFinish(int resId) {
        mHandler.post(() -> {
            Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void scanAndStart(String filePath, Uri uri, String hash) {
        final ProgressDialog dialog = new ProgressDialog(this);
        dialog.setMessage(getString(R.string.intent_launch_scanning));
        dialog.show();

        mExecutor.execute(() -> {
            LibraryManager.ScanResult result = null;
            SQLiteDatabase db = DatabaseProvider.getDatabase(this);
            LibraryManager library = new LibraryManager(this, db);

            try {
                if (filePath != null) {
                    result = library.scanBook(filePath, 0, hash, System.currentTimeMillis());
                } else if (uri != null) {
                    String copiedPath = saveUriToFile(uri);
                    if (copiedPath != null) {
                        result = library.scanBook(copiedPath, LibraryManager.FLAG_COPIED, hash, System.currentTimeMillis());
                    }
                }
            } catch (Throwable t) {
                Log.e(TAG, "Scan failed", t);
            }

            final LibraryManager.ScanResult finalResult = result;
            mHandler.post(() -> {
                try {
                    dialog.dismiss();
                } catch (IllegalArgumentException ignored) {}

                if (finalResult != null && finalResult.exception == null) {
                    startWithId(finalResult.id, false);
                } else {
                    Toast.makeText(this, R.string.intent_launch_cant_open_book_load_failed, Toast.LENGTH_SHORT).show();
                }
                finish();
            });
        });
    }

    private String saveUriToFile(Uri uri) {
        try {
            File tempFile = File.createTempFile("copied_", ".epub", getFilesDir());
            String path = tempFile.getAbsolutePath();
            try (InputStream in = getContentResolver().openInputStream(uri);
                 BufferedInputStream bis = new BufferedInputStream(in);
                 BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(path))) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = bis.read(buffer)) != -1) {
                    bos.write(buffer, 0, bytesRead);
                }
                return path;
            }
        } catch (IOException | SecurityException e) {
            e.printStackTrace();
            return null;
        }
    }

    private boolean startWithFilePath(String path) {
        try (Cursor c = DatabaseProvider.getDatabase(this).query(
                BooksTable.TABLE_NAME, new String[]{"_id"},
                "file_path=?", new String[]{path}, null, null, null)) {
            return c.moveToFirst() && startWithId(c.getLong(0), true);
        }
    }

    private boolean startWithHash(String hash) {
        try (Cursor c = DatabaseProvider.getDatabase(this).query(
                BooksTable.TABLE_NAME, new String[]{"_id"},
                "hash=?", new String[]{hash}, null, null, null)) {
            return c.moveToFirst() && startWithId(c.getLong(0), true);
        }
    }

    private boolean startWithId(long id, boolean fromDbCheck) {
        if (fromDbCheck) {
            SQLiteDatabase db = DatabaseProvider.getDatabase(this);
            try (Cursor c = db.query(BooksTable.TABLE_NAME, new String[]{"hidden"},
                    "_id=?", new String[]{String.valueOf(id)}, null, null, null)) {
                if (c.moveToFirst() && c.getInt(0) == 1) {
                    if (db.delete(BooksTable.TABLE_NAME, "_id=?", new String[]{String.valueOf(id)}) != 1) {
                        Log.e(TAG, "Failed to delete hidden book entry to re-add.");
                    }
                    return false;
                }
            }
        }

        Intent intent = new Intent(this, ReaderActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("book_id", id)
                .putExtra(ReaderActivity.EXTRA_LAUNCH_SOURCE, ReaderActivity.LAUNCH_SOURCE_INTENT);
        startActivity(intent);
        return true;
    }

    private String getFilePathFromUri(Uri uri) {
        if ("file".equals(uri.getScheme())) {
            return uri.getPath();
        }
        if (!"media".equals(uri.getAuthority())) {
            return null;
        }
        try (Cursor c = getContentResolver().query(uri, new String[]{"_data"}, null, null, null)) {
            if (c != null && c.moveToFirst() && c.getColumnCount() > 0) {
                return c.getString(0);
            }
        } catch (SecurityException ignored) {}
        return null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mExecutor.shutdownNow();
    }
}
