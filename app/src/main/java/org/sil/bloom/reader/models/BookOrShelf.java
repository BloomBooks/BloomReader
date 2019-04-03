package org.sil.bloom.reader.models;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;
import org.sil.bloom.reader.BloomFileReader;

import java.io.File;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import static org.sil.bloom.reader.IOUtilities.BOOKSHELF_FILE_EXTENSION;
import static org.sil.bloom.reader.IOUtilities.BOOK_FILE_EXTENSION;

public class BookOrShelf {
    public static final String SHARED_PREFERENCES_TAG = "org.sil.bloom.reader.BookMetaJson";
    public static final String HAS_AUDIO = "hasAudio";
    public final String path;
    public final String name;
    public boolean highlighted = false;
    // currently only applies to bookshelf. But that could change.
    public String backgroundColor;
    public String shelfId;

    // currently only applies to books
    public String brandingProjectName;
    private JSONObject bookMeta; // Lazy loaded - Use getBookMeta() to access

    private Set<String> bookshelves = new HashSet<String>();

    public BookOrShelf(String path, String name) {
        //this.id = id;
        this.path = path;
        this.name = name;
    }

    public BookOrShelf(String path) {
        this(path, BookOrShelf.getNameFromPath(path));
    }

    public static String getNameFromPath(String path) {
        return new File(path).getName().replace(BOOK_FILE_EXTENSION,"").replace(BOOKSHELF_FILE_EXTENSION,"");
    }

    public boolean isShelf() {
        return path.endsWith(BOOKSHELF_FILE_EXTENSION);
    }

    private JSONObject getBookMeta(Context context) {
        if (bookMeta != null) return bookMeta;
        if (isShelf()) return new JSONObject(); // Only applies to books

        try {
            SharedPreferences values = context.getSharedPreferences(SHARED_PREFERENCES_TAG, 0);
            String bookMetaJson = values.getString(metaCacheKey(), null);
            if (bookMetaJson != null) {
                bookMeta = new JSONObject(bookMetaJson);
                return bookMeta;
            }

            // BookMeta not found in cache - need to get it from file
            bookMeta = new JSONObject();
            boolean hasAudio = new BloomFileReader(context, path).hasAudio();
            bookMeta.put(HAS_AUDIO, hasAudio);
            SharedPreferences.Editor valuesEditor = values.edit();
            valuesEditor.putString(metaCacheKey(), bookMeta.toString());
            valuesEditor.apply();
            return bookMeta;
        }
        catch (JSONException e) {
            e.printStackTrace();
            return new JSONObject();
        }
    }

    private String metaCacheKey() {
        // A combination of the filepath and modified timestamp on the file
        File bookFile = new File(path);
        return path + " - " + String.valueOf(bookFile.lastModified());
    }

    public boolean hasAudio(Context context) {
        return getBookMeta(context).optBoolean(HAS_AUDIO);
    }

    public void addBookshelf(String shelf) {
        bookshelves.add(shelf);
    }

    public boolean isBookInShelf(String shelf) {
        return bookshelves.contains(shelf);
    }

    // Return true if the book is tagged as belonging to at least one of the shelves
    // in the set passed in.
    public boolean isBookInAnyShelf(Set<String> existingShelves) {
        Set<String> intersection = new HashSet<String>(bookshelves);
        intersection.retainAll(existingShelves);
        return !intersection.isEmpty();
    }

    @Override
    public String toString() {
        return this.name;
    }

    public static final Comparator<BookOrShelf> AlphabeticalComparator = new AlphabeticalBookComparator();

    public static class AlphabeticalBookComparator implements Comparator<BookOrShelf> {
        @Override
        public int compare(BookOrShelf bookOrShelf1, BookOrShelf bookOrShelf2) {
            int nameCompare = bookOrShelf1.name.compareToIgnoreCase(bookOrShelf2.name);
            if (nameCompare != 0)
                return nameCompare;
            return bookOrShelf1.path.compareToIgnoreCase(bookOrShelf2.path);
        }
    }

    public boolean inShareableDirectory() {
        // We can only share files from whitelisted directories
        // And Android doesn't let us whitelist arbitrary directories on the sd card
        // Not applicable to shelves
        return isInOnDeviceBloomFolder();
    }

    public boolean isDeleteable() {
        // We can only delete files in the standard Bloom directory
        // That is, not from a folder on the SD card
        return isInOnDeviceBloomFolder();
    }

    private boolean isInOnDeviceBloomFolder() {
        try {
            return path.startsWith(BookCollection.getLocalBooksDirectory().getAbsolutePath());
        }
        catch (ExtStorageUnavailableException e) {
            e.printStackTrace();
            return false;
        }
    }
}