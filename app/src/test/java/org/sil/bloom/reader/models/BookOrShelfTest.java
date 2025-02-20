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
    public void alphanumComparator_sortsAlphabetically() {

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
    public void alphanumComparator_edgeCases_sortsAlphabeticallyWithNullsLast() {

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

    // I've spent almost a day (maybe more?) trying to get this unit test to work.
    // At this point, I'm giving up and saying it isn't worth it.
    // Complication 1: gated code in AlphanumComparator for the SDK version.
    //  When running unit tests, Build.Version.SDK_INT is 0.
    //  I tried
    //  1. setting the SDK_INT via reflection
    //  2. mocking a wrapper around Build.VERSION.SDK_INT
    //  3. an "if" condition in production code which checks if we are running unit tests
    // Complication 2:
    //  Even when it was possible to get the correct production code to run,
    //  the unit test fails with "java.lang.RuntimeException: Method getInstance in android.icu.text.Collator not mocked."
    //  Turns out, you can't use the real ICU collator in unit tests. We could mock the collator,
    //  but that's basically working around the whole point of this test.
//    @Test
//    public void alphanumComparator_sortsUnicodeIntelligently() {
//
//        List<BookOrShelf> books = Arrays.asList(
//                new BookOrShelf("/dummypath/Huɛɲɔ̃"),
//                new BookOrShelf("/dummypath/Hããsiekɔluwɔ"),
//                new BookOrShelf("/dummypath/Nuɔ̃kɔ"),
//                new BookOrShelf("/dummypath/Sɛ̃cɔ"),
//                new BookOrShelf("/dummypath/Vasirɔ biɛ̃ŋɔ̃"),
//                new BookOrShelf("/dummypath/Ɲĩbũmɔ̃")
//        );
//
//        Collections.sort(books, BookOrShelf.AlphanumComparator);
//
//        List<String> expectedNames =
//                Arrays.asList("Hããsiekɔluwɔ", "Huɛɲɔ̃", "Nuɔ̃kɔ", "Ɲĩbũmɔ̃", "Sɛ̃cɔ", "Vasirɔ biɛ̃ŋɔ̃");
//
//        List<String> actualNames = books.stream().map(book -> book.name).collect(Collectors.toList());
//        assertThat(actualNames, is(expectedNames));
//    }
}