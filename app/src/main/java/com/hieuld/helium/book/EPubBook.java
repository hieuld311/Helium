package com.hieuld.helium.book;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.Log;

import com.hieuld.helium.exceptions.BookLoadException;
import com.hieuld.helium.exceptions.BookNotFoundException;
import com.hieuld.helium.exceptions.EncryptedBookException;
import com.hieuld.helium.exceptions.MalformedBookException;
import com.hieuld.helium.exceptions.PermissionException;
import com.hieuld.helium.util.UrlUtils;
import com.hieuld.helium.util.Utils;
import com.hieuld.helium.util.ZipFileCompat;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.AccessDeniedException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;

public class EPubBook extends Book {
    private static final String MIME_TYPE = "application/epub+zip";
    private static final String TAG = "EPubBook";

    private EPubFontDecryptor mAdobeFontDecryptor;
    private String mCreator;
    private String mGuideCoverHtml;
    private String mGuideCoverImage;
    private EPubFontDecryptor mIdpfFontDecryptor;
    private Map<String, Rendition> mItemRenditionMap;
    private String mLanguage;
    private String mLinkedCoverImage;
    private Map<String, ManifestEntry> mManifestEntries;
    private Map<String, ManifestEntry> mManifestEntryByFile;
    private int mPageDirection;
    private Map<Integer, String> mPaperPageMap;
    private List<String> mSpineItemRefs;
    private String mTitle;
    private List<TocEntry> mTocEntries;
    private ZipFileCompat mZip;
    private final Map<String, EPubFontDecryptor> mFontDecryptorMap = new HashMap<>();
    private final Rendition mRendition = new Rendition();

    @Override
    protected void load(String filePath) throws BookLoadException {
        Log.d(TAG, "Loading " + filePath);
        try {
            this.mZip = new ZipFileCompat(filePath);
            try {
                readEpub();
            } catch (BookLoadException e) {
                close();
                throw e;
            }
        } catch (IOException e) {
            if ((e instanceof FileNotFoundException) || (e instanceof AccessDeniedException)) {
                String message = e.getMessage();
                if (message != null && message.toLowerCase().contains("permission denied")) {
                    throw new PermissionException();
                }
                throw new BookNotFoundException(e);
            }
            throw new MalformedBookException(e);
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new MalformedBookException("Failed to open ZIP. Internal ZipFile error thrown.", e);
        } catch (IllegalArgumentException e) {
            String message = e.getMessage();
            if (message != null && message.contains("MALFORMED")) {
                throw new MalformedBookException("Failed to open ZIP. It likely contains non-UTF-8 file names.", e);
            }
            throw e;
        }
    }

    public String getMimeType(String file) {
        ManifestEntry entry = this.mManifestEntryByFile.get(file);
        if (entry != null) {
            return entry.mediaType;
        }
        return null;
    }

