package org.sil.bloom.reader.models;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class BookTest {

    @Test
    public void alphabeticalComparator_sortsAlphabetically() {

        List<Book> books = Arrays.asList(
            new Book("/dummypath/c"),
            new Book("/dummypath/a"),
            new Book("/dummypath/B")
        );
        Collections.sort(books, Book.AlphabeticalComparator);
        assertThat(books.get(0).name, is("a"));
        assertThat(books.get(1).name, is("B"));
        assertThat(books.get(2).name, is("c"));
    }
}