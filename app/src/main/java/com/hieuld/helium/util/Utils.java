package com.hieuld.helium.util;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.View;

import java.io.InputStream;
import java.security.MessageDigest;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class Utils {

    /**
     * Generates an MD5 hash string from an InputStream.
     */


    public static String md5Hex(InputStream inputStream) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] buffer = new byte[8192]; // Buffer 8KB
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            md.update(buffer, 0, read);
        }
        byte[] digest = md.digest();

        // Convert byte array to Hex string
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Converts density-independent pixels (dp) to device pixels (px).
     */
    public static int dpToPx(Context context, int dp) {
        return (int) ((dp * context.getResources().getDisplayMetrics().density) + 0.5f);
    }

    /**
     * Extracts the file extension from a URL, ignoring any hash fragments.
     */
    public static String getExtensionFromUrl(String url) {
        String urlWithoutHash = stripHashFromUrl(url);
        int lastDotIndex = urlWithoutHash.lastIndexOf(".");
        if (lastDotIndex == -1) {
            return null;
        }
        return urlWithoutHash.substring(lastDotIndex + 1);
    }

    /**
     * Removes the hash (#) fragment from a URL string.
     */
    public static String stripHashFromUrl(String url) {
        int hashIndex = url.indexOf("#");
        return hashIndex > -1 ? url.substring(0, hashIndex) : url;
    }

    /**
     * Returns the parent directory path from a given URL.
     */
    public static String getPathFromUrl(String url) {
        int lastSlashIndex = url.lastIndexOf("/");
        return lastSlashIndex == -1 ? "" : url.substring(0, lastSlashIndex + 1);
    }

    /**
     * Checks if a string matches any of the provided strings, ignoring case.
     */
    public static boolean equalsAnyIgnoreCase(String str, String... searchStrings) {
        for (String searchStr : searchStrings) {
            if (searchStr.equalsIgnoreCase(str)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Simplifies a file path by resolving ".." and "." segments.
     */
    public static String normalizePath(String path) {
        String[] parts = path.split("/");
        List<String> list = new ArrayList<>();
        for (String part : parts) {
            if ("..".equals(part)) {
                if (!list.isEmpty()) {
                    list.remove(list.size() - 1);
                }
            } else if (!".".equals(part)) {
                list.add(part);
            }
        }
        return TextUtils.join("/", list);
    }

    /**
     * Smoothly animates a view's background color to the target color.
     */
    public static void animateBackgroundToColor(final View view, int targetColor) {
        Drawable background = view.getBackground();
        int startColor = (background instanceof ColorDrawable) ? ((ColorDrawable) background).getColor() : 0;

        ValueAnimator animator = ValueAnimator.ofObject(new ArgbEvaluator(), startColor, targetColor);
        animator.setDuration(200L);
        animator.addUpdateListener(valueAnimator ->
                view.setBackgroundColor((Integer) valueAnimator.getAnimatedValue())
        );
        animator.start();
    }

    /**
     * Calculates the scaling factor for decoding a bitmap to avoid memory issues.
     */
    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    /**
     * Copies content from a source file to a destination file.
     */
    public static boolean copyFile(File source, File dest) {
        try (FileInputStream fileInputStream = new FileInputStream(source);
             FileOutputStream fileOutputStream = new FileOutputStream(dest)) {

            byte[] buffer = new byte[1024];
            int length;
            while ((length = fileInputStream.read(buffer)) > 0) {
                fileOutputStream.write(buffer, 0, length);
            }
            return true;

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}