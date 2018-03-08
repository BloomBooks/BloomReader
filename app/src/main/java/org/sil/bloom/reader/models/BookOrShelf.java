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
    public boolean highlighted = false;
    // currently only applies to bookshelf. But that could change.
    public String backgroundColor;
    public String shelfId;

    // currently only applies to books
    public String brandingProjectName;

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
}