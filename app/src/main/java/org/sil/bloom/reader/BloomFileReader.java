package org.sil.bloom.reader;

import android.content.Context;
import android.net.Uri;
import androidx.annotation.Nullable;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.sil.bloom.reader.models.BookCollection;
import org.sil.bloom.reader.models.BookOrShelf;

import java.io.File;
import java.io.IOException;

public class BloomFileReader {

    private final Context context;
    private String bloomFilePath;
    private Uri bookUri;
    private File bookDirectory;
    private JSONObject metaProperties;
    private ZipFileOrUri fileOrUri;

    private static final String CURRENT_BOOK_FOLDER = "currentbook";
    private static final String THUMBNAIL_NAME_1 = "thumbnail.png";
    private static final String THUMBNAIL_NAME_2 = "thumbnail.jpg";
    private static final String META_JSON_FILE = "meta.json";
    private static final String BOOK_AUDIO_MATCH = "audio-sentence";
    private static final String AUDIO_FOLDER = "/audio/";

    public BloomFileReader(Context context, String bloomFilePath){
        this(context, bloomFilePath, null);
    }

    public BloomFileReader(Context context, Uri uri){
        this(context, null, uri);
    }

    public BloomFileReader(Context context, String bloomFilePath, Uri uri){
        this.context = context;
        this.bookUri = uri;
        this.bloomFilePath = bloomFilePath;
    }

    public BloomFileReader(Context context, BookOrShelf book) {
        this(context, book.pathOrUri, book.uri);
    }

    // If there is a file by the specified name in the book folder, return it.
    // If there is an entry by the specified name in the zip file, extract and return it.
    // Otherwise return null.
    public File tryGetFile(String name) {
        return fileOrUri.tryGetFile(name);
    }

    public File getHtmlFile() throws IOException{
        initialize();
        File index = fileOrUri.tryGetFile("index.htm");
        if (index != null) {
            return index;
        }
        // Handle various legacy places the file might be by renaming it.
        // As well as simplifying things and meeting an expectation about
        // what the root file of a directory will be, this avoids any complications
        // with passing special characters to bloom-player in a URL.
        File currentFile = findHtmlFile();
        return currentFile; // pathological, but should work in most cases.
    }

