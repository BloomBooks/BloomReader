package org.sil.bloom.reader;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import static org.sil.bloom.reader.models.BookCollection.getLocalBooksDirectory;

public class ImportBundleTask extends AsyncTask<Uri, String, Void> {
    private final WeakReference<MainActivity> mainActivityRef;
    private final Toast toast;
    private final List<String> newBookPaths;
    private final List<Uri> bundlesToCleanUp;
    private final ImportBundleErrorHandler importBundleErrorHandler;

    @SuppressLint("ShowToast")
    ImportBundleTask(MainActivity mainActivity) {
        // See https://stackoverflow.com/questions/44309241/warning-this-asynctask-class-should-be-static-or-leaks-might-occur/46166223#46166223
        this.mainActivityRef = new WeakReference<>(mainActivity);
        // Using a single toast object allows us to update the message immediately
        this.toast = Toast.makeText(mainActivity, "", Toast.LENGTH_SHORT);
        this.newBookPaths = new ArrayList<>();
        this.bundlesToCleanUp = new ArrayList<>();
        this.importBundleErrorHandler = new ImportBundleErrorHandler(mainActivity, toast); // setup ErrorHandler object
    }

    protected Void doInBackground(Uri... bundleUris) {
        try {
            for(Uri bloomBundleUri : bundleUris) {
                extractBloomBundle(bloomBundleUri);
                bundlesToCleanUp.add(bloomBundleUri);
            }
        }
        catch (IOException e) {
            // The exception is already handled by the importBundleErrorHandler,
            // but we need the catch phrase here to satisfy the compiler.
        }
        return null;
    }

    protected void onProgressUpdate(String... filenames) {
        // get a reference to the activity if it is still there
        MainActivity mainActivity = mainActivityRef.get();
        if (mainActivity == null || mainActivity.isFinishing())
            return;

        String toastMessage = mainActivity.getString(R.string.adding_book, filenames[0]);
        toast.setText(toastMessage);
        toast.show();
    }

    protected void onPostExecute(Void v) {
        // get a reference to the activity if it is still there
        MainActivity mainActivity = mainActivityRef.get();
        if (mainActivity == null || mainActivity.isFinishing())
            return;

        if (newBookPaths.size() > 0)
            mainActivity.reloadBookList();

        if (importBundleErrorHandler.hasErrors()) {
            importBundleErrorHandler.toastErrors();
        }

        new FileCleanupTask(mainActivity).execute(bundlesToCleanUp.toArray(new Uri[0]));
    }

    private void extractBloomBundle(Uri bloomBundleUri) throws IOException {
        // get a reference to the activity if it is still there
        MainActivity mainActivity = mainActivityRef.get();
        if (mainActivity == null || mainActivity.isFinishing())
            return;

        TarArchiveInputStream tarInput = null;
        try {
            InputStream fs = mainActivity.getContentResolver().openInputStream(bloomBundleUri);
            tarInput = new TarArchiveInputStream(fs);

            final String booksDirectoryPath = getLocalBooksDirectory().getAbsolutePath();
            ArchiveEntry entry;
            while ((entry = tarInput.getNextEntry()) != null) {
                publishProgress(entry.getName());
                newBookPaths.add(IOUtilities.extractTarEntry(tarInput, booksDirectoryPath));
            }
            tarInput.close();
            if (newBookPaths.isEmpty()) {
                importBundleErrorHandler.addErrorUri(bloomBundleUri, null);
            }
        } catch (IOException e) {
            // It could be that a .bloombundle.enc file really is still uuencoded I suppose.
            // Or the file could have been corrupted.
            if (tarInput != null)
                tarInput.close();
            if (bloomBundleUri != null)
                importBundleErrorHandler.addErrorUri(bloomBundleUri, e);
        }
    }

    private class ImportBundleErrorHandler {
        WeakReference<MainActivity> mMainActivityRef;
        Toast mToast;
        List<ImportErrorObject> mImportErrors;

        ImportBundleErrorHandler(MainActivity mainActivity, Toast toastSingleton) {
            mToast = toastSingleton;
            mMainActivityRef = new WeakReference<>(mainActivity);
            mImportErrors = new ArrayList<>();
        }

        private boolean hasErrors() {
            return mImportErrors.size() > 0;
        }

        private void toastErrors() {
            MainActivity activity = mainActivityRef.get();
            if (activity == null || activity.isFinishing())
                return;

            // Usually there will only be one... but be prepared.
            for(ImportErrorObject importError : mImportErrors) {
                reportSingleError(activity, importError);
            }
        }

        private void reportSingleError(MainActivity activity, ImportErrorObject importError) {
            String toastMessage;
            String bundleName = importError.getBundleName();
            if (importError.mException == null) {
                Log.e("BundleIO", "Imported bloom bundle, but got no books. Probably " +
                    importError.getBundleName() + " was a corrupt file.");
                toastMessage = activity.getString(R.string.bundle_import_error, bundleName);
            } else {
                IOException exception = importError.mException;
                Log.e("BundleIO", "IO exception reading bloom bundle " +
                    importError.getBundleName() + ": " + exception.getMessage());
                importError.mException.printStackTrace();

                if (importError.mException.getMessage() != null &&
                        importError.mException.getMessage().contains("ENOSPC")) {
                    toastMessage = activity.getString(R.string.bundle_import_out_of_space);
                } else {
                    toastMessage = activity.getString(R.string.bundle_import_error, bundleName);
                }

                // IOExceptions skip the file cleanup; so do it now.
                new FileCleanupTask(activity).execute(importError.mUri);
            }
            // Trying to use the same member variable Toast for multiple error toasts only showed
            // the last one. Also, we want a longer duration for these error messages.
            Toast toast = Toast.makeText(activity, toastMessage, Toast.LENGTH_LONG);
            toast.show();
        }

        private void addErrorUri(Uri bloomBundleUri, IOException e) {
            mImportErrors.add(new ImportErrorObject(bloomBundleUri, e));
        }
    }

    private static class ImportErrorObject {
        private final Uri mUri;
        private final IOException mException;

        ImportErrorObject(Uri uri, IOException exc) {
            mUri = uri;
            mException = exc;
        }

        String getBundleName() {
            String path = mUri.getEncodedPath();
            return (path != null && path.contains("/")) ? Uri.decode(path.substring(path.lastIndexOf("/") + 1)) : "";
        }
    }
}