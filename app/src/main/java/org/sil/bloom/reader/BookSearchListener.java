package org.sil.bloom.reader;

import android.net.Uri;

import java.io.File;

public interface BookSearchListener {
    // called for each book or shelf in the place we're searching
    void onFoundBookOrShelf(File bloomdFile, Uri bookOrShelfUri);
    // called for each bundle in the place we're searching
    void onFoundBundle(Uri bundleUri);
    void onSearchComplete();
}
