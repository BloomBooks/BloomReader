package org.sil.bloom.reader.models;

import android.content.Context;

import org.sil.bloom.reader.IOUtilities;

import java.io.File;

import static org.sil.bloom.reader.IOUtilities.showError;


class SampleBookLoader {

    public static void CopySampleBooksFromAssetsIntoBooksFolder(Context context, File directory) {

        //IOUtilities.copyAssetFolder(context.getAssets(), "sample books", directory.getAbsolutePath());

        // put a sample book in the public Bloom location
        if (!directory.exists()) {
            showError(context, "Could not create " + directory.getAbsolutePath());
            return;
        }

        String fileName = "The Moon and the Cap.bloompub";
        IOUtilities.copyAsset(context.getAssets(), "sample books/" + fileName, directory.getAbsolutePath() + File.separator + fileName);

        cleanUpLegacySample(directory);
    }

    private static void cleanUpLegacySample(File directory) {
        String legacyFileName = "The Moon and the Cap.bloomd";
        File legacyFile = new File(directory.getAbsolutePath() + File.separator + legacyFileName);
        if (legacyFile.exists())
            legacyFile.delete();
    }
}
