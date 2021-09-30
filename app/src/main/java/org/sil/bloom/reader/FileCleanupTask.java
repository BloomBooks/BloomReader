package org.sil.bloom.reader;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.util.Log;

import org.sil.bloom.reader.models.BookCollection;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.ref.WeakReference;

/*
    Used to clean up a bloombundle or bloompub/bloomd file after importing the contents into
    the Bloom directory.

    We only want to do this if the file is in non-removable storage.
    If the file is on an sd card, we leave it alone so that the sd card
    can be passed to another device to import the same file.
 */

public class FileCleanupTask extends AsyncTask<Uri, Void, Void> {

    private final WeakReference<Context> contextRef;
    private File bloomDirectory; // We don't want to remove bloompub's/bloomd's from here

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
            Context context = contextRef.get();
            if (context == null)
                return; // we might not be able to get it if we are in the process of shutting down.

            // File URIs don't work with various SAF functions, especially ones involving .query
            // So we need basically two complete implementations of this.
            if (uriToCleanUp.getScheme().equals("file")) {
                File searchFile = new File(uriToCleanUp.getPath());
                if (okToDelete(searchFile))
                    searchFile.delete();
                return;
            }

            if (SAFUtilities.isUriOnSdCard(context, uriToCleanUp))
                return; // we never delete things on the SD card

            // Review: should we check for books in our private storage? As far as I can tell,
            // the SAF file chooser does not allow us to choose one of them.
            // However, could there be other paths to here that do include them, especially
            // if we remove the file-url-only code above?
            // Books in the Bloom directory should not be deleted unless we're running a release build
            // (Once the SAF version reaches release we might remove this.)
            if (BloomReaderApplication.shouldPreserveFilesInOldDirectory() && SAFUtilities.IsUriInOldBloomDirectory(context, uriToCleanUp)) {
                return;
            }

            // Throws if not found (or no permission, etc.)
            DocumentsContract.deleteDocument(context.getContentResolver(), uriToCleanUp);
        }
        catch (SecurityException e) {
            // SecurityException can be thrown by File.delete()
            Log.e("BloomReader", e.getLocalizedMessage());
            assert false; // we want to look into this in debug builds
        } catch (Exception e) {
            // Note: if thinking of cutting this back, note that in at least one case we attempted
            // to clean up the same URI twice, and the second try failed not with any sensible
            // exception but with IllegalArgumentException. So I decided if for any reason we can't
            // clean up, just don't.
            e.printStackTrace();
            Log.e("BloomReader", e.getLocalizedMessage());
            assert false; // we want to look into this in debug builds
        }
    }

    private boolean isOnNonRemovableStorage(File file) {
        return !Environment.isExternalStorageRemovable(file);
    }

    private boolean okToDelete(File file) {
        // We don't delete files on removable drives, such drives may well be used to install books
        // on multiple devices. The point of deleting is to conserve space on built-in storage,
        // which is usually at a premium.
        if (!isOnNonRemovableStorage(file))
            return false;
        // If by any chance we're being asked about a book in our private books folder, we don't want
        // to clean that up!
        if (isABookInOurLibrary(file))
            return false;
        // Don't clean up things in the Bloom directory until all channels of Bloom stop using it
        // as their live storage.
        if (BloomReaderApplication.shouldPreserveFilesInOldDirectory() && isABookInOldBloomDirectory(file))
            return false;
        return true;
    }

    private boolean isABookInOldBloomDirectory(File file) {
        return file.getPath().startsWith(IOUtilities.getOldBloomBooksFolder(contextRef.get()).getPath());
    }

    private boolean isABookInOurLibrary(File file) {
        // The library does not include bloombundles
        if (file.getPath().endsWith(IOUtilities.BLOOM_BUNDLE_FILE_EXTENSION))
            return false;

        return file.getPath().startsWith(getBloomDirectory().getPath());
    }

    private boolean shouldSearchThisDirectory(File dir, String fileName) {
        // We don't want to find the bloompub's/bloomd's in our library
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
