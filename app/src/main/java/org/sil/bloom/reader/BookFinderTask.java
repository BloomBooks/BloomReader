package org.sil.bloom.reader;


import android.app.Activity;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import java.io.File;

import static org.sil.bloom.reader.IOUtilities.BLOOM_BUNDLE_FILE_EXTENSION;
import static org.sil.bloom.reader.IOUtilities.BLOOMD_FILE_EXTENSION;

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
                    if (f.getName().endsWith(BLOOM_BUNDLE_FILE_EXTENSION))
                        foundNewBundle(f);
                    else if (f.getName().endsWith(BLOOMD_FILE_EXTENSION))
                        foundNewBook(f);
                }
                else if (f.isDirectory()) {
                    scan(f);
                }
            }
        }
    }

    private void foundNewBook(final File bloomd) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                bookSearchListener.onNewBloomd(bloomd);
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
        void onNewBloomd(File bloomdFile);
        void onNewBloomBundle(File bundleFile);
        void onSearchComplete();
    }
}
