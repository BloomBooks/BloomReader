package org.sil.bloom.reader.models;

import org.junit.Test;

import java.util.HashSet;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

/**
 * Created by Thomson on 10/19/2017.
 */
public class BookCollectionTest {
    @Test
    public void isBookInFilter_noShelf_noFilter_True() throws Exception {
        BookOrShelf bookInNoShelf = new BookOrShelf("nowhere/rubbish/a root book");
        // A book that isn't in any shelf is in the root collection
        assertThat(BookCollection.isBookInFilter(bookInNoShelf, "", new HashSet<String>()), is(true));
    }

    @Test
    public void isBookInFilter_noShelf_someFilter_False() throws Exception {
        BookOrShelf bookInNoShelf = new BookOrShelf("nowhere/rubbish/a root book");
        // And, not being in a shelf, it is false for any particular shelf
        assertThat(BookCollection.isBookInFilter(bookInNoShelf, "myshelf", new HashSet<String>()), is(false));
    }

    @Test
    public void isBookInFilter_inExistingShelf_noFilter_False() throws Exception {
        BookOrShelf bookInLevel2 = new BookOrShelf("nowhere/rubbish/a Level 2 book");
        bookInLevel2.addBookshelf("Level 2");
        final HashSet<String> existingShelves = new HashSet<>();
        existingShelves.add("Level 2");
        assertThat(BookCollection.isBookInFilter(bookInLevel2, null, existingShelves), is(false));
    }

    @Test
    public void isBookInFilter_inNonExistingShelf_noFilter_True() throws Exception {
        BookOrShelf bookInLevel2 = new BookOrShelf("nowhere/rubbish/a Level 2 book");
        bookInLevel2.addBookshelf("Level 2");
        // the book is in the shelf Level 2. So usually, we'd expect it to show up in that shelf,
        // and NOT in the root (null filter) list. But if there is no Level 2 shelf,
        // it DOES show up at the root. We check this both with an empty set of shelves and one
        // that contains another shelf.
        final HashSet<String> existingShelves = new HashSet<>();
        assertThat(BookCollection.isBookInFilter(bookInLevel2, null, existingShelves), is(true));
        existingShelves.add("Level 1");
        assertThat(BookCollection.isBookInFilter(bookInLevel2, "", existingShelves), is(true));
    }

    @Test
    public void isBookInFilter_inShelf_sameFilter_True() throws Exception {
        BookOrShelf bookInLevel2 = new BookOrShelf("nowhere/rubbish/a Level 2 book");
        bookInLevel2.addBookshelf("Level 2");
        final HashSet<String> existingShelves = new HashSet<>();
        existingShelves.add("Level 2");
        assertThat(BookCollection.isBookInFilter(bookInLevel2, "Level 2", existingShelves), is(true));
        // Note; plausibly we could have a test for the case that Level 2 is the filter
        // and in the book but not in existingShelves. However, we don't expect that situation
        // to occur and don't care much what the result is.
    }

    @Test
    public void isBookInFilter_in2Shelves_isInBoth() throws Exception {
        BookOrShelf bookInLevel2AndAnimals = new BookOrShelf("nowhere/rubbish/a Level 2 and Animal book");
        bookInLevel2AndAnimals.addBookshelf("Level 2");
        bookInLevel2AndAnimals.addBookshelf("Animals");
        final HashSet<String> existingShelves = new HashSet<>();
        existingShelves.add("Level 2");
        existingShelves.add("Animals");
        assertThat(BookCollection.isBookInFilter(bookInLevel2AndAnimals, "Level 2", existingShelves), is(true));
        assertThat(BookCollection.isBookInFilter(bookInLevel2AndAnimals, "Animals", existingShelves), is(true));
    }

    @Test
    public void isBookInFilter_in2Shelves_isNotInRootOrOther() throws Exception {
        BookOrShelf bookInLevel2AndAnimals = new BookOrShelf("nowhere/rubbish/a Level 2 and Animal book");
        bookInLevel2AndAnimals.addBookshelf("Level 2");
        bookInLevel2AndAnimals.addBookshelf("Animals");
        final HashSet<String> existingShelves = new HashSet<>();
        existingShelves.add("Level 2");
        existingShelves.add("Animals");
        assertThat(BookCollection.isBookInFilter(bookInLevel2AndAnimals, "Level 23", existingShelves), is(false));
        assertThat(BookCollection.isBookInFilter(bookInLevel2AndAnimals, "", existingShelves), is(false));
        assertThat(BookCollection.isBookInFilter(bookInLevel2AndAnimals, null, existingShelves), is(false));
    }

    @Test
    public void isBookInFilter_isShelf_noFilter_True() throws Exception {
        BookOrShelf shelf = new BookOrShelf("nowhere/rubbish/a shelf.bloomshelf");
        // A shelf is in the root collection.
        // (This didn't actually take any implementation because it behaves like any other
        // BookOrShelf that is not on any shelves.)
        assertThat(BookCollection.isBookInFilter(shelf, "", new HashSet<String>()), is(true));
    }

    @Test
    public void isBookInFilter_isShelf_anyFilter_False() throws Exception {
        BookOrShelf shelf = new BookOrShelf("nowhere/rubbish/a shelf.bloomshelf");
        // A shelf is not in any shelf
        // (This also took no implementation, because a shelf never gets any setting saying
        // it's in a shelf, so it's not on any particular one.)
        final HashSet<String> existingShelves = new HashSet<>();
        existingShelves.add("Anywhere");
        assertThat(BookCollection.isBookInFilter(shelf, "Anywhere", existingShelves), is(false));
    }

    @Test
    public void setBookFilters_shelf_addsNone() {
        BookOrShelf shelf = new BookOrShelf("where can we put this.bloomshelf");
        BookCollection.setShelvesAndTitleOfBook(shelf);
        assertThat(shelf.isBookInAnyShelf(new HashSet<String>()), is(false));
    }
}