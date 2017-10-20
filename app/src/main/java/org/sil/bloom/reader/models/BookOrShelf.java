package org.sil.bloom.reader.models;

import android.support.annotation.NonNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

public class BookOrShelf {
    public static final String BOOK_FILE_EXTENSION = ".bloomd";
    public static final String BOOKSHELF_FILE_EXTENSION = ".bloomshelf";

    public final String path;
    public final String name;
    // currently only applies to bookshelf. But that could change.
    public String backgroundColor;

    private Set<String> bookshelves = new HashSet<String>();

    public BookOrShelf(String path) {
        //this.id = id;
        this.path = path;
        File f = new File(path);
        this.name = f.getName().replace(BOOK_FILE_EXTENSION,"");
    }

    public boolean isShelf() {
        return path.endsWith(BOOKSHELF_FILE_EXTENSION);
    }

    public void addBookshelf(String shelf) {
        bookshelves.add(shelf);
    }

    public boolean isBookInShelf(String shelf) {
        return bookshelves.contains(shelf);
    }

    // Review: do we need to restrict this to shelves that actually exist?
    public boolean isBookInAnyShelf() {
        return !bookshelves.isEmpty();
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
}