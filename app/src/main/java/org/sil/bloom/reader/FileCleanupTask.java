package org.sil.bloom.reader;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import org.sil.bloom.reader.models.BookCollection;
import org.sil.bloom.reader.models.ExtStorageUnavailableException;

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

            // Returns null if the file is not found
            File fileToDelete = searchForFile(nonRemovableStorageDir, uriToCleanUp);

            if (fileToDelete != null)
                fileToDelete.delete();
        }
        catch (SecurityException e) {
            // SecurityException can be thrown by File.delete()
            Log.e("BloomReader", e.getLocalizedMessage());
        }
    }

    private boolean isOnNonRemovableStorage(File file) {
        if (Build.VERSION.SDK_INT >= 21)
            return !Environment.isExternalStorageRemovable(file);

        // True if default storage is non-removable and the file is in that directory
        return (!Environment.isExternalStorageRemovable() &&
                file.getPath().startsWith(Environment.getExternalStorageDirectory().getPath()));
    }

    private boolean isABookInOurLibrary(File file) {
        // The library does not include bloombundles
        if (file.getPath().endsWith(IOUtilities.BLOOM_BUNDLE_FILE_EXTENSION))
            return false;

        return file.getPath().startsWith(getBloomDirectory().getPath());
    }

    private boolean shouldSearchThisDirectory(File dir, Uri uriToCleanUp) {
        // We don't want to find the bloomd's in our library
        // Bundles are fair game everywhere
        if (uriToCleanUp.getPath().endsWith(IOUtilities.BLOOM_BUNDLE_FILE_EXTENSION))
            return true;

        return !dir.equals(getBloomDirectory());
    }

    private File getBloomDirectory() {
        if (bloomDirectory == null)
            bloomDirectory = BookCollection.getLocalBooksDirectory();
        return bloomDirectory;
    }

    private File searchForFile(File dir, Uri uriToCleanUp) {
        if (dir == null)
            return null;

        File[] list = dir.listFiles();
        if (list == null)
            return null;

        for (File f : list) {
            if (f.isFile() && matchesSearchFile(f, uriToCleanUp))
                return f;
            if (f.isDirectory() && shouldSearchThisDirectory(f, uriToCleanUp)) {
                File fileToDelete = searchForFile(f, uriToCleanUp);
                if (fileToDelete != null)
                    return fileToDelete;
            }
        }
        return null;
    }

    private boolean matchesSearchFile(File file, Uri searchFileUri) {
        return file.getName().equals(fileNameFromUri(searchFileUri));
    }

    private String fileNameFromUri(Uri uri) {
        // expected path is something like /document/primary:MyBook.bloomd
        String path = uri.getPath();
        int separatorIndex = Math.max(
                                path.lastIndexOf(File.separator),
                                path.lastIndexOf(':')
        );
        if (separatorIndex < 0)
            return path;
        return path.substring(separatorIndex + 1);
    }
}
