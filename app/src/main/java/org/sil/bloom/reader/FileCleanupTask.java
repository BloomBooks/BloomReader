package org.sil.bloom.reader;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import org.sil.bloom.reader.models.BookCollection;

import java.io.File;
import java.lang.ref.WeakReference;

/*
    Used to clean up a bloombundle or bloomd file after importing the contents into
    the Bloom directory.

    We only want to do this if the file is in non-removable storage.
    If the file is on an sd card, we leave it alone so that the sd card
    can be passed to another device to import the same file.
 */

public class FileCleanupTask extends AsyncTask<Uri, Void, Void> {

    private final WeakReference<Context> contextRef;
    private File bloomDirectory; // We don't want to remove bloomd's from here

    public FileCleanupTask(Context context) {
        // See https://stackoverflow.com/questions/44309241/warning-this-asynctask-class-should-be-static-or-leaks-might-occur/46166223#46166223
        this.contextRef = new WeakReference<>(context);
    }

    @Override
    public Void doInBackground(Uri... urisToCleanUp) {
        for(Uri uriToCleanUp : urisToCleanUp)
            cleanUpUri(uriToCleanUp);
        return null;
    }

    private void cleanUpUri(Uri uriToCleanUp) {
        try {
            // If the URI is file:// we have direct access to the file,
            // otherwise we have to search for it
            if (uriToCleanUp.getScheme().equals("file")) {
                File searchFile = new File(uriToCleanUp.getPath());
                if (isOnNonRemovableStorage(searchFile) && !isABookInOurLibrary(searchFile))
                    searchFile.delete();
                return;
            }

            Context context = contextRef.get();
            if (context == null)
                return;

            File nonRemovableStorageDir = IOUtilities.nonRemovablePublicStorageRoot(context);
            if (nonRemovableStorageDir == null)
                return;

            String fileNameFromUri = IOUtilities.getFileNameFromUri(context, uriToCleanUp);
            if (fileNameFromUri == null || fileNameFromUri.isEmpty())
                return;

            //TODO we should be able to delete the file directly using the uri
            // and the below doesn't work if you don't have legacy storage permission

            // Returns null if the file is not found
            File fileToDelete = legacyStorageSearchForFile(nonRemovableStorageDir, fileNameFromUri);

            if (fileToDelete != null)
                fileToDelete.delete();
        }
        catch (SecurityException e) {
            // SecurityException can be thrown by File.delete()
            Log.e("BloomReader", e.getLocalizedMessage());
        }
    }

    private boolean isOnNonRemovableStorage(File file) {
        return !Environment.isExternalStorageRemovable(file);
    }

    private boolean isABookInOurLibrary(File file) {
        // The library does not include bloombundles
        if (file.getPath().endsWith(IOUtilities.BLOOM_BUNDLE_FILE_EXTENSION))
            return false;

        return file.getPath().startsWith(getBloomDirectory().getPath());
    }

    private boolean shouldSearchThisDirectory(File dir, String fileName) {
        // We don't want to find the bloomd's in our library
        // Bundles are fair game everywhere
        if (fileName.endsWith(IOUtilities.BLOOM_BUNDLE_FILE_EXTENSION))
            return true;

        return !dir.equals(getBloomDirectory());
    }

    private File getBloomDirectory() {
        if (bloomDirectory == null)
            bloomDirectory = BookCollection.getLocalBooksDirectory();
        return bloomDirectory;
    }

    private File legacyStorageSearchForFile(File dir, String fileName) {
        if (dir == null)
            return null;

        File[] list = dir.listFiles();
        if (list == null)
            return null;

        for (File f : list) {
            if (f.isFile() && f.getName().equals(fileName))
                return f;
            if (f.isDirectory() && shouldSearchThisDirectory(f, fileName)) {
                File fileToDelete = legacyStorageSearchForFile(f, fileName);
                if (fileToDelete != null)
                    return fileToDelete;
            }
        }
        return null;
    }
}
