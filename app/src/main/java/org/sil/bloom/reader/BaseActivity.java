package org.sil.bloom.reader;

import android.os.FileObserver;
import android.support.v7.app.AppCompatActivity;

import org.sil.bloom.reader.models.Book;
import org.sil.bloom.reader.models.BookCollection;

// A base abstract class which every activity should extend
public abstract class BaseActivity extends AppCompatActivity {
    private static FileObserver sObserver;

    @Override
    protected void onResume() {
        super.onResume();

        startObserving();
    }

    @Override
    protected void onPause() {
        stopObserving();

        super.onPause();
    }

    abstract protected void onNewOrUpdatedBook(String fullPath);

    private void startObserving() {
        // This shouldn't happen, but just in case...
        if (sObserver != null) {
            stopObserving();
        }

        // Need to create every time so the correct activity calls onNewOrUpdatedBook
        sObserver = createFileObserver();
        sObserver.startWatching();
    }

    private void stopObserving() {
        sObserver.stopWatching();
        sObserver = null;
    }

    private FileObserver createFileObserver() {
        final String pathToWatch = BookCollection.getLocalBooksDirectory().getPath();
        return new FileObserver(pathToWatch) { // set up a file observer to watch this directory
            @Override
            public void onEvent(int event, String file) {
                //At least currently, an update/modify also causes a CREATE event
                //if (((event & (FileObserver.CREATE | FileObserver.MODIFY)) != 0) && (file.endsWith(Book.BOOK_FILE_EXTENSION))) {

                if (((event & FileObserver.CREATE) != 0) && file.endsWith(Book.BOOK_FILE_EXTENSION)) {
                    onNewOrUpdatedBook(pathToWatch + "/" + file);
                }
            }
        };
    }
}
