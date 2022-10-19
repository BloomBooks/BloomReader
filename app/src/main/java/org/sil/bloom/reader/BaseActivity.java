package org.sil.bloom.reader;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.segment.analytics.Properties;

import org.json.JSONException;
import org.json.JSONObject;
import org.sil.bloom.reader.models.BookCollection;

import java.io.File;
import java.util.Iterator;

// A base abstract class which every activity should extend
public abstract class BaseActivity extends AppCompatActivity {
    private Runnable mObserver;
    long mostRecentlyModifiedBloomFileTime;
    long mostRecentMarkerFileModifiedTime;
    private Handler mHandler;

    // This is our "legacy" storage model which allowed us to gain
    // general file system access by user permission.
    // In Android 11, this became unavailable and we must use private storage
    // or gain access via Storage Access Framework (SAF).
    //
    // We could have returned true here if running on Android 11 and
    // the user still has legacy storage access because they did an upgrade
    // (see android:preserveLegacyExternalStorage="true" in AndroidManifest.xml).
    // However, that would mean users with Android 11 could have different experiences
    // and even the same user would have different experiences if he uninstalled/reinstalled.
    // In general, if we have the migrated permission, haveLegacyStoragePermission will return
    // true, and we won't call this. If we don't have it, this is a good criterion for deciding
    // whether to ask for it.
    public static boolean osAllowsGeneralStorageAccess() {
        // Counter-intuitively, Build.VERSION.SDK_IN is the version of the Android system
        // we are running under, not the one we were built for. Q is Android 10, the last
        // version where the user could give us this permission.
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q;
    }

    // This is true if we have the old-style permission, that is, the user has actually granted it.
    // This MIGHT be true on Android 11, but only if Bloom was upgraded from an earlier version
    // where the permission was already granted.
    public static boolean haveLegacyStoragePermission(Context context) {
        // Counter-intuitively, Build.VERSION.SDK_IN is the version of the Android system
        // we are running under, not the one we were built for.
        // Once we tested on 12 in release, we realized the checkSelfPermission calls were returning true
        // for an upgraded phone even though we didn't actually have write permission to move files.
        // So we added the check for Android version <=11 to make things behave the way we expected
        // (that version 12+ will never claim to have any legacy permission).
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.R &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    // Given a JSONObject, obtained by parsing a JSON string sent by BloomPlayer,
    // send an analytics report. The data object is expected to contain fields
    // "event" (the first argument to track), and "params", an arbitrary object
    // each of whose fields will be used as a name-value pair in the Properties
    // of the track event.
    void sendAnalytics(JSONObject data) {
        String event = null;
        JSONObject params = null;
        try {
            event = data.getString("event");
            params = data.getJSONObject("params");
        } catch (JSONException e) {
            Log.e("sendAnalytics", "analytics event missing event or params");
            return;
        }

        Properties p = new Properties();
        Iterator<String> keys = params.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            try {
                p.putValue(key, params.get(key));
            } catch (JSONException e) {
                Log.e("sendAnalytics", "Very unexpectedly we can't get a value whose key we just retrieved");
            }
        }
        BloomReaderApplication.ReportAnalyticsWithLocationIfPossible(this, event, p);
    }

    protected void moveBookFileToLocalFolderLegacy(boolean preserveFilesInOldDirectory, File bookFileToMove, File dest) {
        // Originally we did a copy when preserveFilesInOldDirectory == true
        // and a renameTo when preserveFilesInOldDirectory == false.
        // But it turned out that in some cases, renameTo was failing where delete was succeeding.
        // By copying and then deleting, we get the books copied regardless of whether we
        // successfully delete. And we make the two code paths more similar
        // regardless of the preserveFilesInOldDirectory setting. See BL-10863.
        boolean fileCopied = false;
        try {
            fileCopied = IOUtilities.copyFile(bookFileToMove.getPath(), dest.getPath());
            if (!fileCopied) {
                Log.e("moveOrCopyFromBloomDir", "Failed to copy file " + bookFileToMove.toString());
            }
        } catch (Exception e) {
            Log.e("moveOrCopyFromBloomDir", e.getMessage());
        }
        if (fileCopied && !preserveFilesInOldDirectory) {
            try {
                boolean fileDeleted = bookFileToMove.delete();
                if (!fileDeleted) {
                    Log.e("moveOrCopyFromBloomDir", "Failed to delete file " + bookFileToMove.toString());
                }
            } catch (Exception e) {
                Log.e("moveOrCopyFromBloomDir", e.getMessage());
            }
        }
    }

