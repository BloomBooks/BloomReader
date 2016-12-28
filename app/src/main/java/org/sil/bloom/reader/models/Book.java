package org.sil.bloom.reader.models;

public class Book {
    public final String path;
    public final String name;
    public final String id;

    public Book(String id, String name, String path) {
        this.id = id;
        this.path = path;
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}