    @Override
    public void close() {
        try {
            if (this.mZip != null) {
                this.mZip.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.mZip = null;
    }

    protected void readEpub() throws BookLoadException {
        String container = readContainer();
        if (container == null) {
            throw new MalformedBookException("No rootfile found in container.xml.");
        }
        this.mTocEntries = new ArrayList<>();
        this.mManifestEntries = new HashMap<>();
        this.mManifestEntryByFile = new HashMap<>();
        this.mSpineItemRefs = new ArrayList<>();
        this.mItemRenditionMap = new HashMap<>();

        readEncryptionInfo();
        readOpfFile(container);
    }

    private boolean validateMimeType() {
        ZipEntry entry = this.mZip.getEntryFromRoot("mimetype");
        if (entry == null) {
            Log.e(TAG, "Missing mimetype!");
            return false;
        }
        try (InputStream is = this.mZip.getInputStream(entry);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line = reader.readLine();
            if (MIME_TYPE.equals(line)) {
                return true;
            }
            Log.e(TAG, "Mime type is incorrect: '" + line + "' expected: 'application/epub+zip'");
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void readEncryptionInfo() throws BookLoadException {
        ZipEntry entry = this.mZip.getEntryFromRoot("META-INF/encryption.xml");
        if (entry == null) {
            return;
        }
        try (InputStream is = this.mZip.getInputStream(entry)) {
            XmlPullParser parser = android.util.Xml.newPullParser();
            SimplifiedXmlParser simplifiedParser = new SimplifiedXmlParser(parser);
            parser.setInput(is, null);
            while (true) {
                String path = simplifiedParser.nextPath();
                if (path == null) {
                    return;
                }
                if ("encryption/EncryptedData".equals(path)) {
                    readEncryptedData(simplifiedParser, parser, simplifiedParser.getDepth());
                }
            }
        } catch (IOException | XmlPullParserException e) {
            throw new MalformedBookException("Exception while reading container file.", e);
        }
    }

    private void readEncryptedData(SimplifiedXmlParser simplifiedParser, XmlPullParser parser, int depth) throws XmlPullParserException, BookLoadException, IOException {
        Set<String> uriSet = new HashSet<>();
        EPubFontDecryptor decryptor = null;

        while (true) {
            String path = simplifiedParser.nextPath(depth);
            if (path == null) {
                if (decryptor == null) {
                    return;
                }
                for (String uri : uriSet) {
                    this.mFontDecryptorMap.put(uri, decryptor);
                }
                return;
            }

            if ("EncryptionMethod".equals(path)) {
                String algo = parser.getAttributeValue(null, "Algorithm");
                if (algo != null) {
                    if ("http://ns.adobe.com/pdf/enc#RC".equals(algo)) {
                        if (this.mAdobeFontDecryptor == null) {
                            this.mAdobeFontDecryptor = new EPubFontDecryptor();
                        }
                        decryptor = this.mAdobeFontDecryptor;
                    } else if ("http://www.idpf.org/2008/embedding".equals(algo)) {
                        if (this.mIdpfFontDecryptor == null) {
                            this.mIdpfFontDecryptor = new EPubFontDecryptor();
                        }
                        decryptor = this.mIdpfFontDecryptor;
                    } else {
                        throw new EncryptedBookException(algo + " is not supported.");
                    }
                }
            } else if ("CipherData/CipherReference".equals(path)) {
                uriSet.add(parser.getAttributeValue(null, "URI"));
            }
        }
    }

    private String readContainer() throws BookLoadException {
        ZipEntry entry = this.mZip.getEntryFromRoot("META-INF/container.xml");
        if (entry == null) {
            throw new MalformedBookException("Missing META-INF/container.xml!");
        }
        try (InputStream is = this.mZip.getInputStream(entry)) {
            XmlPullParser parser = android.util.Xml.newPullParser();
            SimplifiedXmlParser simplifiedParser = new SimplifiedXmlParser(parser);
            parser.setInput(is, null);
            while (true) {
                String path = simplifiedParser.nextPath();
                if (path == null) {
                    return null;
                }
                if ("container/rootfiles/rootfile".equals(path)) {
                    String mediaType = parser.getAttributeValue(null, "media-type");
                    String fullPath = parser.getAttributeValue(null, "full-path");
                    if (mediaType == null || "application/oebps-package+xml".equals(mediaType)) {
                        return fullPath;
                    }
                    Log.e(TAG, "Unknown media-type '" + mediaType + "'");
                }
            }
        } catch (IOException | XmlPullParserException e) {
            throw new MalformedBookException("Exception while reading container file.", e);
        }
    }

    private void readOpfFile(String str) throws BookLoadException {
        ZipEntry entry = this.mZip.getEntryFromRoot(str);
        if (entry == null) {
            throw new MalformedBookException("Missing OPF file: " + str);
        }
        String basePath = Utils.getPathFromUrl(str);
        String coverId = null;
        String navHref = null;
        String ncxHref = null;

        try (InputStream is = this.mZip.getInputStream(entry)) {
            XmlPullParser parser = android.util.Xml.newPullParser();
            SimplifiedXmlParser simplifiedParser = new SimplifiedXmlParser(parser);
            parser.setInput(is, null);

            while (true) {
                String path = simplifiedParser.nextPath();
                if (path == null) break;

                if ("package".equals(path)) {
                    String dir = parser.getAttributeValue(null, "dir");
                    if ("rtl".equalsIgnoreCase(dir)) {
                        this.mPageDirection = Book.DIRECTION_RTL;
                    }
                } else if ("package/metadata/title".equals(path)) {
                    if (TextUtils.isEmpty(this.mTitle)) {
                        this.mTitle = parser.nextText();
                    }
                } else if ("package/metadata/creator".equals(path)) {
                    if (TextUtils.isEmpty(this.mCreator)) {
                        this.mCreator = parser.nextText();
                    }
                } else if ("package/metadata/language".equals(path)) {
                    this.mLanguage = parser.nextText();
                } else if ("package/metadata/meta".equals(path)) {
                    String name = parser.getAttributeValue(null, "name");
                    String property = parser.getAttributeValue(null, "property");
                    String content = parser.getAttributeValue(null, "content");
                    String text = parser.isEmptyElementTag() ? null : parser.nextText();
                    if (TextUtils.isEmpty(text)) {
                        text = content;
                    }
                    if ("cover".equals(name)) {
                        coverId = content;
                    }

                    if ("rendition:layout".equals(property) && !TextUtils.isEmpty(text)) {
                        if ("pre-paginated".equals(text)) {
                            this.mRendition.extend(Rendition.withLayoutStyle(2));
                        } else if ("reflowable".equals(text)) {
                            this.mRendition.extend(Rendition.withLayoutStyle(1));
                        }
                    } else if ("rendition:flow".equals(property) && !TextUtils.isEmpty(text)) {
                        if ("scrolled-continuous".equals(text) || "scrolled-doc".equals(text)) {
                            this.mRendition.extend(Rendition.withFlowStyle(2));
                        } else if ("paginated".equals(text) || "auto".equals(text)) {
                            this.mRendition.extend(Rendition.withFlowStyle(1));
                        }
                    }
                } else if ("package/manifest/item".equals(path)) {
                    ManifestEntry manifestEntry = new ManifestEntry();
                    manifestEntry.id = parser.getAttributeValue(null, "id");
                    manifestEntry.mediaType = parser.getAttributeValue(null, "media-type");
                    String href = parser.getAttributeValue(null, "href");

                    if (href != null) {
                        String decoded = UrlUtils.safeDecode(href, "UTF-8");
                        if (basePath != null) {
                            decoded = basePath + decoded;
                        }
                        manifestEntry.href = Utils.normalizePath(decoded);
                    }

                    if (!TextUtils.isEmpty(manifestEntry.id) && !TextUtils.isEmpty(manifestEntry.href)) {
                        this.mManifestEntries.put(manifestEntry.id, manifestEntry);
                        this.mManifestEntryByFile.put(manifestEntry.href, manifestEntry);
                    }

                    String properties = parser.getAttributeValue(null, "properties");
                    if (properties != null) {
                        String lowerProps = properties.toLowerCase(Locale.US);
                        if (lowerProps.contains("nav")) {
                            navHref = manifestEntry.href;
                        }
                        if (lowerProps.contains("cover-image")) {
                            this.mLinkedCoverImage = manifestEntry.href;
                        }
                    }

                    if ("application/x-dtbncx+xml".equals(manifestEntry.mediaType)) {
                        ncxHref = manifestEntry.href;
                    }
                } else if ("package/spine".equals(path)) {
                    String toc = parser.getAttributeValue(null, "toc");
                    if (!TextUtils.isEmpty(toc)) {
                        ManifestEntry entryToc = this.mManifestEntries.get(toc);
                        if (entryToc != null) {
                            ncxHref = entryToc.href;
                        }
                    }
                    String direction = parser.getAttributeValue(null, "page-progression-direction");
                    if ("rtl".equalsIgnoreCase(direction)) {
                        this.mPageDirection = Book.DIRECTION_RTL;
                    } else if ("ltr".equalsIgnoreCase(direction)) {
                        this.mPageDirection = Book.DIRECTION_LTR;
                    }
                } else if ("package/spine/itemref".equals(path)) {
                    String idref = parser.getAttributeValue(null, "idref");
                    if (!TextUtils.isEmpty(idref)) {
                        this.mSpineItemRefs.add(idref);
                    }

                    String properties = parser.getAttributeValue(null, "properties");
                    if (!TextUtils.isEmpty(idref) && !TextUtils.isEmpty(properties)) {
                        Rendition rendition = new Rendition();
                        String lowerProps = properties.toLowerCase(Locale.US);

                        if (lowerProps.contains("rendition:layout-pre-paginated")) {
                            rendition.layoutStyle = 2;
                        } else if (lowerProps.contains("rendition:layout-reflowable")) {
                            rendition.layoutStyle = 1;
                        }

                        if (lowerProps.contains("rendition:flow-scrolled-continuous") || lowerProps.contains("rendition:flow-scrolled-doc")) {
                            rendition.flowStyle = 2;
                        } else if (lowerProps.contains("rendition:flow-paginated") || lowerProps.contains("rendition:flow-auto")) {
                            rendition.flowStyle = 1;
                        }

                        if (rendition.layoutStyle != 0 || rendition.flowStyle != 0) {
                            this.mItemRenditionMap.put(idref, rendition);
                        }
                    }
                } else if ("package/guide/reference".equals(path)) {
                    String type = parser.getAttributeValue(null, "type");
                    String href = parser.getAttributeValue(null, "href");

                    if (!TextUtils.isEmpty(type) && !TextUtils.isEmpty(href)) {
                        String decoded = UrlUtils.safeDecode(href, "UTF-8");
                        if (basePath != null) {
                            decoded = basePath + decoded;
                        }
                        String normalized = Utils.normalizePath(decoded);
                        if ("cover".equalsIgnoreCase(type)) {
                            if (normalized.endsWith(".xhtml") || normalized.endsWith(".html") || normalized.endsWith(".htm")) {
                                this.mGuideCoverHtml = normalized;
                            } else {
                                this.mGuideCoverImage = normalized;
                            }
                        }
                    }
                }
            }

            if (this.mLinkedCoverImage == null && !TextUtils.isEmpty(coverId)) {
                ManifestEntry coverEntry = this.mManifestEntries.get(coverId);
                if (coverEntry != null) {
                    this.mLinkedCoverImage = coverEntry.href;
                }
            }
            if (this.mSpineItemRefs.isEmpty()) {
                throw new MalformedBookException("Book has empty spine.");
            }

            if (!TextUtils.isEmpty(navHref)) {
                readNavDocument(navHref);
            } else if (!TextUtils.isEmpty(ncxHref)) {
                readNcxFile(ncxHref);
            }

            if (this.mTocEntries == null) {
                this.mTocEntries = new ArrayList<>();
            }

        } catch (IOException | XmlPullParserException e) {
            throw new MalformedBookException("Exception while reading OPF.", e);
        }
    }

    private boolean readNavDocument(String str) {
        ZipEntry entry = this.mZip.getEntryFromRoot(str);
        if (entry == null) {
            Log.e(TAG, "Missing referenced nav file '" + str + "'");
            return false;
        }
        String basePath = Utils.getPathFromUrl(str);
        try (InputStream is = this.mZip.getInputStream(entry)) {
            EPubNavigationDocument navDoc = new EPubNavigationDocument(is);
            navDoc.setBaseUrl(basePath);
            navDoc.parse();
            this.mTocEntries = navDoc.getTable("toc");
            this.mPaperPageMap = navDoc.getPageMap();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean readNcxFile(String str) {
        ZipEntry entry = this.mZip.getEntryFromRoot(str);
        if (entry == null) {
            Log.e(TAG, "Missing referenced ncx file '" + str + "'");
            return false;
        }
        String basePath = Utils.getPathFromUrl(str);
        try (InputStream is = this.mZip.getInputStream(entry)) {
            EPubNcxDocument ncxDoc = new EPubNcxDocument(is);
            ncxDoc.setBaseUrl(basePath);
            ncxDoc.parse();
            this.mTocEntries = ncxDoc.getTable("toc");
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public InputStream getInputStreamForFile(String str) {
        ZipEntry entry = this.mZip.getEntryFromRoot(str);
        if (entry == null) {
            Log.e(TAG, "Zip entry '" + str + "' doesn't exist!");
            return null;
        }
        try {
            InputStream is = this.mZip.getInputStream(entry);
            EPubFontDecryptor decryptor = this.mFontDecryptorMap.get(str);
            return (decryptor == null || !decryptor.isValid()) ? is : decryptor.decrypt(is);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean containsFile(String str) {
        return this.mZip.getEntryFromRoot(str) != null;
    }

    @Override
    public String getTitle() {
        return this.mTitle;
    }

    @Override
    public String getCreator() {
        return this.mCreator;
    }

    @Override
    public int getPageDirection() {
        return this.mPageDirection;
    }

    @Override
    public String getLanguage() {
        return this.mLanguage;
    }

    @Override
    public List<TocEntry> getTocEntries() {
        return this.mTocEntries;
    }

    @Override
    public TocEntry findTocEntry(String url) {
        for (TocEntry entry : this.mTocEntries) {
            if (url.equalsIgnoreCase(entry.url)) {
                return entry;
            }
        }
        return null;
    }

    @Override
    public Set<Integer> getPaperPages() {
        return this.mPaperPageMap != null ? this.mPaperPageMap.keySet() : new HashSet<>();
    }

    @Override
    public SearchProvider getSearchProvider() {
        return new EPubSearchProvider(this);
    }

    public String nextSpineItem(String currentHref) {
        String nextIdRef;
        if (currentHref != null) {
            ManifestEntry entry = this.mManifestEntryByFile.get(currentHref);
            if (entry == null) return null;
            int index = this.mSpineItemRefs.indexOf(entry.id);
            if (index == -1 || index == this.mSpineItemRefs.size() - 1) {
                return null;
            }
            nextIdRef = this.mSpineItemRefs.get(index + 1);
        } else {
            nextIdRef = this.mSpineItemRefs.get(0);
        }

        ManifestEntry nextEntry = this.mManifestEntries.get(nextIdRef);
        return nextEntry == null ? null : nextEntry.href;
    }

    public String previousSpineItem(String currentHref) {
        String prevIdRef;
        if (currentHref != null) {
            ManifestEntry entry = this.mManifestEntryByFile.get(currentHref);
            if (entry == null) return null;
            int index = this.mSpineItemRefs.indexOf(entry.id);
            if (index <= 0) {
                return null;
            }
            prevIdRef = this.mSpineItemRefs.get(index - 1);
        } else {
            prevIdRef = this.mSpineItemRefs.get(this.mSpineItemRefs.size() - 1);
        }

        ManifestEntry prevEntry = this.mManifestEntries.get(prevIdRef);
        return prevEntry == null ? null : prevEntry.href;
    }

    private Bitmap decodeBitmapForSize(String href, int reqWidth, int reqHeight) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;

        try (InputStream is = getInputStreamForFile(href)) {
            if (is == null) return null;
            BitmapFactory.decodeStream(is, null, options);
        } catch (IOException e) {
            e.printStackTrace();
        }

        options.inJustDecodeBounds = false;
        options.inSampleSize = Utils.calculateInSampleSize(options, reqWidth, reqHeight);

        try (InputStream is2 = getInputStreamForFile(href)) {
            if (is2 == null) return null;
            return BitmapFactory.decodeStream(is2, null, options);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private Bitmap renderCoverBitmap(Context context) {
        int targetSize = Utils.dpToPx(context, Book.THUMBNAIL_SIZE);

        if (this.mLinkedCoverImage != null) {
            Bitmap bitmap = decodeBitmapForSize(this.mLinkedCoverImage, targetSize, targetSize);
            if (bitmap != null) return scaleToThumbnail(context, bitmap);
        }

        if (this.mGuideCoverHtml != null) {
            String inferredImage = inferImageFromHtmlCover(this.mGuideCoverHtml);
            if (inferredImage != null) {
                Bitmap bitmap = decodeBitmapForSize(inferredImage, targetSize, targetSize);
                if (bitmap != null) return scaleToThumbnail(context, bitmap);
            }
        }

        if (this.mGuideCoverImage != null) {
            Bitmap bitmap = decodeBitmapForSize(this.mGuideCoverImage, targetSize, targetSize);
            if (bitmap != null) return scaleToThumbnail(context, bitmap);
        }

        if (this.mZip.getEntryFromRoot("iTunesArtwork") != null) {
            Bitmap bitmap = decodeBitmapForSize("iTunesArtwork", targetSize, targetSize);
            if (bitmap != null) return scaleToThumbnail(context, bitmap);
        }

        ManifestEntry firstEntry = this.mManifestEntries.get(this.mSpineItemRefs.get(0));
        if (firstEntry != null) {
            String href = firstEntry.href.toLowerCase();
            if (href.endsWith(".png") || href.endsWith(".jpeg") || href.endsWith(".jpg")) {
                Bitmap bitmap = decodeBitmapForSize(firstEntry.href, targetSize, targetSize);
                if (bitmap != null) return scaleToThumbnail(context, bitmap);
            }
        }
        return null;
    }

    private String inferImageFromHtmlCover(String href) {
        String basePath = Utils.getPathFromUrl(href);
        try (InputStream is = getInputStreamForFile(href)) {
            if (is == null) return null;

            Document document = Jsoup.parse(is, "UTF-8", "/");
            if (!document.body().text().trim().isEmpty()) {
                return null; // Không phải là trang bìa ảnh đơn thuần
            }

            Elements imgs = document.getElementsByTag("img");
            if (imgs.size() == 1) {
                String src = UrlUtils.safeDecode(imgs.get(0).attr("src"), "UTF-8");
                return Utils.normalizePath(basePath + src);
            }

            Elements svgs = document.getElementsByTag("svg");
            if (svgs.size() == 1) {
                Elements images = svgs.get(0).getElementsByTag("image");
                if (images.size() == 1) {
                    Element image = images.get(0);
                    String attr = image.attr("href");
                    if (TextUtils.isEmpty(attr)) {
                        attr = image.attr("xlink:href");
                    }
                    String decodedAttr = UrlUtils.safeDecode(attr, "UTF-8");
                    return Utils.normalizePath(basePath + decodedAttr);
                }
            }
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public Bitmap getCoverBitmap(Context context) {
        return renderCoverBitmap(context);
    }

    public Rendition getItemRendition(String href) {
        ManifestEntry entry = this.mManifestEntryByFile.get(href);
        if (entry == null || !this.mItemRenditionMap.containsKey(entry.id)) {
            return this.mRendition;
        }
        return this.mItemRenditionMap.get(entry.id);
    }

    public Map<Integer, String> getPaperPageMap() {
        return this.mPaperPageMap;
    }

    public static class ManifestEntry {
        public String href;
        public String id;
        public String mediaType;

        public ManifestEntry() {
        }
    }
}
