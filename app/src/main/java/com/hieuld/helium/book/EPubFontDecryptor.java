package com.hieuld.helium.book;

import android.util.Log;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * XOR-based font obfuscation decryptor for EPUB fonts.
 *
 * Supports two obfuscation schemes defined in the EPUB specification:
 * <ul>
 *   <li><b>Adobe</b>: {@code http://ns.adobe.com/pdf/enc#RC} — XOR the first 1024 bytes
 *       using the first 16 bytes of the unique-identifier.</li>
 *   <li><b>IDPF</b>: {@code http://www.idpf.org/2008/embedding} — XOR the first 1040 bytes
 *       using a SHA-1-derived key.</li>
 * </ul>
 *
 * Call {@link #setKey} and {@link #setStop} before calling {@link #decrypt}.
 */
public class EPubFontDecryptor {

    private byte[] mKey;
    private int    mStop; // number of bytes at the start of the stream to XOR

    public void setKey(byte[] key) {
        mKey = key;
    }

    public void setStop(int stop) {
        mStop = stop;
    }

    /** @return {@code true} if both key and stop have been configured. */
    public boolean isValid() {
        return mKey != null && mStop != 0;
    }

    /**
     * Wraps {@code source} in a decrypting stream.
     * The first {@link #mStop} bytes are XOR'd with the key; the rest pass through unchanged.
     *
     * @throws IllegalStateException if {@link #setKey} or {@link #setStop} has not been called.
     */
    public InputStream decrypt(InputStream source) {
        if (mKey == null || mStop == 0) {
            throw new IllegalStateException("Key and stop position must be set before decryption.");
        }
        return new DecryptingInputStream(source);
    }

    /**
     * XOR one byte at stream position {@code position} with the cycling key.
     *
     * @param rawByte  the raw (obfuscated) byte value (0–255)
     * @param position byte offset within the stream (0-based)
     * @return the deobfuscated byte value
     */
    public int transform(int rawByte, long position) {
        return rawByte ^ (mKey[(int) (position % mKey.length)] & 0xFF);
    }

    // ── DecryptingInputStream ─────────────────────────────────────────────────

    private class DecryptingInputStream extends FilterInputStream {

        private static final String TAG = "DecryptingInputStream";
        private long mPosition;

        DecryptingInputStream(InputStream source) {
            super(source);
        }

        @Override
        public boolean markSupported() {
            return false;
        }

        @Override
        public synchronized void mark(int readLimit) {
            Log.e(TAG, "mark() is not supported on encrypted streams.");
        }

        @Override
        public synchronized void reset() {
            Log.e(TAG, "reset() is not supported on encrypted streams.");
        }

        @Override
        public synchronized int read() throws IOException {
            int b = super.read();
            if (b == -1) return -1;
            if (mPosition < EPubFontDecryptor.this.mStop) {
                b = EPubFontDecryptor.this.transform(b, mPosition);
            }
            mPosition++;
            return b;
        }

        @Override
        public synchronized int read(byte[] buffer, int offset, int length) throws IOException {
            int bytesRead = super.read(buffer, offset, length);
            if (bytesRead == -1) return -1;

            if (mPosition < EPubFontDecryptor.this.mStop) {
                for (int i = 0; i < bytesRead; i++) {
                    long bytePos = mPosition + i;
                    if (bytePos < EPubFontDecryptor.this.mStop) {
                        int idx = offset + i;
                        buffer[idx] = (byte) EPubFontDecryptor.this.transform(buffer[idx] & 0xFF, bytePos);
                    }
                }
            }
            mPosition += bytesRead;
            return bytesRead;
        }

        @Override
        public synchronized long skip(long byteCount) throws IOException {
            long skipped = super.skip(byteCount);
            mPosition += skipped;
            return skipped;
        }
    }
}
