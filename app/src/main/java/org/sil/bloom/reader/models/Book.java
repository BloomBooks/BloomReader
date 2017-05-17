package org.sil.bloom.reader.models;

import java.io.File;

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
}