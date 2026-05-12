package com.hieuld.helium.library;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.hieuld.helium.R;
import com.hieuld.helium.exceptions.EncryptedBookException;
import com.hieuld.helium.exceptions.MalformedBookException;
import com.hieuld.helium.util.HtmlCompat;
import com.hieuld.helium.util.Utils;

public class LibraryUpdateReportDialog extends DialogFragment {
    private LibraryUpdate.Result mResult;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            this.mResult = (LibraryUpdate.Result) getArguments().getSerializable("result");
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View view = LayoutInflater.from(getActivity()).inflate(R.layout.dialog_library_update_report, null, false);
        ListView listView = view.findViewById(R.id.files);
        if (this.mResult != null) {
            listView.setAdapter(new Adapter());
        }

        int count = this.mResult != null ? this.mResult.failedCount : 0;
        return new AlertDialog.Builder(requireActivity())
                .setTitle(getResources().getQuantityString(R.plurals.library_update_failed_details_title, count, count))
                .setView(view)
                .setPositiveButton(R.string.library_update_failed_details_close, null)
                .create();
    }

    public String getReasonString(LibraryUpdate.FailedBook failedBook) {
        String filename = failedBook.filename;
        // Trả về ký tự '/' thay vì số magic number 47
        int lastSlashIndex = filename.lastIndexOf('/');
        if (lastSlashIndex > -1) {
            filename = filename.substring(lastSlashIndex + 1);
        }

        String boldFilename = "<b>" + filename + "</b>";
        if (failedBook.exception instanceof MalformedBookException) {
            return getString(R.string.library_update_failed_details_malformed, boldFilename);
        }
        if (failedBook.exception instanceof EncryptedBookException) {
            return getString(R.string.library_update_failed_details_encrypted, boldFilename);
        }
        return getString(R.string.library_update_failed_details_other, boldFilename);
    }

    private String trimToPath(String path) {
        return path.substring(0, path.lastIndexOf('/') + 1);
    }

    public static LibraryUpdateReportDialog newInstance(LibraryUpdate.Result result) {
        LibraryUpdateReportDialog dialog = new LibraryUpdateReportDialog();
        Bundle args = new Bundle();
        args.putSerializable("result", result);
        dialog.setArguments(args);
        return dialog;
    }

    private class Adapter extends BaseAdapter {
        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getCount() {
            return mResult != null ? mResult.failedBooks.size() : 0;
        }

        @Override
        public Object getItem(int position) {
            return mResult != null ? mResult.failedBooks.get(position) : null;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getActivity()).inflate(R.layout.dialog_library_update_report_item, parent, false);
            }

            TextView textView = convertView.findViewById(R.id.text);
            TextView locationView = convertView.findViewById(R.id.location);

            LibraryUpdate.FailedBook failedBook = mResult.failedBooks.get(position);
            textView.setText(HtmlCompat.fromHtml(getReasonString(failedBook)));
            locationView.setText(Utils.getPathFromUrl(failedBook.filename));

            return convertView;
        }
    }
}