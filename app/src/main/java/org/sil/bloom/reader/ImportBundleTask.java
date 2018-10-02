package org.sil.bloom.reader;

import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.sil.bloom.reader.models.ExtStorageUnavailableException;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.sil.bloom.reader.models.BookCollection.getLocalBooksDirectory;

public class ImportBundleTask extends AsyncTask<Uri, String, Void> {
    private MainActivity mainActivity;
    private Uri bloomBundleUri;
    private IOException ioException;
    private Toast toast;
    private List<String> newBookPaths;

    ImportBundleTask(MainActivity mainActivity) {
        this.mainActivity = mainActivity;
        this.toast = Toast.makeText(mainActivity, "", Toast.LENGTH_SHORT);
        this.newBookPaths = new ArrayList<String>();
    }

    protected Void doInBackground(Uri... bundleUris) {
        try {
            bloomBundleUri = bundleUris[0];
            extractBloomBundle(bloomBundleUri);
        }
        catch (IOException e) {
            ioException = e;
        }
        return null;
    }

    protected void onProgressUpdate(String... filenames) {
        String toastMessage = mainActivity.getString(R.string.adding_book, filenames[0]);
        toast.setText(toastMessage);
        toast.show();
    }

    protected void onPostExecute(Void v) {
        if (newBookPaths.size() > 0)
            mainActivity.bloomBundleImported(newBookPaths);

        if (ioException != null) {
            Log.e("BundleIO", "IO exception reading bloom bundle: " + ioException.getMessage());
            ioException.printStackTrace();
            int toastMessage = ioException.getMessage().contains("ENOSPC") ?
                                                R.string.bundle_import_out_of_space :
                                                R.string.bundle_import_error;
            toast.setText(toastMessage);
            toast.show();
        }
    }

    private void extractBloomBundle(Uri bloomBundleUri) throws IOException {
        ArchiveEntry entry = null;
        String lastSuccessfulEntryName = null;

        FileDescriptor fd = mainActivity.getContentResolver().openFileDescriptor(bloomBundleUri, "r").getFileDescriptor();
        if(!fd.valid())
            throw new IOException("Invalid FileDescriptor from bloombundle");
        TarArchiveInputStream tarInput = new TarArchiveInputStream(new FileInputStream(fd));

        // Loop is terminated either by a return or by a thrown error
        while (true) {
            try {
                final String booksDirectoryPath = getLocalBooksDirectory().getAbsolutePath();
                while ((entry = tarInput.getNextEntry()) != null) {
                    publishProgress(entry.getName());
                    newBookPaths.add(IOUtilities.extractTarEntry(tarInput, booksDirectoryPath));
                    lastSuccessfulEntryName = entry.getName();
                }
                tarInput.close();
                return;
            } catch (IOException e) {
                // Maybe 10-20% of the time (with a medium sized bundle),
                // a valid FileDescriptor becomes invalid during the unpacking process
                // Having not found a way to prevent this, we catch it and try again
                // If the IOException is something else, it will go through
                if (!fd.valid()) {
                    Log.e("BundleIO", "Bad file descriptor while importing bundle. Trying again...");
                    tarInput.close();
                    fd = mainActivity.getContentResolver().openFileDescriptor(bloomBundleUri, "r").getFileDescriptor();
                    tarInput = new TarArchiveInputStream(new FileInputStream(fd));
                    // Fast-forward to the entry that failed
                    if (lastSuccessfulEntryName != null) {
                        while (!tarInput.getNextEntry().getName().equals(lastSuccessfulEntryName))
                            ;
                    }
                } else {
                    tarInput.close();
                    throw e;
                }
            }
        }
    }
}