package org.sil.bloom.reader;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import org.sil.bloom.reader.models.Book;
import org.sil.bloom.reader.models.BookCollection;
import org.sil.bloom.reader.models.ExtStorageUnavailableException;

import java.io.File;
import java.io.IOException;

// A base abstract class which every activity should extend
public abstract class BaseActivity extends AppCompatActivity {
    private Runnable mObserver;
    long mostRecentlyModifiedBloomFileTime;
    long mostRecentMarkerFileModifiedTime;
    private Handler mHandler;

    abstract protected void onNewOrUpdatedBook(String fullPath);

    // Call in onResume() if subclass wants notifications.
    protected void startObserving() throws ExtStorageUnavailableException{
        createFileObserver();
    }

    // Call in onPause if subclass calls startObserving in OnResume.
    protected void stopObserving() {
        mHandler.removeCallbacks(mObserver);
    }

    // We want to monitor for new and changed books. We ought to be able to get notifications
    // efficiently and reliably using FileObserver, but as documented in
    // https://issuetracker.google.com/issues/37065227 this does not work in Android 6 and possibly
    // some versions of Android 7 for files modified by the MTP process.
    // We work around this by doing two things.
    // 1. We keep track of the most recent modify time of any file in the bloom book directory,
    // and when it increases, we send a notification that the most recently modified file there
    // has changed.
    // However, we believe it is too costly to do this every second (measured as 5ms for a folder
    // of only 25 books on a Nexus 5X, and presumably proportional to folder size). So,
    // 2. We have arranged that bloom desktop, when it sends a new book via MTP, will also send
    // a 'new' version of a tiny file called something.modified. Doing this will change the modify
    // time on that file. So the task that runs every second only has to check to see whether
    // this one file's modify time has changed. If so, it does the full check to see whether it
    // can find a file more recently modified than the last one recorded, and if so sends the
    // notification.
    // There should be no contention for access to something.modified, because BloomReader never
    // accesses or locks it; all it does is check its modify time.
    private void createFileObserver() throws ExtStorageUnavailableException {
        final String pathToWatch = BookCollection.getLocalBooksDirectory().getPath();
        String [] mostRecent = new String[1];
        if (mHandler == null) {
            // Calling for the first time (on startup of this activity). Assume it's just read
            // the files and doesn't need a notification; so we want to initialize
            // the current most-recent-modify-time.
            // If we already have mHandler, we don't need a new one, and do NOT want to
            // update the modify time we already have, since we do want notifications
            // about any changes since last pause.
            mostRecentlyModifiedBloomFileTime = getLatestModifedTimeAndFile(new File(pathToWatch)).time;
            mHandler = new Handler();
        }
        mObserver = new Runnable() {
            @Override
            public void run() {
                try {
                    // must match what is written in AndroidDeviceUsbConnection.SendFile
                    // Note that the file might not exist. By test, the value we get for
                    // lastModified in that case is such that if it is later created,
                    // we will interpret that as an update.
                    String markerFilePath = pathToWatch + "/" + "something.modified";
                    long markerModified = new File(markerFilePath).lastModified();
                    if (markerModified == mostRecentMarkerFileModifiedTime)
                        return; // presume nothing changed
                    mostRecentMarkerFileModifiedTime = markerModified;
                    // Now look and see what actually changed (most recently)
                    notifyIfNewFileChanges(pathToWatch);

                } finally {
                    // We will run this task again a second later (unless stopObserving is called).
                    mHandler.postDelayed(mObserver, 1000);
                }
            }
        };
        // Start the loop.
        mHandler.postDelayed(mObserver, 1000);
    }

    // Look for new changes to files and send notification if there have been any.
    protected void notifyIfNewFileChanges(final String pathToWatch) {
        PathModifyTime newModifyData = getLatestModifedTimeAndFile(new File(pathToWatch));
        if (newModifyData.time > mostRecentlyModifiedBloomFileTime) {
            mostRecentlyModifiedBloomFileTime = newModifyData.time;
            onNewOrUpdatedBook(newModifyData.path);
        }
    }

    private static PathModifyTime getLatestModifedTimeAndFile(File dir) {
        //long startTime = System.currentTimeMillis();
        File[] files = dir.listFiles();
        String lastModFile = null;
        long latestTime = 0;
        for (File file : files) {
            if (!file.isDirectory() && file.getName().endsWith(Book.BOOK_FILE_EXTENSION)) {
                long modified = file.lastModified();
                if (modified > latestTime) {
                    latestTime = modified;
                    lastModFile = file.getPath();
                }
            }
        }
        //long elapsedTime = System.currentTimeMillis() - startTime;
        PathModifyTime result = new PathModifyTime();
        result.path = lastModFile;
        result.time = latestTime;
        return result;
    }

    public static void playSoundFile(int id) {
        Context bloomApplicationContext = BloomReaderApplication.getBloomApplicationContext();
        if (bloomApplicationContext == null)
            return; // unlikely, but better to skip the sound than crash.
        MediaPlayer mp = MediaPlayer.create(bloomApplicationContext, id);
        mp.start();
    }
}

class PathModifyTime {
    public String path;
    public long time;
}
