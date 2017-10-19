package org.sil.bloom.reader.models;

import java.io.File;
import java.util.Comparator;

public class BookOrShelf {
    public static final String BOOK_FILE_EXTENSION = ".bloomd";
    public static final String BOOKSHELF_FILE_EXTENSION = ".bloomshelf";

    public final String path;
    public final String name;
    // currently only applies to bookshelf. But that could change.
    public String backgroundColor;

    public BookOrShelf(String path) {
        //this.id = id;
        this.path = path;
        File f = new File(path);
        this.name = f.getName().replace(BOOK_FILE_EXTENSION,"");
    }

    public boolean isShelf() {
        return path.endsWith(BOOKSHELF_FILE_EXTENSION);
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