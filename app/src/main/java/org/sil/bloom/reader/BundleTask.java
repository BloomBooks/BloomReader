package org.sil.bloom.reader;

import android.os.AsyncTask;
import android.util.Log;

import java.io.File;

/*
    Async task for tarring up bloombundles in the background
 */

public class BundleTask extends AsyncTask<File, Void, File> {
    private String bundlePath;
    private BundleTaskDoneListener taskDoneListener;

    public BundleTask(String bundlePath, BundleTaskDoneListener taskDoneListener) {
        this.bundlePath = bundlePath;
        this.taskDoneListener = taskDoneListener;
    }

    @Override
    public File doInBackground(File[] files) {
        try {
            // If no files listed, bundle the whole library
            if (files == null || files.length == 0)
                IOUtilities.makeBloomBundle(bundlePath);
            else
                IOUtilities.tar(files, bundlePath);

            return new File(bundlePath);
        }
        catch (Exception e) {
            Log.e("BlReader/SharingManager", e.toString());
            return null;
        }
    }

    @Override
    public void onPostExecute(File bundleFile) {
        taskDoneListener.onBundleTaskDone(bundleFile);
    }

    public interface BundleTaskDoneListener {
        void onBundleTaskDone(File bundleFile);
    }
}