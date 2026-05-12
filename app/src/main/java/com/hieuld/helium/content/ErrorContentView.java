package com.hieuld.helium.content;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import com.hieuld.helium.R;
import com.hieuld.helium.book.EPubBook;

/**
 * Fallback content view shown when a spine item is missing or its format is unsupported.
 *
 * The layout resource is passed in at construction time so the same class can be used
 * for both "missing" and "unsupported format" error states.
 */
public class ErrorContentView extends ContentView {

    private int mLastTouchX;
    private int mLastTouchY;
    private final int mLayoutResId;
    private TextView mUrlView;

    public ErrorContentView(Context context, EPubBook book, ContentClient contentClient,
                            int layoutResId) {
        super(context, book, contentClient);
        mLayoutResId = layoutResId;
    }

    @Override
    public void init() {
        View view = LayoutInflater.from(mContext).inflate(mLayoutResId, this, false);
        view.setSoundEffectsEnabled(false);
        view.setOnTouchListener((v, event) -> onContentTouch(event));
        view.setOnClickListener(v -> onContentClick());

        mUrlView = view.findViewById(R.id.url);
        addView(view);
    }

    private boolean onContentTouch(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            mLastTouchX = (int) event.getX();
            mLastTouchY = (int) event.getY();
        }
        return false; // let the click listener fire
    }

    private void onContentClick() {
        mContentClient.onPagePress(mLastTouchX, mLastTouchY);
    }

    @Override
    public void loadUrl(String url) {
        if (mUrlView != null) mUrlView.setText(url);
        mContentClient.onLoadDone();
    }
}
