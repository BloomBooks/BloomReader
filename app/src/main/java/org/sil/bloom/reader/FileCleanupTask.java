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

public class FileCleanupTask extends AsyncTask<Void, Void, Void> {
    private Context context;
    private Uri searchFileUri;
    private File bloomDirectory; // We don't want to search here

    public FileCleanupTask(Context context, Uri searchFileUri) {
        this.context = context;
        this.searchFileUri = searchFileUri;
    }

    @Override
    public Void doInBackground(Void... v) {
        // If the URI is file:// we have direct access to the file,
        // otherwise we have to search for it

        if (searchFileUri.getScheme() == "file") {
            File searchFile = new File(searchFileUri.getPath());
            if (onNonRemovableStorage(searchFile))
                searchFile.delete();
            return null;
        }

        try {
            bloomDirectory = BookCollection.getLocalBooksDirectory();

            File nonRemovableStorageDir = getNonRemovableStorageDir();
            if (nonRemovableStorageDir == null)
                return null;

            File fileToDelete = searchForFile(nonRemovableStorageDir);

            if (fileToDelete != null)
                fileToDelete.delete();
        }
        catch (ExtStorageUnavailableException e) {
            Log.e("BloomReader", e.getLocalizedMessage());
        }

        return null;
    }

    private boolean onNonRemovableStorage(File file) {
        if (Build.VERSION.SDK_INT >= 21)
            return !Environment.isExternalStorageRemovable(file);

        // True if default storage is non-removable and the file is in that directory
        return (!Environment.isExternalStorageRemovable() &&
                file.getPath().startsWith(Environment.getExternalStorageDirectory().getPath()));
    }

    private File getNonRemovableStorageDir() {
        // Common case - primary "external" storage is non-removable
        if (!Environment.isExternalStorageRemovable())
            return Environment.getExternalStorageDirectory();

        return findNonRemovableStorageDir();
    }

    private File findNonRemovableStorageDir() {
        if (Build.VERSION.SDK_INT >= 21) {
            File[] appStorageDirs = context.getExternalFilesDirs("");
            for (File appStorageDir : appStorageDirs) {
                if (!Environment.isExternalStorageRemovable(appStorageDir))
                    return getNonRemovableStorageRootDir(appStorageDir);
            }
        }
        return null;
    }

    private File getNonRemovableStorageRootDir(File appStorageDir) {
        // appStorageDir is a directory within the public storage with a path like
        // /path/to/public/storage/Android/data/org.sil.bloom.reader/files

        String path = appStorageDir.getPath();
        int androidDirIndex = path.indexOf(File.separator + "Android" + File.separator);
        if (androidDirIndex > 0)
            return new File(path.substring(0, androidDirIndex));
        return null;
    }

    private File searchForFile(File dir) {
        if (dir == null)
            return null;

        File[] list = dir.listFiles();
        if (list == null)
            return null;

        for (File f : list) {
            if (f.isFile() && matchesSearchFile(f))
                return f;
            if (f.isDirectory() && !f.equals(bloomDirectory)) {
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
