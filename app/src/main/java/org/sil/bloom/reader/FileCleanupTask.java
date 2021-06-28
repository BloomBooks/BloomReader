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

/*
    Used to clean up a bloombundle or bloomd file after importing the contents into
    the Bloom directory.

    We only want to do this if the file is in non-removable storage.
    If the file is on an sd card, we leave it alone so that the sd card
    can be passed to another device to import the same file.
 */

public class FileCleanupTask extends AsyncTask<Uri, Void, Void> {
    private Context context;
    private File bloomDirectory; // We don't want to remove bloomd's from here

    public FileCleanupTask(Context context) {
        this.context = context;
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

            File nonRemovableStorageDir = IOUtilities.nonRemovablePublicStorageRoot(context);
            if (nonRemovableStorageDir == null)
                return;

            String fileNameFromUri = fileNameFromUri(uriToCleanUp);
            if (fileNameFromUri == null || fileNameFromUri.isEmpty())
                return;

            // Returns null if the file is not found
            File fileToDelete = searchForFile(nonRemovableStorageDir, fileNameFromUri);

            if (fileToDelete != null)
                fileToDelete.delete();
        }
        catch (ExtStorageUnavailableException|SecurityException e) {
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

    private boolean isABookInOurLibrary(File file) throws ExtStorageUnavailableException {
        // The library does not include bloombundles
        if (file.getPath().endsWith(IOUtilities.BLOOM_BUNDLE_FILE_EXTENSION))
            return false;

        return file.getPath().startsWith(getBloomDirectory().getPath());
    }

    private boolean shouldSearchThisDirectory(File dir, String fileName) throws ExtStorageUnavailableException {
        // We don't want to find the bloomd's in our library
        // Bundles are fair game everywhere
        if (fileName.endsWith(IOUtilities.BLOOM_BUNDLE_FILE_EXTENSION))
            return true;

        return !dir.equals(getBloomDirectory());
    }

    private File getBloomDirectory() throws ExtStorageUnavailableException {
        if (bloomDirectory == null)
            bloomDirectory = BookCollection.getLocalBooksDirectory();
        return bloomDirectory;
    }

    private File searchForFile(File dir, String fileName) throws ExtStorageUnavailableException {
        if (dir == null)
            return null;

        File[] list = dir.listFiles();
        if (list == null)
            return null;

        for (File f : list) {
            if (f.isFile() && f.getName().equals(fileName))
                return f;
            if (f.isDirectory() && shouldSearchThisDirectory(f, fileName)) {
                File fileToDelete = searchForFile(f, fileName);
                if (fileToDelete != null)
                    return fileToDelete;
            }
        }
        return null;
    }

    private String fileNameFromUri(Uri contentURI) {

        String fileName = "";
        try {
            String fileNameOrPath = IOUtilities.getFileNameOrPathFromUri(context, contentURI);
            if (fileNameOrPath != null && !fileNameOrPath.isEmpty())
                fileName = IOUtilities.getFilename(fileNameOrPath);
        } catch (Exception e) {
            fileName = "";
        }
        if (!fileName.isEmpty())
            return fileName;

        // From here to the end was the original code, but it seems to have stopped working in Android 10.
        // Prefer the method above, but fallback to this.
        // I admit that the ideal would be to test that the code above works and get rid of the below.
        // But I have 0% confidence that I could properly test all the right combinations of Android
        // versions and uri variations. So, I felt the safest thing was to leave this fallback in place.
        // expected path is something like /document/primary:MyBook.bloomd
        String path = contentURI.getPath();
        int separatorIndex = Math.max(
                path.lastIndexOf(File.separator),
                path.lastIndexOf(':')
        );
        if (separatorIndex < 0)
            return path;
        return path.substring(separatorIndex + 1);
    }
}
