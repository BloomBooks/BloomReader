package org.sil.bloom.reader;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.os.FileObserver;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.widget.Toast;

import org.sil.bloom.reader.models.Book;
import org.sil.bloom.reader.models.BookCollection;

import java.io.File;
import java.io.IOException;

public class DirectoryChangeListenerService extends Service {
    static final public String DCLS_RESULT = "org.sil.bloom.reader.DirectoryChangeListenerService.REQUEST_PROCESSED";
    static final public String DCLS_FILE_NAME_MESSAGE = "org.sil.bloom.reader.DirectoryChangeListenerService.FILE_NAME";

    static public FileObserver observer;

    private LocalBroadcastManager broadcaster;

    public DirectoryChangeListenerService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        broadcaster = LocalBroadcastManager.getInstance(this);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        stopObserving();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startObserving();
        return START_STICKY;
    }

    private void startObserving() {
        final String pathToWatch = BookCollection.getLocalBooksDirectory().getPath();
        observer = new FileObserver(pathToWatch) { // set up a file observer to watch this directory
            @Override
            public void onEvent(int event, String file) {
                //At least currently, an update also causes a CREATE event
                //if (((event & (FileObserver.CREATE | FileObserver.MODIFY)) != 0) && (file.endsWith(Book.BOOK_FILE_EXTENSION))) {
                if (((event & FileObserver.CREATE) != 0) && file.endsWith(Book.BOOK_FILE_EXTENSION)) {
                    sendObservation(pathToWatch + "/" + file);
                }
            }
        };
        observer.startWatching();
    }

    private void stopObserving() {
        observer.stopWatching();
    }

    public void sendObservation(String fileName) {
        Intent intent = new Intent(DCLS_RESULT);
        if (fileName != null)
            intent.putExtra(DCLS_FILE_NAME_MESSAGE, fileName);
        broadcaster.sendBroadcast(intent);
    }
}
