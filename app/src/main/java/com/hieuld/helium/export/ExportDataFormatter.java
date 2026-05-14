package com.hieuld.helium.export;

import android.content.Context;
import android.database.Cursor;

import java.io.IOException;
import java.io.OutputStreamWriter;

public interface ExportDataFormatter {
    void format(Context context, String title, Cursor annotations, Cursor bookmarks, OutputStreamWriter writer) throws IOException;

    String getFileExtension();

    String getMimeType();
}
