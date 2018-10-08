package org.sil.bloom.reader;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import org.sil.bloom.reader.models.BookCollection;
import org.sil.bloom.reader.models.BookOrShelf;
import org.sil.bloom.reader.models.ExtStorageUnavailableException;

import java.io.File;

/*
    Used to clean up a bloombundle or bloomd file after importing the contents into
    the Bloom directory.

    We only want to do this if the file is in non-removable storage.
    If the file is on an sd card, we leave it alone so that the sd card
    can be passed to another device to import the same file.
 */

public class FileCleanupTask extends AsyncTask<Void, Void, Void> {
    private Context context;
    private Uri searchFileUri;
    private File bloomDirectory; // We don't want to remove bloomd's from here

    public FileCleanupTask(Context context, Uri searchFileUri) {
        this.context = context;
        this.searchFileUri = searchFileUri;
    }

    @Override
    public Void doInBackground(Void... v) {
        try {
            // If the URI is file:// we have direct access to the file,
            // otherwise we have to search for it
            if (searchFileUri.getScheme().equals("file")) {
                File searchFile = new File(searchFileUri.getPath());
                if (isOnNonRemovableStorage(searchFile) && !isABookInOurLibrary(searchFile))
                    searchFile.delete();
                return null;
            }

            File nonRemovableStorageDir = IOUtilities.nonRemovablePublicStorageRoot(context);
            if (nonRemovableStorageDir == null)
                return null;

            // Returns null if the file is not found
            File fileToDelete = searchForFile(nonRemovableStorageDir);

            if (fileToDelete != null)
                fileToDelete.delete();
        }
        catch (Exception e) {
            // Is there any way to communicate this back to us
            // so we can fix issues that cause our code to crash?
            Log.e("BloomReader", e.getLocalizedMessage());
        }

        return null;
    }

    private boolean isOnNonRemovableStorage(File file) {
        if (Build.VERSION.SDK_INT >= 21)
            return !Environment.isExternalStorageRemovable(file);

        // True if default storage is non-removable and the file is in that directory
        return (!Environment.isExternalStorageRemovable() &&
                file.getPath().startsWith(Environment.getExternalStorageDirectory().getPath()));
    }

    private boolean isABookInOurLibrary(File file) throws ExtStorageUnavailableException {
        // The library only includes bloomd files
        if (file.getPath().endsWith(IOUtilities.BLOOM_BUNDLE_FILE_EXTENSION))
            return false;

        return file.getPath().startsWith(getBloomDirectory().getPath());
    }

    private boolean shouldSearchThisDirectory(File dir) throws ExtStorageUnavailableException {
        // We don't want to find the bloomd's in our library
        // Bundles are fair game everywhere
        if (searchFileUri.getPath().endsWith(IOUtilities.BLOOM_BUNDLE_FILE_EXTENSION))
            return true;

        return !dir.equals(getBloomDirectory());
    }

    private File getBloomDirectory() throws ExtStorageUnavailableException {
        if (bloomDirectory == null)
            bloomDirectory = BookCollection.getLocalBooksDirectory();
        return bloomDirectory;
    }

    private File searchForFile(File dir) throws ExtStorageUnavailableException {
        if (dir == null)
            return null;

        File[] list = dir.listFiles();
        if (list == null)
            return null;

        for (File f : list) {
            if (f.isFile() && matchesSearchFile(f))
                return f;
            if (f.isDirectory() && shouldSearchThisDirectory(f)) {
                File fileToDelete = searchForFile(f);
                if (fileToDelete != null)
                    return fileToDelete;
            }
        }
        return null;
    }

    private boolean matchesSearchFile(File file) {
        return file.getName().equals(searchFileName());
    }

    private String searchFileName() {
        // path is probably something like /document/primary:MyBook.bloomd
        String path = searchFileUri.getPath();
        int separatorIndex = Math.max(
                                path.lastIndexOf(File.separator),
                                path.lastIndexOf(':')
        );
        if (separatorIndex < 0)
            return path;
        return path.substring(separatorIndex + 1);
    }
}
