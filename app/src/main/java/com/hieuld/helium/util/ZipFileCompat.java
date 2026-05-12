package com.hieuld.helium.util;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A wrapper for ZipFile that automatically detects and handles
 * a common root directory prefix within the archive.
 */
public class ZipFileCompat implements Closeable {
    private String mRootPath;
    private final ZipFile mZipFile;

    /**
     * Initializes the ZipFile and determines if all entries share a common root folder.
     */
    public ZipFileCompat(String filePath) throws IOException {
        this.mZipFile = new ZipFile(filePath);
        this.mRootPath = "";

        Enumeration<? extends ZipEntry> entries = this.mZipFile.entries();
        if (entries.hasMoreElements()) {
            ZipEntry firstEntry = entries.nextElement();
            if (firstEntry.isDirectory()) {
                boolean allInRoot = true;
                while (entries.hasMoreElements()) {
                    if (!entries.nextElement().getName().startsWith(firstEntry.getName())) {
                        allInRoot = false;
                        break;
                    }
                }
                if (allInRoot) {
                    this.mRootPath = firstEntry.getName();
                }
            }
        }
    }

    /** Retrieves a ZipEntry by prepending the detected root path to the given name. */
    public ZipEntry getEntryFromRoot(String entryName) {
        return this.mZipFile.getEntry(this.mRootPath + entryName);
    }

    /** Returns an InputStream for reading the specified ZipEntry. */
    public InputStream getInputStream(ZipEntry entry) throws IOException {
        return this.mZipFile.getInputStream(entry);
    }

    @Override
    public void close() throws IOException {
        this.mZipFile.close();
    }

    /** Returns the absolute path of the ZIP file. */
    public String getName() {
        return this.mZipFile.getName();
    }
}
