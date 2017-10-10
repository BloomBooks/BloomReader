package org.sil.bloom.reader.models;

import java.io.IOException;

/**
 * Created by rick on 10/10/17.
 */

public class ExtStorageUnavailableException extends IOException {
    public String messageForUser(){
        return "Unable to access file storage. Is the SD card missing?";
    }
}
