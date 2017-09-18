package org.sil.bloom.reader.models;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.util.Log;

import org.sil.bloom.reader.IOUtilities;
import org.sil.bloom.reader.WiFi.NewBookListenerService;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BookCollection {

    private List<Book> _books = new ArrayList<Book>();
    private File mLocalBooksDirectory;

    public int indexOf(Book book) { return _books.indexOf(book); }

    public Book get(int i) {
        return _books.get(i);
    }

    public int size() {
        return _books.size();
    }

    public Book addBookIfNeeded(String path) {
        Book existingBook = getBookByPath(path);
        if (existingBook != null)
            return existingBook;
        return addBook(path, true);
    }

    private Book addBook(String path, boolean sortList) {
        Book book = new Book(path);
        _books.add(book);
        if (sortList)
            Collections.sort(_books, Book.AlphabeticalComparator);
        return book;
    }

    public Book getBookByPath(String path) {
        for (Book book:_books) {
            if (book.path.equals(path))
                return book;
        }
        return null;
    }

    public void init(Context context) {
        ContextWrapper cw = new ContextWrapper(context);
        //File directory = cw.getExternalFilesDir("books");
        //IOUtilities.deleteFileOrDirectory(directory);

        SampleBookLoader.CopySampleBooksFromAssetsIntoBooksFolder(context, mLocalBooksDirectory);

//        for (int i = 2; i <= 4; i++) {
//            createFilesForDummyBook(context, directory, i);
//        }

        // load from our private directory. We may get rid of this entirely
        //LoadFromDirectory(directory);

        // load from the directory the user can see
        mLocalBooksDirectory = getLocalBooksDirectory();
        LoadFromDirectory(mLocalBooksDirectory);
    }

    public static File getLocalBooksDirectory() {
        return Environment.getExternalStoragePublicDirectory("Bloom");
    }

    private void LoadFromDirectory(File directory) {
        File[] files = directory.listFiles();
        if(files != null) {
            for (int i = 0; i < files.length; i++) {
                if (!files[i].getName().endsWith(Book.BOOK_FILE_EXTENSION))
                    continue; // not a book!
                addBook(files[i].getAbsolutePath(), false);
            }
            Collections.sort(_books, Book.AlphabeticalComparator);
        }
    }

    private void createFilesForDummyBook(Context context, File directory, int position) {
        File bookDir = new File(directory, "The " + position + " dwarves");
        bookDir.mkdir();
        File file = new File(bookDir, "The " + position + " dwarves.htm");
        if (file.exists()) {
            file.delete();
        }

        try {
            FileWriter out = new FileWriter(file);
            out.write("<html><body>Once upon a time, " + position + " dwarves lived in a forest.</body></html>");
            out.close();
        } catch (IOException e) {
            IOUtilities.showError(context, e.getMessage());
        }
    }

    public  List<Book> getBooks() {
        return _books;
    }

    public void deleteFromDevice(Book book) {
        File file = new File(book.path);
        if(file.exists()){
            file.delete();
        }
        _books.remove(book);
    }

    // is this coming from somewhere other than where we store books?
    // then move or copy it in
    public String ensureBookIsInCollection(Context context, Uri bookUri) {
        if (bookUri.getPath().indexOf(mLocalBooksDirectory.getAbsolutePath()) >= 0)
            return bookUri.getPath();
        String name = nameOfBloomFile(context, bookUri);
        if(!name.endsWith(Book.BOOK_FILE_EXTENSION))
            return null; //I'm not 100% sure this won't weed out some legit book files
        Log.d("BloomReader", "Moving book into Bloom directory");
        //File source = new File(path);
        String destination = mLocalBooksDirectory.getAbsolutePath() + File.separator + name;
        boolean copied = IOUtilities.copyFile(context, bookUri, destination);
        if(copied){
            // We assume that they will be happy with us removing from where ever the file was,
            // so long as it is on the same device (e.g. not coming from an sd card they plan to pass
            // around the room).
            if(!IOUtilities.seemToBeDifferentVolumes(bookUri.getPath(),destination)) {
                (new File(bookUri.getPath())).delete();
            }
            // we wouldn't have it in our list that we display yet, so make an entry there
            addBook(destination, true);
            return destination;
        } else{
            return null;
        }
    }

    public String nameOfBloomFile(Context context, Uri bookUri){
        //Three different apps sent me the file three different ways. There may be other ways
        //we want to handle that I don't know about.
        Cursor cursor = context.getContentResolver().query(bookUri, null, null, null, null);
        if(cursor != null) {
            cursor.moveToFirst();
            int displayNameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            int filenameColumn = cursor.getColumnIndex("filename");
            if (displayNameColumn >= 0)
                return cursor.getString(displayNameColumn);
            if (filenameColumn >= 0)
                return cursor.getString(filenameColumn);
        }
        return new File(bookUri.getPath()).getName();
    }
}
