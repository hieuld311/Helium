package com.hieuld.helium.export;

import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;

import com.hieuld.helium.R;
import com.hieuld.helium.db.AnnotationsTable;
import com.hieuld.helium.db.BookmarksTable;

import java.io.IOException;
import java.io.OutputStreamWriter;

public class PlainTextExportDataFormatter implements ExportDataFormatter {
    @Override
    public String getFileExtension() {
        return "txt";
    }

    @Override
    public String getMimeType() {
        return "text/plain";
    }

    @Override
    public void format(Context context, String title, Cursor annotations, Cursor bookmarks, OutputStreamWriter writer) throws IOException {
        writer.write(title + "\n");
        writer.write("----------------------------------------------\n\n");
        if (annotations != null) {
            writer.write(context.getString(R.string.export_doc_highlights_header) + ":\n");
            writeAnnotations(context, annotations, writer);
        }
        if (bookmarks != null) {
            writer.write(context.getString(R.string.export_doc_bookmarks_header) + ":\n");
            writeBookmarks(bookmarks, writer);
        }
    }

    private static void writeAnnotations(Context context, Cursor cursor, OutputStreamWriter writer) throws IOException {
        String noteLabel = context.getString(R.string.export_doc_note);
        int textIndex = cursor.getColumnIndexOrThrow(AnnotationsTable.COLUMN_TEXT);
        int noteIndex = cursor.getColumnIndexOrThrow(AnnotationsTable.COLUMN_NOTE);
        int sectionIndex = cursor.getColumnIndexOrThrow(AnnotationsTable.COLUMN_SECTION_TITLE);
        while (cursor.moveToNext()) {
            writer.write("* " + value(cursor.getString(sectionIndex)) + "\n");
            writer.write("  \"" + value(cursor.getString(textIndex)) + "\"\n");
            String note = cursor.getString(noteIndex);
            if (!TextUtils.isEmpty(note)) {
                writer.write("  \n  " + noteLabel + "\n  " + note + "\n");
            }
            writer.write("\n\n");
        }
    }

    private static void writeBookmarks(Cursor cursor, OutputStreamWriter writer) throws IOException {
        int sectionIndex = cursor.getColumnIndexOrThrow(BookmarksTable.COLUMN_SECTION_TITLE);
        int pageIndex = cursor.getColumnIndexOrThrow(BookmarksTable.COLUMN_PAGE);
        while (cursor.moveToNext()) {
            writer.write("* " + value(cursor.getString(sectionIndex)) + "\n");
            writer.write("  Page: " + cursor.getInt(pageIndex) + "\n\n");
        }
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
