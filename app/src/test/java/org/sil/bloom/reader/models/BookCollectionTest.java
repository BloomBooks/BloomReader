package org.sil.bloom.reader.models;

import android.test.InstrumentationTestCase;

import org.junit.Test;

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
        assertThat(BookCollection.isBookInFilter(bookInNoShelf, ""), is(true));
    }

    @Test
    public void isBookInFilter_noShelf_someFilter_False() throws Exception {
        BookOrShelf bookInNoShelf = new BookOrShelf("nowhere/rubbish/a root book");
        // And, not being in a shelf, it is false for any particular shelf
        assertThat(BookCollection.isBookInFilter(bookInNoShelf, "myshelf"), is(false));
    }

    @Test
    public void isBookInFilter_inShelf_noFilter_False() throws Exception {
        BookOrShelf bookInLevel2 = new BookOrShelf("nowhere/rubbish/a Level 2 book");
        bookInLevel2.addBookshelf("Level 2");
        assertThat(BookCollection.isBookInFilter(bookInLevel2, null), is(false));
    }

    @Test
    public void isBookInFilter_inShelf_sameFilter_True() throws Exception {
        BookOrShelf bookInLevel2 = new BookOrShelf("nowhere/rubbish/a Level 2 book");
        bookInLevel2.addBookshelf("Level 2");
        assertThat(BookCollection.isBookInFilter(bookInLevel2, "Level 2"), is(true));
    }

    @Test
    public void isBookInFilter_in2Shelves_isInBoth() throws Exception {
        BookOrShelf bookInLevel2AndAnimals = new BookOrShelf("nowhere/rubbish/a Level 2 and Animal book");
        bookInLevel2AndAnimals.addBookshelf("Level 2");
        bookInLevel2AndAnimals.addBookshelf("Animals");
        assertThat(BookCollection.isBookInFilter(bookInLevel2AndAnimals, "Level 2"), is(true));
        assertThat(BookCollection.isBookInFilter(bookInLevel2AndAnimals, "Animals"), is(true));
    }

    @Test
    public void isBookInFilter_in2Shelves_isNotInRootOrOther() throws Exception {
        BookOrShelf bookInLevel2AndAnimals = new BookOrShelf("nowhere/rubbish/a Level 2 and Animal book");
        bookInLevel2AndAnimals.addBookshelf("Level 2");
        bookInLevel2AndAnimals.addBookshelf("Animals");
        assertThat(BookCollection.isBookInFilter(bookInLevel2AndAnimals, "Level 23"), is(false));
        assertThat(BookCollection.isBookInFilter(bookInLevel2AndAnimals, ""), is(false));
        assertThat(BookCollection.isBookInFilter(bookInLevel2AndAnimals, null), is(false));
    }

    @Test
    public void isBookInFilter_isShelf_noFilter_True() throws Exception {
        BookOrShelf shelf = new BookOrShelf("nowhere/rubbish/a shelf.bloomshelf");
        // A shelf is in the root collection.
        // (This didn't actually take any implementation because it behaves like any other
        // BookOrShelf that is not on any shelves.)
        assertThat(BookCollection.isBookInFilter(shelf, ""), is(true));
    }

    @Test
    public void isBookInFilter_isShelf_anyFilter_False() throws Exception {
        BookOrShelf shelf = new BookOrShelf("nowhere/rubbish/a shelf.bloomshelf");
        // A shelf is not in any shelf
        // (This also took no implementation, because a shelf never gets any setting saying
        // it's in a shelf, so it's not on any particular one.)
        assertThat(BookCollection.isBookInFilter(shelf, "Anywhere"), is(false));
    }

    @Test
    public void setBookFilters_shelf_addsNone() {
        BookOrShelf shelf = new BookOrShelf("where can we put this.bloomshelf");
        BookCollection.setShelvesOfBook(shelf);
        assertThat(shelf.isBookInAnyShelf(), is(false));
    }
}