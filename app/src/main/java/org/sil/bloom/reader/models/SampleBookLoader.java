package org.sil.bloom.reader.models;

import android.content.Context;
import android.content.ContextWrapper;
import org.sil.bloom.reader.AssetCopier;
import java.io.File;


class SampleBookLoader {

    public static void CopySampleBooksFromAssetsIntoBooksFolder(Context context) {
        ContextWrapper cw = new ContextWrapper(context);
        File directory = cw.getExternalFilesDir("books");

        //enhance: remove existing sample files

        AssetCopier.copyAssetFolder(context.getAssets(), "sample books", directory.getAbsolutePath());
    }
}
