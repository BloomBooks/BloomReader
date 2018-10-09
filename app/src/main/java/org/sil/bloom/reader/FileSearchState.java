package org.sil.bloom.reader;

import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

/*
    Used in MainActivity to track the state of an ongoing File Search
 */

public class FileSearchState {
    // These have been imported, but original files haven't been cleaned up yet
    public List<Uri> bloomdsAdded = new ArrayList<>();

    // These will all be imported at the end (and cleaned up at the same time)
    public List<Uri> bundlesToAdd = new ArrayList<>();

    public boolean nothingAdded() {
        return bloomdsAdded.isEmpty() && bundlesToAdd.isEmpty();
    }

    public Uri[] bloomdsAddedAsArray() {
        return bloomdsAdded.toArray(new Uri[0]);
    }

    public Uri[] bundlesToAddAsArray() {
        return bundlesToAdd.toArray(new Uri[0]);
    }
}
