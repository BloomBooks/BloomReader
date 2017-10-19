package org.sil.bloom.reader.models;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class BookOrShelfTest {

    @Test
    public void alphabeticalComparator_sortsAlphabetically() {

        List<BookOrShelf> books = Arrays.asList(
            new BookOrShelf("/dummypath/c"),
            new BookOrShelf("/dummypath/a"),
            new BookOrShelf("/dummypath/B")
        );
        Collections.sort(books, BookOrShelf.AlphabeticalComparator);
        assertThat(books.get(0).name, is("a"));
        assertThat(books.get(1).name, is("B"));
        assertThat(books.get(2).name, is("c"));
    }
}