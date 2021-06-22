package org.sil.bloom.reader;

import java.io.File;

public interface BookSearchListener {
    void onNewBookOrShelf(File bloomdFile);
    void onNewBloomBundle(File bundleFile);
    void onSearchComplete();
}
