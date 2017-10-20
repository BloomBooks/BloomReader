package org.sil.bloom.reader;

import android.content.Context;
import android.net.Uri;

import org.sil.bloom.reader.models.BookOrShelf;

import java.io.File;
import java.io.IOException;

/**
 * Created by rick on 9/26/17.
 */

public class BloomFileReader {

    private Context context;
    private String bloomFilePath;
    private Uri bookUri;
    private File bookDirectory;

    private final String CURRENT_BOOK_FOLDER = "currentbook";
    private final String VALIDATE_BOOK_FILE_FOLDER = "validating";
    private final String HTM_EXTENSION = ".htm";

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
