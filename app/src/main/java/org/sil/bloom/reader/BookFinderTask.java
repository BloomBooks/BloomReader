package org.sil.bloom.reader;


import android.app.Activity;
import android.os.AsyncTask;

import java.io.File;

import static org.sil.bloom.reader.IOUtilities.BLOOM_BUNDLE_FILE_EXTENSION;
import static org.sil.bloom.reader.IOUtilities.BOOK_FILE_EXTENSION;
import static org.sil.bloom.reader.IOUtilities.BOOKSHELF_FILE_EXTENSION;
import static org.sil.bloom.reader.IOUtilities.ENCODED_FILE_EXTENSION;

public class BookFinderTask extends AsyncTask<Void, Void, Void> {

    private Activity activity;
    private BookSearchListener bookSearchListener;

    public BookFinderTask(Activity activity, BookSearchListener bookSearchListener)  {
        this.activity = activity;
        this.bookSearchListener = bookSearchListener;
    }

    @Override
    public Void doInBackground(Void... v) {
        scan(IOUtilities.removablePublicStorageRoot(activity));
        scan(IOUtilities.nonRemovablePublicStorageRoot(activity));

        return null;
    }

    @Override
    public void onPostExecute(Void v) {
        bookSearchListener.onSearchComplete();
    }

    private void scan(File root) {
        if (root != null) {
            File[] list = root.listFiles();

            if (list == null)
                return;

            for (File f : list) {
//                Log.d("BookSearch", f.getName() + " is dir: "
//                        + Boolean.toString(f.isDirectory()) + " is file: " + Boolean.toString(f.isFile()));

                if (f.isFile()) {
                    if (f.getName().endsWith(BLOOM_BUNDLE_FILE_EXTENSION) ||
                            f.getName().endsWith(BLOOM_BUNDLE_FILE_EXTENSION + ENCODED_FILE_EXTENSION))
                        foundNewBundle(f);
                    else if (f.getName().endsWith(BOOK_FILE_EXTENSION) ||
                             f.getName().endsWith(BOOK_FILE_EXTENSION + ENCODED_FILE_EXTENSION) ||
                             f.getName().endsWith(BOOKSHELF_FILE_EXTENSION))
                        foundNewBookOrShelf(f);
                }
                else if (f.isDirectory()) {
                    scan(f);
                }
            }
        }
    }

    private void foundNewBookOrShelf(final File bookOrShelfFile) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                bookSearchListener.onNewBookOrShelf(bookOrShelfFile);
            }
        });
    }

    private void foundNewBundle(final File bundle) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                bookSearchListener.onNewBloomBundle(bundle);
            }
        });
    }

    public interface BookSearchListener {
        void onNewBookOrShelf(File bloomdFile);
        void onNewBloomBundle(File bundleFile);
        void onSearchComplete();
    }
}