    @Nullable // If no font file matches the give name
    public File getFontFile(String fontFileName) {
        try {
            initialize();
            File fontFile = fileOrUri.tryGetFile(fontFileName);
            return fontFile;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

    }

    // returns null if anything goes wrong reading it
    public String getFileContent(String name) {
        try {
            initialize();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        // If this method was commonly used, it might be worth getting the content
        // without writing out an unzipped file. But according to AS, it's not used at all.
        File file = fileOrUri.tryGetFile(name);
        if (file == null)
            return null;
        return IOUtilities.FileToString(file);
    }

    public Boolean hasAudio() {
        String html;
        File bookDirectory;
        boolean audioFilesExist = false;
        try {
            // This method is commonly called on a new BloomFileReader in a background process that
            // may be manipulating a different book than the one we are currently opening.
            // We must not unzip into the current book folder as that would interfere with the
            // current book (a race condition).
            if (this.bookDirectory == null) {
                prepareFileOrUriForBook("tempAudioPath");
            }
            final File bookHtmlFile = this.getHtmlFile();
            html = IOUtilities.FileToString(bookHtmlFile);
            audioFilesExist = fileOrUri.findFirstMatching(name -> name.startsWith("audio/")) != null;
        } catch (IOException ex) {
            return false; // we're just trying to put audio icons on thumbnails
        } finally {
            closeFile();
        }
        return html != null && html.contains(BOOK_AUDIO_MATCH) && audioFilesExist;
    }

    public Uri getThumbnail(File thumbsDirectory) throws IOException {
        Uri thumbUri = null;
        // This function is called in a background thread for books that are not the current one
        // being opened. It must not race for the same directory.
        prepareFileOrUriForBook("tempBookPath");
        String path = bloomFilePath == null ? bookUri.getPath() :bloomFilePath; // uri version is not a valid file path, but works for this.
        String bookName = IOUtilities.stripBookFileExtension((new File(path)).getName());
        File thumb = fileOrUri.tryGetFile(THUMBNAIL_NAME_1);
        if (thumb == null)
            thumb = fileOrUri.tryGetFile(THUMBNAIL_NAME_2);
        if (thumb != null) {
            String toPath = thumbsDirectory.getPath() + File.separator + bookName;
            if (IOUtilities.copyFile(thumb.getPath(), toPath))
                thumbUri = Uri.fromFile(new File(toPath));
        } else {
            String noThumbPath = thumbsDirectory + File.separator + BookCollection.NO_THUMBS_DIR + File.separator + bookName;
            (new File(noThumbPath)).createNewFile();
        }
        closeFile();

        return thumbUri;
    }

    public boolean getBooleanMetaProperty(String property, boolean defaultIfNotFound){
        JSONObject properties = getMetaProperties();
        if(properties == null)
            return defaultIfNotFound;
        return properties.optBoolean(property, defaultIfNotFound);
    }

    public String getStringMetaProperty(String property, String defaultIfNotFound){
        JSONObject properties = getMetaProperties();
        if(properties == null)
            return defaultIfNotFound;
        return properties.optString(property, defaultIfNotFound);
    }

    public int getIntMetaProperty(String property, int defaultIfNotFound){
        JSONObject properties = getMetaProperties();
        if(properties == null)
            return defaultIfNotFound;
        return properties.optInt(property, defaultIfNotFound);
    }

    public String[] getStringArrayMetaProperty(String property, String[] defaultIfNotFound)
    {
        JSONObject properties = getMetaProperties();
        if(properties == null)
            return defaultIfNotFound;
        try {
            JSONArray jsonArray = properties.getJSONArray(property);
            String[] result = new String[jsonArray.length()];
            for (int i = 0; i < result.length; i++) {
                result[i] = jsonArray.getString(i);
            }
            return result;
        }
        catch(JSONException e) {
            return defaultIfNotFound;
        }
    }

    private JSONObject getMetaProperties(){
        if(metaProperties == null) {
            try {
                String rawJson = getFileContent(META_JSON_FILE);
                metaProperties = new JSONObject(rawJson);
            } catch (JSONException e) {
                Log.e("BloomFileReader", "Error parsing meta.json: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        }
        return metaProperties;
    }

    // Typically, this reader was constructed with a bloomFilePath pointing to a .bloompub file.
    // Unzip this file into the folder indicated by the path argument, and set bookDirectory
    // to that directory. (On subsequent calls, the zip should already have been expanded,
    // and bookDirectory set, so the method will do nothing. It's therefore cheap to call this
    // when in any doubt about initialization. However, be careful about different instances
    // of the class, possibly in different threads, trying to expand the same or different
    // books into the same folder.)
    // If a BloomFileReader is ever created with a URI instead of a file path (currently there
    // are no callers of this constructor), it will unzip that instead.
    // If the supplied path is a directory (I doubt this ever happens), it will assume
    // that directory IS the book content, and simply set bookDirectory to point to it.
    private void initialize() throws IOException{
        if (bookDirectory != null)
            return; // already initialized.
        if(bloomFilePath != null) {
            File bloomFile = new File(bloomFilePath);
            if (bloomFile.isDirectory()) {
                // not sure how this happens, but apparently it's possible
                // that bloomFilePath is pointing to an already-unzipped directory.
                bookDirectory = bloomFile;
                return;
            }
        }
        prepareFileOrUriForBook(CURRENT_BOOK_FOLDER);
    }

    private File findHtmlFile() throws IOException {
        // so, we're calling this because we could not find "index.htm".
        // Next, look for an htm file that matches the name of the .bloompub/.bloomd
        String nameFromZipFile = IOUtilities.stripBookFileExtension(bookDirectory.getName()) + ".htm";
        File htmlFile = fileOrUri.tryGetFile(nameFromZipFile);
        if (htmlFile != null) {
            return htmlFile;
        }
        // Maybe the .bloompub/.bloomd file was renamed. So now just take the first htm file that
        // is in there and hope it is the right one.
        htmlFile = fileOrUri.findFirstWithExtension(".htm", "index.htm");
        if (htmlFile == null) {
            throw new IOException("No HTML file found inside bloomPUB zip file.");
        }
        return htmlFile;
    }

    private void closeFile() {
        File toEmpty = bookDirectory;
        bookDirectory = null;
        IOUtilities.emptyDirectory(toEmpty);
        fileOrUri.close();
    }

    // The book will be progressively unzipped into toPath as fileOrUri is asked for them
    // (or all at once if we only have a URI).
    private void prepareFileOrUriForBook(String toPath) throws IOException {
        setupBookDirectory(toPath);
        if (bookUri == null) {
            fileOrUri = new ZipFileOrUri(new File(bloomFilePath), bookDirectory.getPath());
        } else {
            fileOrUri = new ZipFileOrUri(bookUri, context, bookDirectory.getPath());
        }
    }

    private void setupBookDirectory(String path){
        bookDirectory = context.getDir(path, Context.MODE_PRIVATE);
        IOUtilities.emptyDirectory(bookDirectory);

    }
}
