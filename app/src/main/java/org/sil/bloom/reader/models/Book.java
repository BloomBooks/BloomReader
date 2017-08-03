package org.sil.bloom.reader.models;

import java.io.File;
import java.util.Comparator;

public class Book {
    public static final String BOOK_FILE_EXTENSION = ".bloomd";

    public final String path;
    public final String name;

    public Book(String path) {
        //this.id = id;
        this.path = path;
        File f = new File(path);
        this.name = f.getName().replace(BOOK_FILE_EXTENSION,"");
    }

    @Override
    public String toString() {
        return this.name;
    }

    public static final Comparator<Book> AlphabeticalComparator = new AlphabeticalBookComparator();

    public static class AlphabeticalBookComparator implements Comparator<Book> {
        @Override
        public int compare(Book book1, Book book2) {
            int nameCompare = book1.name.compareToIgnoreCase(book2.name);
            if (nameCompare != 0)
                return nameCompare;
            return book1.path.compareToIgnoreCase(book2.path);
        }
    }
}