package org.sil.bloom.reader;

import android.net.Uri;

import java.io.File;

public interface BookSearchListener {
    void onNewBookOrShelf(File bloomdFile, Uri bookOrShelfUri);
    void onNewBloomBundle(Uri bundleUri);
    void onSearchComplete();
}
