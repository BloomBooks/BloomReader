package org.sil.bloom.reader;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.sil.bloom.reader.models.BookCollection;

import java.io.File;
import java.io.IOException;

public class BloomFileReader {

    private Context context;
    private String bloomFilePath;
    private Uri bookUri;
    private File bookDirectory;
    private JSONObject metaProperties;

    private static final String CURRENT_BOOK_FOLDER = "currentbook";
    private static final String VALIDATE_BOOK_FILE_FOLDER = "validating";
    private static final String HTM_EXTENSION = ".htm";
    private static final String THUMBNAIL_NAME_1 = "thumbnail.png";
    private static final String THUMBNAIL_NAME_2 = "thumbnail.jpg";
    private static final String META_JSON_FILE = "meta.json";
    private static final String BOOK_AUDIO_MATCH = "audio-sentence";
    private static final String AUDIO_FOLDER = "/audio/";

    public BloomFileReader(Context context, String bloomFilePath){
        this.context = context;
        this.bloomFilePath = bloomFilePath;
    }

    public BloomFileReader(Context context, Uri uri){
        this.context = context;
        this.bookUri = uri;
    }

    public File getHtmlFile() throws IOException{
        if(bookDirectory == null)
            openFile(CURRENT_BOOK_FOLDER);
        return findHtmlFile();
    }

    @Nullable // If no font file matches the give name
    public File getFontFile(String fontFileName) {
        try {
            if (bookDirectory == null)
                openFile(CURRENT_BOOK_FOLDER);
            File fontFile = new File(bookDirectory + File.separator + fontFileName);
            return fontFile.exists() ? fontFile : null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

    }

    // returns null if anything goes wrong reading it
    public String getFileContent(String name) {
        if(bookDirectory == null) {
            try {
                openFile(CURRENT_BOOK_FOLDER);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
        File file = new File(bookDirectory + File.separator + name);
        if (!file.exists())
            return null;
        return IOUtilities.FileToString(file);
    }

    public Boolean hasAudio() {
        String html;
        File bookDirectory;
        boolean audioFilesExist = false;
        try {
            final File bookHtmlFile = this.getHtmlFile();
            html = IOUtilities.FileToString(bookHtmlFile);
            bookDirectory = bookHtmlFile.getParentFile();
            final String audioDirectoryPath = bookDirectory + AUDIO_FOLDER;
            File audioDir = new File(audioDirectoryPath);
            if (audioDir.exists()) {
                audioFilesExist = !IOUtilities.isDirectoryEmpty(audioDir);
            }
        } catch (IOException ex) {
            return false; // we're just trying to put audio icons on thumbnails
        } finally {
            closeFile();
        }
        return html != null && html.contains(BOOK_AUDIO_MATCH) && audioFilesExist;
    }

    public Uri getThumbnail(File thumbsDirectory) throws IOException{
        Uri thumbUri = null;
        if(bookDirectory == null)
            openFile(CURRENT_BOOK_FOLDER);
        String bookName = (new File(bloomFilePath)).getName().replace(IOUtilities.BOOK_FILE_EXTENSION, "");
        File thumb = new File(bookDirectory.getPath() + File.separator + THUMBNAIL_NAME_1);
        if (!thumb.exists())
            thumb = new File(bookDirectory.getPath() + File.separator + THUMBNAIL_NAME_2);
        if(thumb.exists()){
            String toPath = thumbsDirectory.getPath() + File.separator + bookName;
            if(IOUtilities.copyFile(thumb.getPath(), toPath));
                thumbUri = Uri.fromFile(new File(toPath));
        }
        else{
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

    private JSONObject getMetaProperties(){
        if(metaProperties == null) {
            try {
                File metaFile = new File(bookDirectory + File.separator + META_JSON_FILE);
                if (!metaFile.exists())
                    throw new IOException(META_JSON_FILE + " not found");
                metaProperties = new JSONObject(IOUtilities.FileToString(metaFile));
            } catch (JSONException | IOException e) {
                Log.e("BloomFileReader", "Error parsing meta.json: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        }
        return metaProperties;
    }

    private void openFile(String path) throws IOException{
        if(bloomFilePath == null) {
            unzipBook(bookUri, path);
            return;
        }
        File bloomFile = new File(bloomFilePath);
        if(bloomFile.isDirectory()){
            bookDirectory = bloomFile;
            return;
        }
        unzipBook(bloomFilePath, path);
    }

    private File findHtmlFile() throws IOException{
        String name = bookDirectory.getName().replace(IOUtilities.BOOK_FILE_EXTENSION, "");
        File htmlFile = new File(bookDirectory + File.separator + HTM_EXTENSION);
        if(!htmlFile.exists()){
            htmlFile = IOUtilities.findFirstWithExtension(bookDirectory, HTM_EXTENSION);
            if(htmlFile == null)
                throw new IOException("No HTML file found inside .bloomd zip file.");
        }
        return htmlFile;
    }

    private void closeFile() {
        File toEmpty = bookDirectory;
        bookDirectory = null;
        IOUtilities.emptyDirectory(toEmpty);
    }

    private void unzipBook(String fromPath, String toPath) throws IOException {
        setupBookDirectory(toPath);
        IOUtilities.unzip(new File(fromPath), bookDirectory);
    }

    private void unzipBook(Uri uri, String toPath) throws IOException {
        setupBookDirectory(toPath);
        IOUtilities.unzip(context, uri, bookDirectory);
    }

    private void setupBookDirectory(String path){
        bookDirectory = context.getDir(path, Context.MODE_PRIVATE);
        IOUtilities.emptyDirectory(bookDirectory);

    }
}
