package org.sil.bloom.reader.models;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONException;
import org.json.JSONObject;
import org.sil.bloom.reader.BloomFileReader;
import org.sil.bloom.reader.BloomReaderApplication;
import org.sil.bloom.reader.IOUtilities;
import org.sil.bloom.reader.SAFUtilities;

import java.io.File;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import static org.sil.bloom.reader.IOUtilities.BOOKSHELF_FILE_EXTENSION;
import static org.sil.bloom.reader.IOUtilities.BOOK_FILE_EXTENSION;

public class BookOrShelf {
    public static final String SHARED_PREFERENCES_TAG = "org.sil.bloom.reader.BookMetaJson";
    public static final String HAS_AUDIO = "hasAudio";
    public final String pathOrUri; // May actually be the toString() of a Uri (if it starts with content:)
    public final Uri uri;
    public final String name;
    public boolean highlighted = false;
    // currently only applies to bookshelf. But that could change.
    public String backgroundColor;
    public String shelfId;

    // currently only applies to books
    public String brandingProjectName;
    public String title;
    private JSONObject bookMeta; // Lazy loaded - Use getBookMeta() to access

    // This is set on certain shelves...so far only the one that stands for the external SD card
    // when we don't have permission to access it...that behave specially when clicked.
    public String specialBehavior = null;

    private Set<String> bookshelves = new HashSet<String>();

    public BookOrShelf(String pathOrUri, String name, Uri uri) {
        this.uri = uri;
        this.pathOrUri = pathOrUri;
        this.name = name == null ? BookOrShelf.getNameFromPath(pathOrUri) : name;
    }

    public BookOrShelf(String pathOrUri, String name) {
        this(pathOrUri, name, SAFUtilities.tryGetUri(pathOrUri));
    }

    public BookOrShelf(Uri uri) {
        this(uri.toString(), null, uri);
    }

    public BookOrShelf(String pathOrUri) {
        this(pathOrUri, null, SAFUtilities.tryGetUri(pathOrUri));
    }

    public static String getNameFromPath(String pathOrUri) {
        Uri uri = SAFUtilities.tryGetUri(pathOrUri);
        // The main reason for getPath() is to remove url encoding.
        final String path = uri == null ? pathOrUri : uri.getPath();
        return new File(path).getName().replace(BOOK_FILE_EXTENSION,"").replace(BOOKSHELF_FILE_EXTENSION,"");
    }

    public boolean isShelf() {
        return pathOrUri.endsWith(BOOKSHELF_FILE_EXTENSION);
    }

    public long lastModified() {
        if (uri != null) return IOUtilities.lastModified(BloomReaderApplication.getBloomApplicationContext(), uri);
        else return new File(pathOrUri).lastModified();
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
            BloomFileReader reader = new BloomFileReader(context, pathOrUri, uri);
            boolean hasAudio = reader.hasAudio();
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
        return pathOrUri + " - " + String.valueOf(lastModified());
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

    public static final Comparator<BookOrShelf> AlphanumComparator = new AlphanumComparator();

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
            return pathOrUri.startsWith(BookCollection.getLocalBooksDirectory().getAbsolutePath());
    }
}