package org.sil.bloom.reader.models;

import android.content.Context;
import android.os.Environment;

import org.sil.bloom.reader.IOUtilities;

import java.io.File;

import static org.sil.bloom.reader.IOUtilities.showError;


class SampleBookLoader {

    public static void CopySampleBooksFromAssetsIntoBooksFolder(Context context, File directory) {

        //IOUtilities.copyAssetFolder(context.getAssets(), "sample books", directory.getAbsolutePath());

        // put a sample book in the public Bloom location
        directory = Environment.getExternalStoragePublicDirectory("Bloom");
        directory.mkdirs();
        if(!directory.exists()) {
            showError(context, "Could not create " + directory.getAbsolutePath());
        }

        String fileName = "The Moon and the Cap" + Book.BOOK_FILE_EXTENSION;
        IOUtilities.copyAsset(context.getAssets(), "sample books/" + fileName, directory.getAbsolutePath() + File.separator + fileName);
    }
}
