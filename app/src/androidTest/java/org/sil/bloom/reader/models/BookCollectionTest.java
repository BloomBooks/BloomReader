package org.sil.bloom.reader.models;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

/**
 * Created by Thomson on 10/20/2017.
 * These tests run on a real device (or emulator) which makes them very slow.
 * If you don't need some real-device behavior, put your test in the test folder BookCollectionTest,
 * rather than here in androidTest.
 */
public class BookCollectionTest {
    // This test requires a real device because infuriatingly JSON object parsing is not supported
    // in the regular unit test environment, and setShelvesOfBook uses it.
    @Test
    public void setBookFilters_bookOnTwoShelves_addsThem() throws Exception {
        // suggested for a file in test assets using a subclass of InstrumentationTestCase. But that is deprecated.
        //InputStream testInput = getInstrumentation().getTargetContext().getResources().getAssets().open("testmeta.bloomd");
        // Looks for a file in a parallel directory.
        // Since this file is app\src\androidTest\java\org\sil\bloom\reader\models\BookCollectionTest.java,
        // the file it looks for is app\src\androidTest\resources\org\sil\bloom\reader\models\testmeta.bloomd
        // That is a version-controlled test data file (described further below).
        InputStream testInput = getClass().getResourceAsStream("testmeta.bloomd");
        // Botheration! We're not allowed to know the path to one of our assets.
        // Copy it to a temp file.
        File file = File.createTempFile("some book","bloomd");
        file.deleteOnExit();
        OutputStream output = new FileOutputStream(file);
        byte[] buffer = new byte[4 * 1024]; // or other buffer size
        int read;

        while ((read = testInput.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        output.flush();
        testInput.close();
        // so now file is a temp zip file containing meta.json containing
        // "tags":["Animals", "bloomshelf:Level 2", "bloomshelf:rise/PNG"]
        BookOrShelf book = new BookOrShelf(file.getPath());
        BookCollection.setShelvesOfBook(book);
        assertThat(book.isBookInShelf("Level 2"), is(true));
        assertThat(book.isBookInShelf("rise/PNG"), is(true));
        assertThat(book.isBookInShelf("Animals"), is(false));
    }
}