package org.sil.bloom.reader;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;

import java.io.File;

/*
    We were storing thumbnails with the extension .png
    but now that we have .jpg thumbnails too, we're keeping
    the extension out of the filename.

    This task cleans up any old thumbnails stored with the old
    naming system.
 */


public class ThumbnailCleanup extends AsyncTask<File, Void, Void> {

    public static final String DID_THUMB_CLEANUP = "didThumbCleanupRight";

    private SharedPreferences values;

    public ThumbnailCleanup(Context context){
        values = context.getSharedPreferences(BloomReaderApplication.SHARED_PREFERENCES_TAG, 0);
    }

    @Override
    public Void doInBackground(File... files){

        // Only need to run this once
        if (values.getBoolean(DID_THUMB_CLEANUP, false))
            return null;

        File thumbsDirectory = files[0];
        String[] filenames = thumbsDirectory.list();
        for(String filename : filenames){
            if (filename.endsWith(".png"))
                new File(thumbsDirectory + File.separator + filename).delete();
        }

        SharedPreferences.Editor valuesEditor = values.edit();
        valuesEditor.putBoolean(DID_THUMB_CLEANUP, true);
        valuesEditor.apply();

        return null;
    }
}
