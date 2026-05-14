package com.hieuld.helium.export;

import android.content.Context;
import android.database.Cursor;
import android.text.Html;
import android.text.TextUtils;

import com.hieuld.helium.R;
import com.hieuld.helium.db.AnnotationsTable;
import com.hieuld.helium.db.BookmarksTable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class HtmlExportDataFormatter implements ExportDataFormatter {
    @Override
    public String getFileExtension() {
        return "html";
    }

    @Override
    public String getMimeType() {
        return "text/html";
    }

    @Override
    public void format(Context context, String title, Cursor annotations, Cursor bookmarks, OutputStreamWriter writer) throws IOException {
        writer.write("<html><head>\n<style type='text/css'>\n");
        writeAsset(context, writer, "export_html_style.css");
        writer.write("</style>\n</head>\n<body>\n");
        writer.write("<h1>" + Html.escapeHtml(title) + "</h1>\n");
        if (annotations != null) {
            writer.write("<h2>" + Html.escapeHtml(context.getString(R.string.export_doc_highlights_header)) + "</h2>\n");
            writeAnnotations(context, annotations, writer);
        }
        if (bookmarks != null) {
            writer.write("<h2>" + Html.escapeHtml(context.getString(R.string.export_doc_bookmarks_header)) + "</h2>\n");
            writeBookmarks(bookmarks, writer);
        }
        writer.write("</body></html>");
    }

    private static void writeAsset(Context context, OutputStreamWriter writer, String name) throws IOException {
        try (InputStream input = context.getAssets().open(name);
             BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line);
                writer.write('\n');
            }
        }
    }

    private static void writeAnnotations(Context context, Cursor cursor, OutputStreamWriter writer) throws IOException {
        String noteLabel = Html.escapeHtml(context.getString(R.string.export_doc_note));
        int textIndex = cursor.getColumnIndexOrThrow(AnnotationsTable.COLUMN_TEXT);
        int noteIndex = cursor.getColumnIndexOrThrow(AnnotationsTable.COLUMN_NOTE);
        int colorIndex = cursor.getColumnIndexOrThrow(AnnotationsTable.COLUMN_COLOR);
        int sectionIndex = cursor.getColumnIndexOrThrow(AnnotationsTable.COLUMN_SECTION_TITLE);
        while (cursor.moveToNext()) {
            String color = String.format("#%06X", 0xFFFFFF & cursor.getInt(colorIndex));
            writer.write("<div class='annotation'>\n");
            writer.write(String.format("<div class='color' style='background-color: %s'></div>\n", color));
            writer.write(String.format("<div class='title'>%s</div>\n", escape(cursor.getString(sectionIndex))));
            writer.write(String.format("<div class='highlight'>%s</div>\n", escape(cursor.getString(textIndex))));
            writer.write("</div>\n");
            String note = cursor.getString(noteIndex);
            if (!TextUtils.isEmpty(note)) {
                writer.write(String.format("<div class='notelabel'>%s</div>\n", noteLabel));
                writer.write(String.format("<div class='note'>%s</div>\n", escape(note)));
            }
        }
    }

    private static void writeBookmarks(Cursor cursor, OutputStreamWriter writer) throws IOException {
        int sectionIndex = cursor.getColumnIndexOrThrow(BookmarksTable.COLUMN_SECTION_TITLE);
        int pageIndex = cursor.getColumnIndexOrThrow(BookmarksTable.COLUMN_PAGE);
        writer.write("<ul>");
        while (cursor.moveToNext()) {
            writer.write("<li>");
            writer.write("<div class='bookmark-title'>" + escape(cursor.getString(sectionIndex)) + "</div>\n");
            writer.write("<div class='bookmark-content'>Page " + cursor.getInt(pageIndex) + "</div>\n");
            writer.write("</li>");
        }
        writer.write("</ul>");
    }

    private static String escape(String value) {
        return value == null ? "" : Html.escapeHtml(value);
    }
}
