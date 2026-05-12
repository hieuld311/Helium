package com.hieuld.helium.content;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.hieuld.helium.book.EPubBook;
import com.hieuld.helium.util.Utils;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Renders a standalone image (BMP, JPEG, PNG) spine item.
 *
 * Decoding is performed on a background thread; the result is delivered to
 * the main thread via a {@link Handler}.
 */
public class BitmapContentView extends ContentView {

    private ImageView mImageView;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    public BitmapContentView(Context context, EPubBook book, ContentClient contentClient) {
        super(context, book, contentClient);
    }

    @Override
    public void init() {
        mImageView = new ImageView(mContext);
        mImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);

        int padding = Utils.dpToPx(mContext, 10);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        lp.setMargins(padding, padding, padding, padding);
        addView(mImageView, lp);
    }

    @Override
    public void loadUrl(String url) {
        mExecutor.execute(() -> {
            Bitmap bitmap = null;
            try (InputStream is = mBook.getInputStreamForFile(url)) {
                if (is != null) bitmap = BitmapFactory.decodeStream(is);
            } catch (Exception e) {
                e.printStackTrace();
            }
            final Bitmap result = bitmap;
            mMainHandler.post(() -> {
                if (result != null) mImageView.setImageBitmap(result);
                mContentClient.onLoadDone();
            });
        });
    }

    @Override
    public void release() {
        super.release();
        mExecutor.shutdownNow();
    }
}
