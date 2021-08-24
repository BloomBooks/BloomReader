package org.sil.bloom.reader;

import android.app.Activity;
import android.net.Uri;
import android.os.AsyncTask;

import java.io.File;
import java.lang.ref.WeakReference;

import static org.sil.bloom.reader.IOUtilities.BLOOM_BUNDLE_FILE_EXTENSION;
import static org.sil.bloom.reader.IOUtilities.BOOK_FILE_EXTENSION;
import static org.sil.bloom.reader.IOUtilities.BOOKSHELF_FILE_EXTENSION;
import static org.sil.bloom.reader.IOUtilities.ENCODED_FILE_EXTENSION;

// This class implements the "Find books on this device" command for Android pre-11.
public class BookFinderTask extends AsyncTask<Void, Void, Void> {

    private final WeakReference<Activity> activityRef;
    private final BookSearchListener bookSearchListener;

    public BookFinderTask(Activity activity, BookSearchListener bookSearchListener) {
        // See https://stackoverflow.com/questions/44309241/warning-this-asynctask-class-should-be-static-or-leaks-might-occur/46166223#46166223
        this.activityRef = new WeakReference<>(activity);
        this.bookSearchListener = bookSearchListener;
    }

    @Override
    public Void doInBackground(Void... v) {
        Activity activity = activityRef.get();
        if (activity == null)
            return null;

        scan(IOUtilities.removablePublicStorageRoot(activity));
        scan(IOUtilities.nonRemovablePublicStorageRoot(activity));

        return null;
    }

    @Override
    public void onPostExecute(Void v) {
        if (bookSearchListener != null)
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
        Activity activity = activityRef.get();
        if (activity != null) {
            activity.runOnUiThread(() -> {
                if (bookSearchListener != null)
                    bookSearchListener.onFoundBookOrShelf(bookOrShelfFile, Uri.fromFile(bookOrShelfFile));
            });
        }
    }

    private void foundNewBundle(final File bundleFile) {
        Activity activity = activityRef.get();
        if (activity != null) {
            activity.runOnUiThread(() -> {
                if (bookSearchListener != null)
                    bookSearchListener.onFoundBundle(Uri.fromFile(bundleFile));
            });
        }
    }
}
