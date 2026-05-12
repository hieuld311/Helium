package com.hieuld.helium.util;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

/**
 * Utility class for safe URL operations.
 */
public class UrlUtils {

    /**
     * Decodes a URL string safely by escaping invalid '%' characters
     * before decoding to prevent potential crashes.
     *
     * @param url The encoded URL string.
     * @param enc The encoding scheme (e.g., "UTF-8").
     * @return The decoded string.
     * @throws UnsupportedEncodingException If the encoding is not supported.
     */
    public static String safeDecode(String url, String enc) throws UnsupportedEncodingException {
        return URLDecoder.decode(url.replaceAll("%(?![0-9a-fA-F]{2})", "%25"), enc);
    }
}
