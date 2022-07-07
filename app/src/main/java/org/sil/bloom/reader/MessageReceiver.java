package org.sil.bloom.reader;

// This interface just exists to allow ReaderActivity and BloomLibraryActivity both to be
// passed to WebAppInterface as the object that should receive messages from the javascript
// in the browser. To use correctly in this way, implementors should be able to be cast to
// Context.
public interface MessageReceiver {
    void receiveMessage(String message);
}
