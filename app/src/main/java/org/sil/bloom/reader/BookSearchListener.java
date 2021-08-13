package org.sil.bloom.reader;

import android.net.Uri;

import java.io.File;

public interface BookSearchListener {
    // called for each book in the place we're searching
    void onFoundBook(File bloomdFile, Uri bookOrShelfUri);
    // called for each bundle in the place we're searching
    void onFoundBundle(Uri bundleUri);
    void onSearchComplete();
}
