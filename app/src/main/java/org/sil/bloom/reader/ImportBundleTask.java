package org.sil.bloom.reader;

import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.sil.bloom.reader.models.ExtStorageUnavailableException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.sil.bloom.reader.models.BookCollection.getLocalBooksDirectory;

public class ImportBundleTask extends AsyncTask<Uri, String, Void> {
    private MainActivity mainActivity;
    private Uri bloomBundleUri;
    private IOException ioException;
    private Toast toast;
    private List<String> newBookPaths;
    private List<Uri> bundlesToCleanUp;

    ImportBundleTask(MainActivity mainActivity) {
        this.mainActivity = mainActivity;
        // Using a single toast object allows us to update the message immediately
        this.toast = Toast.makeText(mainActivity, "", Toast.LENGTH_SHORT);
        this.newBookPaths = new ArrayList<>();
        this.bundlesToCleanUp = new ArrayList<>();
    }

    protected Void doInBackground(Uri... bundleUris) {
        try {
            for(Uri bloomBundleUri : bundleUris) {
                extractBloomBundle(bloomBundleUri);
                bundlesToCleanUp.add(bloomBundleUri);
            }
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

        new FileCleanupTask(mainActivity).execute(bundlesToCleanUp.toArray(new Uri[0]));
    }

    private void extractBloomBundle(Uri bloomBundleUri) throws IOException {
        TarArchiveInputStream tarInput = null;
        try {
            InputStream fs = mainActivity.getContentResolver().openInputStream(bloomBundleUri);
            tarInput = new TarArchiveInputStream(fs);

            final String booksDirectoryPath = getLocalBooksDirectory().getAbsolutePath();
            ArchiveEntry entry = null;
            while ((entry = tarInput.getNextEntry()) != null) {
                publishProgress(entry.getName());
                newBookPaths.add(IOUtilities.extractTarEntry(tarInput, booksDirectoryPath));
            }
            tarInput.close();
            return;
        } catch (IOException e) {
            // It could be that a .bloombundle.enc file really is still uuencoded I suppose.
            // Or the file could have been corrupted.
            if (tarInput != null)
                tarInput.close();
            throw e;
        }
    }
}