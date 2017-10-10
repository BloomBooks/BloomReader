package org.sil.bloom.reader.models;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import org.sil.bloom.reader.BloomFileReader;
import org.sil.bloom.reader.BloomReaderApplication;
import org.sil.bloom.reader.IOUtilities;

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

    public void init(Context context) throws ExtStorageUnavailableException {
        mLocalBooksDirectory = getLocalBooksDirectory();
        SharedPreferences values = context.getSharedPreferences(BloomReaderApplication.SHARED_PREFERENCES_TAG, 0);
        if(values.getBoolean(BloomReaderApplication.FIRST_RUN, true)){
            SampleBookLoader.CopySampleBooksFromAssetsIntoBooksFolder(context, mLocalBooksDirectory);
            values.edit().putBoolean(BloomReaderApplication.FIRST_RUN, false);
        }
        LoadFromDirectory(mLocalBooksDirectory);
    }

    public static File getLocalBooksDirectory() throws ExtStorageUnavailableException {
        if(!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
            throw new ExtStorageUnavailableException();
        File bloomDir = Environment.getExternalStoragePublicDirectory("Bloom");
        bloomDir.mkdirs();
        return bloomDir;
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
        BloomFileReader fileReader = new BloomFileReader(context, bookUri);
        String name = fileReader.bookNameIfValid();
        if(name == null)
            return null;

        Log.d("BloomReader", "Moving book into Bloom directory");
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
}
