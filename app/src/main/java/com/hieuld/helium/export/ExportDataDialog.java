package com.hieuld.helium.export;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.hieuld.helium.R;
import com.hieuld.helium.db.BooksTable;
import com.hieuld.helium.db.DatabaseProvider;

public class ExportDataDialog extends DialogFragment implements DialogInterface.OnClickListener, CompoundButton.OnCheckedChangeListener {
    private static final String EXTRA_BOOK_ID = "book_id";

    private long mBookId;
    private String mBookTitle;
    private CheckBox mExportBookmarksCheck;
    private RadioGroup mExportFormatGroup;
    private CheckBox mExportHighlightsCheck;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        mBookId = requireArguments().getLong(EXTRA_BOOK_ID);
        try (Cursor cursor = DatabaseProvider.getDatabase(requireContext()).query(
                BooksTable.TABLE_NAME,
                new String[]{BooksTable.COLUMN_TITLE},
                "_id=?",
                new String[]{Long.toString(mBookId)},
                null,
                null,
                null)) {
            if (cursor.moveToNext()) {
                mBookTitle = cursor.getString(0);
            } else {
                dismiss();
            }
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle bundle) {
        View view = LayoutInflater.from(getActivity()).inflate(R.layout.dialog_export, (ViewGroup) null, false);
        ((TextView) view.findViewById(R.id.book_title)).setText(mBookTitle);
        mExportHighlightsCheck = view.findViewById(R.id.data_highlights);
        mExportHighlightsCheck.setOnCheckedChangeListener(this);
        mExportBookmarksCheck = view.findViewById(R.id.data_bookmarks);
        mExportBookmarksCheck.setOnCheckedChangeListener(this);
        mExportHighlightsCheck.setChecked(true);
        mExportBookmarksCheck.setChecked(true);
        mExportFormatGroup = view.findViewById(R.id.format_group);
        mExportFormatGroup.check(R.id.format_html);
        return new AlertDialog.Builder(requireContext())
                .setTitle(R.string.export_dialog_title)
                .setView(view)
                .setPositiveButton(R.string.export_dialog_ok, this)
                .setNegativeButton(R.string.export_dialog_cancel, null)
                .create();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        ExportDataFormatter formatter = mExportFormatGroup.getCheckedRadioButtonId() == R.id.format_txt
                ? new PlainTextExportDataFormatter()
                : new HtmlExportDataFormatter();
        new ExportDataTask(
                context,
                formatter,
                mBookId,
                mBookTitle,
                DatabaseProvider.getDatabase(context),
                mExportHighlightsCheck.isChecked(),
                mExportBookmarksCheck.isChecked())
                .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public void onCheckedChanged(CompoundButton button, boolean checked) {
        AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog != null) {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                    .setEnabled(mExportHighlightsCheck.isChecked() || mExportBookmarksCheck.isChecked());
        }
    }

    public static ExportDataDialog newInstance(long bookId) {
        ExportDataDialog dialog = new ExportDataDialog();
        Bundle bundle = new Bundle();
        bundle.putLong(EXTRA_BOOK_ID, bookId);
        dialog.setArguments(bundle);
        return dialog;
    }
}