    protected void moveBookFileToLocalFolderSAF(boolean preserveFilesInOldDirectory, Uri bookOrShelfUri, File privateStorageFile) {
        SAFUtilities.copyUriToFile(this, bookOrShelfUri, privateStorageFile);
        if (!preserveFilesInOldDirectory) {
            SAFUtilities.deleteUri(this, bookOrShelfUri);
        }
    }

    abstract protected void onNewOrUpdatedBook(String fullPath);

    // Call in onResume() if subclass wants notifications.
    protected void startObserving(){
        createFileObserver();
    }

    // Call in onPause if subclass calls startObserving in OnResume.
    protected void stopObserving() {
        if (mHandler != null)
            mHandler.removeCallbacks(mObserver);
    }

    // If we import a bundle while the FileObserver is running, the user has already been notified
    // about these files, and we don't want another notification the next time onResume() is called.
    protected void resetFileObserver() {
        mostRecentlyModifiedBloomFileTime = getLatestModifiedTimeAndFile().time;
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
    private void createFileObserver() {
        if (mHandler == null) {
            // Calling for the first time (on startup of this activity). Assume it's just read
            // the files and doesn't need a notification; so we want to initialize
            // the current most-recent-modify-time.
            // If we already have mHandler, we don't need a new one, and do NOT want to
            // update the modify time we already have, since we do want notifications
            // about any changes since last pause.
            mostRecentlyModifiedBloomFileTime = getLatestModifiedTimeAndFile().time;
            mHandler = new Handler();
        }
        mObserver = () -> {
            try {
                if (haveLegacyStoragePermission(this)) {
                    // must match what is written in AndroidDeviceUsbConnection.SendFile
                    // Note that the file might not exist. By test, the value we get for
                    // lastModified in that case is such that if it is later created,
                    // we will interpret that as an update.
                    String markerFilePath = BookCollection.getBloomDirectory().getPath() + "/" + BloomReaderApplication.SOMETHING_MODIFIED_FILE_NAME;
                    long markerModified = new File(markerFilePath).lastModified();
                    if (markerModified == mostRecentMarkerFileModifiedTime)
                        return; // presume nothing changed
                    mostRecentMarkerFileModifiedTime = markerModified;
                    // Now look and see what actually changed (most recently)
                    handleNewFileChanges();
                } else if (SAFUtilities.hasPermissionToBloomDirectory(this)){
                    // look for it using SAF
                    Uri uriMarkerFile = SAFUtilities.fileUriFromDirectoryUri(
                            this, SAFUtilities.getBloomDirectoryTreeUri(), BloomReaderApplication.SOMETHING_MODIFIED_FILE_NAME);
                    long markerModified = IOUtilities.lastModified(this, uriMarkerFile);
                    if (markerModified == mostRecentMarkerFileModifiedTime)
                        return; // presume nothing changed
                    mostRecentMarkerFileModifiedTime = markerModified;
                    handleNewFileChanges();
                }

            } finally {
                // We will run this task again a second later (unless stopObserving is called).
                mHandler.postDelayed(mObserver, 1000);
            }
        };
        // Start the loop.
        mHandler.postDelayed(mObserver, 1000);
    }

    // Look for new changes to files in the Bloom directory and add/update the newest file to
    // our private book collection.
    // Specifically, it looks at the something.modified file to determine if there are changes.
    protected void handleNewFileChanges() {
        PathModifyTime newModifyData = getLatestModifiedTimeAndFile();
        if (newModifyData.time > mostRecentlyModifiedBloomFileTime) {
            mostRecentlyModifiedBloomFileTime = newModifyData.time;

            // The file is in the Bloom directory. Move it to private book collection.
            File privateStorageFile;
            if (haveLegacyStoragePermission(this)) {
                File newFile = new File(newModifyData.path);
                privateStorageFile = new File(BookCollection.getLocalBooksDirectory(), newFile.getName());
                moveBookFileToLocalFolderLegacy(
                        // In this case, the file is being added in the context of a specific
                        // channel of Bloom Reader. So there's no reason to keep it around for other channels.
                        false,
                        newFile,
                        privateStorageFile);
            } else {
                // Must have SAF permission to the directory or we wouldn't be able
                // to determine there is something new/updated.
                String fileName = BookCollection.fixBloomd(IOUtilities.getFileNameFromUri(this, newModifyData.uri));
                privateStorageFile = new File(BookCollection.getLocalBooksDirectory(), fileName);
                moveBookFileToLocalFolderSAF(
                        // In this case, the file is being added in the context of a specific
                        // channel of Bloom Reader. So there's no reason to keep it around for other channels.
                        false,
                        newModifyData.uri,
                        privateStorageFile);
            }

            onNewOrUpdatedBook(privateStorageFile.getAbsolutePath());
        }
    }

    private PathModifyTime getLatestModifiedTimeAndFile() {
        return haveLegacyStoragePermission(this) ?
                getLatestModifiedTimeAndFile(BookCollection.getBloomDirectory())
                : getLatestModifiedTimeAndFile(SAFUtilities.getBloomDirectoryTreeUri());
    }

    private static PathModifyTime getLatestModifiedTimeAndFile(File dir) {
        //long startTime = System.currentTimeMillis();
        PathModifyTime result = new PathModifyTime();
        String lastModFile = null;
        long latestTime = 0;
        File[] files = dir.listFiles();

        // Fixing NullPointerException reported in Play console
        if (files == null)
        {
            result.path = lastModFile;
            result.time = latestTime;
            return result;
        }

        for (File file : files) {
            if (!file.isDirectory() && IOUtilities.isBloomPubFile(file.getName())) {
                long modified = file.lastModified();
                if (modified > latestTime) {
                    latestTime = modified;
                    lastModFile = file.getPath();
                }
            }
        }
        //long elapsedTime = System.currentTimeMillis() - startTime;
        result.path = lastModFile;
        result.time = latestTime;
        return result;
    }

    // This overload uses SAF
    private static PathModifyTime getLatestModifiedTimeAndFile(Uri dir) {
        PathModifyTime result = new PathModifyTime();
        final Context context = BloomReaderApplication.getBloomApplicationContext();
        if (!SAFUtilities.hasPermission(context, dir))
            return result;
        //long startTime = System.currentTimeMillis();

        final Uri[] lastModFile = {null};
        final long[] latestTime = {0};

        SAFUtilities.searchDirectoryForBooks(context, dir, new BookSearchListener() {
            @Override
            public void onFoundBookOrShelf(File bloomPubFile, Uri bookOrShelfUri) {
                long modified = IOUtilities.lastModified(context, bookOrShelfUri);
                if (modified > latestTime[0]) {
                    latestTime[0] = modified;
                    lastModFile[0] = bookOrShelfUri;
                }
            }

            @Override
            public void onFoundBundle(Uri bundleUri) {

            }

            @Override
            public void onSearchComplete() {

            }
        });
        result.uri = lastModFile[0];
        result.time = latestTime[0];
        return result;
    }

    public static void playSoundFile(int id) {
        Context bloomApplicationContext = BloomReaderApplication.getBloomApplicationContext();
        if (bloomApplicationContext == null)
            return; // unlikely, but better to skip the sound than crash.
        final MediaPlayer mp = MediaPlayer.create(bloomApplicationContext, id);
        mp.setOnCompletionListener(mediaPlayer -> mp.release());
        mp.start();
    }
}

// We set path or uri, depending on whether we are using legacy storage or SAF
class PathModifyTime {
    public String path;
    public Uri uri;
    public long time;
}
