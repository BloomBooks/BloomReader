package org.sil.bloom.reader.models;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.sil.bloom.reader.BloomFileReader;
import org.sil.bloom.reader.BloomReaderApplication;
import org.sil.bloom.reader.BuildConfig;
import org.sil.bloom.reader.IOUtilities;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class BookCollection {
    public static final String BOOKSHELF_PREFIX = "bookshelf:";
    private List<BookOrShelf> _booksAndShelves = new ArrayList<BookOrShelf>();
    private File mLocalBooksDirectory;

    public int indexOf(BookOrShelf book) { return _booksAndShelves.indexOf(book); }

    public BookOrShelf get(int i) {
        return _booksAndShelves.get(i);
    }

    public int size() {
        return _booksAndShelves.size();
    }

    public BookOrShelf addBookIfNeeded(String path) {
        BookOrShelf existingBook = getBookByPath(path);
        if (existingBook != null)
            return existingBook;
        return addBook(path, true);
    }

    private BookOrShelf addBook(String path, boolean sortList) {
        BookOrShelf bookOrShelf = new BookOrShelf(path);
        if (path.endsWith(BookOrShelf.BOOKSHELF_FILE_EXTENSION)) {
            String json = IOUtilities.FileToString(new File(path));
            try {
                JSONObject data = new JSONObject(json);
                bookOrShelf.backgroundColor = data.getString("color");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        _booksAndShelves.add(bookOrShelf);
        if (sortList)
            Collections.sort(_booksAndShelves, BookOrShelf.AlphabeticalComparator);
        return bookOrShelf;
    }

    public BookOrShelf getBookByPath(String path) {
        for (BookOrShelf book: _booksAndShelves) {
            if (book.path.equals(path))
                return book;
        }
        return null;
    }

    public void init(Context context) throws ExtStorageUnavailableException {
        mLocalBooksDirectory = getLocalBooksDirectory();
        SharedPreferences values = context.getSharedPreferences(BloomReaderApplication.SHARED_PREFERENCES_TAG, 0);
        int buildCode = BuildConfig.VERSION_CODE;
        if(buildCode > values.getInt(BloomReaderApplication.LAST_RUN_BUILD_CODE, 0)){
            SampleBookLoader.CopySampleBooksFromAssetsIntoBooksFolder(context, mLocalBooksDirectory);
            SharedPreferences.Editor valuesEditor = values.edit();
            valuesEditor.putInt(BloomReaderApplication.LAST_RUN_BUILD_CODE, buildCode);
            valuesEditor.commit();
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
                final String name = files[i].getName();
                if (!name.endsWith(BookOrShelf.BOOK_FILE_EXTENSION)
                        && !name.endsWith(BookOrShelf.BOOKSHELF_FILE_EXTENSION))
                    continue; // not a book (nor a shelf)!
                addBook(files[i].getAbsolutePath(), false);
            }
            Collections.sort(_booksAndShelves, BookOrShelf.AlphabeticalComparator);
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

    public  List<BookOrShelf> getBooks() {
        return _booksAndShelves;
    }

    public void deleteFromDevice(BookOrShelf book) {
        File file = new File(book.path);
        if(file.exists()){
            file.delete();
        }
        _booksAndShelves.remove(book);
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

    // Tests whether a book passes the current 'filter'.
    // A null (or empty) filter, used by the main activity, contains books that are not on any
    // (existing) shelf. A non-empty filter, which is expected to be the ID of a shelf, contains books
    // which are on that shelf. (existingShelves passes in the list of shelves that actually
    // exist as files in the folder. a book tagged as being on a nonexistent shelf can still be
    // in the root collection.)
    // Shelves themselves, since there is no way for a shelf to be on a shelf, are included
    // when the filter is null or empty.
    public static boolean isBookInFilter(BookOrShelf book, String filter, Set<String> existingShelves) {
        if (filter == null || filter.length() == 0)
            return !book.isBookInAnyShelf(existingShelves);
        else {
            return book.isBookInShelf(filter);
        }
    }

    // Set the shelves if any that a book belongs to. (May be called for shelves, but does nothing.)
    // Extracts the meta.json entry from the bloomd file, extracts the tags from that,
    // finds any that start with "bookshelf:", and sets the balance of the tag as one of the
    // book's shelves.
    public static void setShelvesOfBook(BookOrShelf bookOrShelf) {
        if (bookOrShelf.isShelf())
            return;
        try {
            byte[] jsonBytes = IOUtilities.ExtractZipEntry(new File(bookOrShelf.path), "meta.json");
            if (jsonBytes == null)
                return; // paranoia
            String json = "";
            try {
                json = new String(jsonBytes, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                return;
            }
            try {
                JSONObject data = new JSONObject(json);
                JSONArray tags = data.getJSONArray("tags");
                for (int i = 0; i < tags.length(); i++) {
                    String tag = tags.getString(i);
                    if (!tag.startsWith(BOOKSHELF_PREFIX))
                        continue;
                    bookOrShelf.addBookshelf(tag.substring(BOOKSHELF_PREFIX.length()).trim());
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            // Not sure about just catching everything like this. But the worst that happens if
            // a bloomd does not contain valid meta.json from which we can extract tags is that
            // the book shows up at the root instead of on a shelf.
            e.printStackTrace();
        }
    }
}
