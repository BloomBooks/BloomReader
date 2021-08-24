package org.sil.bloom.reader.models;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class BookOrShelfTest {

    @Test
    public void alphabeticalComparator_sortsAlphabetically() {

        List<BookOrShelf> books = Arrays.asList(
                new BookOrShelf("/dummypath/c"),
                new BookOrShelf("/dummypath/a"),
                new BookOrShelf("/dummypath/B")
        );
        Collections.sort(books, BookOrShelf.AlphanumComparator);
        assertThat(books.get(0).name, is("a"));
        assertThat(books.get(1).name, is("B"));
        assertThat(books.get(2).name, is("c"));
    }

    @Test
    public void alphabeticalComparator_edgeCases_sortsAlphabeticallyWithNullsLast() {

        List<BookOrShelf> books = Arrays.asList(
                new BookOrShelf("z", null),
                new BookOrShelf("z", "a"),
                null,
                new BookOrShelf("y", "b"),
                new BookOrShelf("y", "a"),
                new BookOrShelf((String)null, "b"),
                new BookOrShelf((String)null, "a")
        );
        Collections.sort(books, BookOrShelf.AlphanumComparator);
        assertThat(books.get(0).name, is("a"));
        assertThat(books.get(0).pathOrUri, is("y"));
        assertThat(books.get(1).name, is("a"));
        assertThat(books.get(1).pathOrUri, is("z"));
        assertThat(books.get(2).name, is("a"));
        assertThat(books.get(2).pathOrUri, nullValue());
        assertThat(books.get(3).name, is("b"));
        assertThat(books.get(3).pathOrUri, is("y"));
        assertThat(books.get(4).name, is("b"));
        assertThat(books.get(4).pathOrUri, nullValue());
        assertThat(books.get(5).name, is("z"));
        assertThat(books.get(5).pathOrUri, is("z"));
        assertThat(books.get(6), nullValue());
    }

    @Test
    public void alphanumComparator_sortsNumbersNaturally() {

        List<BookOrShelf> books = Arrays.asList(
                new BookOrShelf("/dummypath/12bob"),
                new BookOrShelf("/dummypath/1bob"),
                new BookOrShelf("/dummypath/bob"),
                new BookOrShelf("/dummypath/3 bob"),
                new BookOrShelf("/dummypath/02bob")
        );
        Collections.sort(books, BookOrShelf.AlphanumComparator);
        assertThat(books.get(0).name, is("1bob"));
        assertThat(books.get(1).name, is("02bob"));
        assertThat(books.get(2).name, is("3 bob"));
        assertThat(books.get(3).name, is("12bob"));
        assertThat(books.get(4).name, is("bob"));
    }
}