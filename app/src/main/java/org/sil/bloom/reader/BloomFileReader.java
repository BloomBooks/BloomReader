package org.sil.bloom.reader;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.sil.bloom.reader.models.BookCollection;
import org.sil.bloom.reader.models.BookOrShelf;

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

    public Uri getThumbnail(File thumbsDirectory) throws IOException{
        Uri thumbUri = null;
        if(bookDirectory == null)
            openFile(CURRENT_BOOK_FOLDER);
        String bookName = (new File(bloomFilePath)).getName().replace(BookOrShelf.BOOK_FILE_EXTENSION, "");
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

    public String bookNameIfValid() {
        boolean valid = openAndValidateFile();
        if (!valid) {
            closeFile();
            return null;
        }
        try {
            String name = bloomFileName();
            closeFile();
            return name;
        }
        catch (IOException e) {
            closeFile();
            return null;
        }
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
        String name = bookDirectory.getName().replace(BookOrShelf.BOOK_FILE_EXTENSION, "");
        File htmlFile = new File(bookDirectory + File.separator + HTM_EXTENSION);
        if(!htmlFile.exists()){
            htmlFile = IOUtilities.findFirstWithExtension(bookDirectory, HTM_EXTENSION);
            if(htmlFile == null)
                throw new IOException("No HTML file found inside .bloomd zip file.");
        }
        return htmlFile;
    }

    private String bloomFileName() throws IOException{
        String path = bloomFilePath;
        if(path == null)
            path = bookUri.getPath();
        if(path.endsWith(BookOrShelf.BOOK_FILE_EXTENSION)){
            // The colon is necessary because the path for the SD card root is "1234-5678:myBook.bloomd"
            // where 1234-5678 is the SD card number
            int start = Math.max(path.lastIndexOf(File.separator), path.lastIndexOf(':')) + 1;
            return path.substring(start);
        }
        return findHtmlFile().getName().replace(HTM_EXTENSION, BookOrShelf.BOOK_FILE_EXTENSION);
    }

    private void closeFile() {
        File toEmpty = bookDirectory;
        bookDirectory = null;
        IOUtilities.emptyDirectory(toEmpty);
    }

    //Verifies the file can be unzipped and that there is an htm file inside.
    private boolean openAndValidateFile(){
        try{
            openFile(VALIDATE_BOOK_FILE_FOLDER);
            return (findHtmlFile() != null);
        }
        catch (IOException e){
            return false;
        }

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
