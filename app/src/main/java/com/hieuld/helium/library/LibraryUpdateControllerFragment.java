package com.hieuld.helium.library;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

public class LibraryUpdateControllerFragment extends Fragment implements LibraryUpdate.Listener {
    private static final String TAG = "LibraryUpdateControllerFragment";
    private LibraryUpdate.Result mDeferredResult;
    private boolean mForced;
    private boolean mFound;
    private LibraryUpdate.Listener mListener;
    private LibraryUpdate mUpdate;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Lưu ý: setRetainInstance(true) đã bị deprecated trên API mới,
        // Nhưng nếu ứng dụng của bạn chưa thiết kế lại theo ViewModel thì vẫn dùng được.
        setRetainInstance(true);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof LibraryUpdate.Listener) {
            this.mListener = (LibraryUpdate.Listener) context;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        this.mListener = null;
    }

    public void startUpdate(boolean force) {
        if (this.mUpdate != null) {
            return;
        }
        Context context = getContext();
        if (context == null) {
            Log.e(TAG, "No context attached.");
        } else {
            this.mUpdate = LibraryUpdate.run(context, this, force);
            this.mForced = force;
        }
    }

    public boolean isActiveOrHasResult() {
        return this.mUpdate != null || this.mDeferredResult != null;
    }

    public boolean isForced() {
        return this.mForced;
    }

    public boolean hasFound() {
        return this.mFound;
    }

    public LibraryUpdate.Result getResult() {
        return this.mDeferredResult;
    }

    public void clear() {
        this.mForced = false;
        this.mFound = false;
        this.mDeferredResult = null;
        this.mUpdate = null;
    }

    @Override
    public void onLibraryUpdateFoundBook() {
        if (this.mListener != null) {
            this.mListener.onLibraryUpdateFoundBook();
        }
        this.mFound = true;
    }

    @Override
    public void onLibraryUpdateAddedBook() {
        if (this.mListener != null) {
            this.mListener.onLibraryUpdateAddedBook();
        }
    }

    @Override
    public void onLibraryUpdateDone(LibraryUpdate.Result result) {
        if (this.mListener != null) {
            this.mListener.onLibraryUpdateDone(result);
            clear();
        } else {
            this.mDeferredResult = result;
        }
    }
}