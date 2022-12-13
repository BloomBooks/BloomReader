package org.sil.bloom.reader;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewParent;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.segment.analytics.Analytics;
import com.segment.analytics.Properties;

import org.sil.bloom.reader.models.BookCollection;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// This view appears at the bottom of BloomLibraryActivity or MainActivity. Usually it has size zero
// and cannot be seen. During and after downloads, it displays a DownloadProgressView or
// BookReadyView to indicate the progress and completion of downloads. It also contains the
// code to initialize a download and to check on the status of pending downloads when the parent
// activity is resumed and update its contents accordingly.
// The view was originally designed to support multiple child views (such as several downloads in
// progress and several notices about completed downloads) but we decided not to do that.
public class DownloadsView extends LinearLayout {

    // This is the directory (under our private directory in external storage) to which we
    // download books. Unlike our internal private storage, it is possible to give other apps
    // (in particular, the DownloadManager) access to this. Books downloaded here are copied to
    // our main books directory and then deleted.
    private static final String BL_DOWNLOADS = "bl-downloads";
    DownloadProgressView mProgressView;
    Context mContext;
    static List<DownloadsView> sInstances = new ArrayList<DownloadsView>();
    boolean mRecentMultipleDownloads;

    public DownloadsView(Context context) {
        super(context);
        initializeViews(context);
    }

    public DownloadsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initializeViews(context);
    }


    class DownloadData {

        public DownloadData(String destPath) {
            this.destPath = destPath;
            //progressView = view;
        }
        public String destPath;
        public int progress;
        //public DownloadProgressView progressView;
    }

    // We use a concurrent hash map here because it can be accessed by callbacks on other threads
    // when the DownloadManager sends us notifications. There might be some case where this is
    // not enough protection from race conditions to produce exactly the ideal behavior, but it
    // should at least prevent crashes, and it should be extremely rare for multiple downloads to
    // finish at the same instant.
    ConcurrentHashMap<Long, DownloadData> mDownloadsInProgress = new ConcurrentHashMap<Long, DownloadData>();
    DownloadManager mDownloadManager;

    // Arbitrary identifier to indicate that we would like to update download progress
    private static final int UPDATE_DOWNLOAD_PROGRESS = 1973;

    // Use a background thread to check the progress of downloading
    private ExecutorService executor;

    // The boilerplate I started from has this, but it seems to work fine to update progress directly:
    // Use a handler to update progress bar on the main thread
//    private final Handler mainHandler = new Handler(Looper.getMainLooper(), new Handler.Callback() {
//        @Override
//        public boolean handleMessage(@NonNull Message msg) {
//            if (msg.what == UPDATE_DOWNLOAD_PROGRESS) {
//                int downloadProgress = msg.arg1;
//
//                // Update your progress bar here.
//                //progressBar.setProgress(downloadProgress);
//            }
//            return true;
//        }
//    });

    public void cancelDownloads() {
        for (long downloadId: mDownloadsInProgress.keySet()) {
            DownloadData data = mDownloadsInProgress.get(downloadId);
            // stop the download
            mDownloadManager.remove(downloadId);
            // Delete any incomplete temp file
            File source = new File(data.destPath);
            source.delete();
        }
        mDownloadsInProgress.clear();

        // remove the view showing its progress
        removeView(mProgressView);
        updateLayoutForChangedChildList();
    }

    private void updateLayoutForChangedChildList() {
        ViewParent root = this.getParent();
        if (root == null) {
            // Can get called in constructor, in which case, we assume the parent layout will be
            // correctly recomputed when we are added to it.
        } else {
            // If we're already in a root, need to update it, too, since we changed size.
            root.requestLayout();
        }
        this.invalidate();
    }

    // Update the progress bar. This is only an approximation if we have multiple downloads...it computes the average
    // progress of all the downloads still happening. To do better, we'd need to know the actual
    // size of each download, and keep track of completed ones also. I think this might be good
    // enough.
    private void updateProgress() {
        int progress = 0;
        for (DownloadData data : mDownloadsInProgress.values()) {
            progress += data.progress;
        }

        // The size can change while we are processing the progress.
        int numDownloads = mDownloadsInProgress.size();
        if (numDownloads == 0)
            return;
        mProgressView.setProgress(progress / numDownloads);
    }

    private void startMonitoringDownloads() {
        if (executor == null) {
            executor = Executors.newFixedThreadPool(1);
        }
        // Run a task in a background thread to check download progress
        executor.execute(new Runnable() {
            @Override
            public void run() {
                int progress = 0;
                while (mDownloadsInProgress.size() > 0) {
                    for (long downloadId: mDownloadsInProgress.keySet()) {
                        DownloadData data = mDownloadsInProgress.get(downloadId);
                        Cursor cursor = mDownloadManager.query(new DownloadManager.Query().setFilterById(downloadId));
                        if (cursor.moveToFirst()) {
                            int downloadStatus = CommonUtilities.getIntFromCursor(cursor, DownloadManager.COLUMN_STATUS);
                            switch (downloadStatus) {
                                case DownloadManager.STATUS_RUNNING:
                                    long totalBytes = CommonUtilities.getLongFromCursor(cursor, DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                                    if (totalBytes > 0) {
                                        long downloadedBytes = CommonUtilities.getLongFromCursor(cursor, DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                                        if (downloadedBytes > 0)
                                            progress = (int) (downloadedBytes * 100 / totalBytes);
                                    }
                                    break;
                                case DownloadManager.STATUS_SUCCESSFUL:
                                    progress = 100;
                                    // todo: any cleanup? I think it's handled in complete handler.
                                    break;
                                case DownloadManager.STATUS_PAUSED:
                                case DownloadManager.STATUS_PENDING:
                                    break;
                                case DownloadManager.STATUS_FAILED:
                                    // todo: something?
                                    break;
                            }
                            // Use this if we find we do need to do it on the UI thread.
                            //Message message = Message.obtain();
                            //message.what = UPDATE_DOWNLOAD_PROGRESS;
                            //message.arg1 = progress;
                            //mainHandler.sendMessage(message);
                            data.progress= progress;
                            updateProgress();
                        }
                    }
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                if (executor != null) {
                    // Not sure how we get here with it null, but I did.
                    executor.shutdown(); // we'll restart it if we do another download.
                    executor = null; // we'll make a new one if we need it.
                }
            }
        });
    }

    private File getDownloadDir() {
        // We need to set a destination for our downloads. This took considerable experiment.
        // The following works, but the book is left in the main Downloads directory, from which we
        // don't seem to be able to delete it.
        // request.setDestinationInExternalFilesDir(BloomLibraryActivity.this, Environment.DIRECTORY_DOWNLOADS,fileName + ".bloompub");

        // The following should download the book directly into our private folder.
        // This would be ideal, except that we'd have to worry about incomplete ones,
        // but it generates a security exception on my test device.
        // https://stackoverflow.com/questions/39744505/how-to-save-files-with-downloadmanager-in-private-path
        // has an answer that says DownloadManager runs in its own process and does not have
        // permission to access our private storage; suggests a possible 3rd party library.
        // Uri target = Uri.fromFile(new File(BookCollection.getLocalBooksDirectory(), fileName + ".bloompub"));
        // request.setDestinationUri(target);

        // So, I finally came up with this, which downloads to our external files directory.
        // Passing null is supposed to give us a folder that we have access to, but which is
        // also a legitimate target for DownloadManager (at least if targeting something after
        // Android Q, which is now required).
        // See https://developer.android.com/reference/android/app/DownloadManager.Request#setDestinationUri(android.net.Uri).
        // Apparently there is a theoretical possibility that a device doesn't provide the app with
        // an externalFilesDir, but it seems to be unheard-of; devices without a real SD card
        // emulate one.
        File result = new File(DownloadsView.this.mContext.getExternalFilesDir(null), BL_DOWNLOADS);
        result.mkdirs(); // make sure it exists.
        return result;
    }

    // This is a much simpler way of extracting a filename than we use in IOUtilities, OK because
    // we create these URIs ourselves and know they are based on a local file.
    public static String getFileNameFromUri(String path) {
        return path.replaceFirst(".*/", "").replaceFirst("\\.[^.]*$", "");
    }

    private void initializeViews(Context context) {
        mContext = context;
        sInstances.add(this);
        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.downloads, this);

        mDownloadManager = (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);

        //set filter to only when download is complete and register broadcast receiver
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        mContext.registerReceiver(mDownloadReceiver, filter);
    }

    public void updateUItoCurrentState() {
        // We will clear out any existing download progress view and then reinsert any needed
        // to reflect the current state.
        // I originally wanted to keep any BookReadyViews so that if there were several and the
        // user clicked to read one, they don't all go away. This is obsolete, since we now never
        // have more than one BookReadyView.
        // However, it's not obvious whether we should remove even just one.
        // - If we were in BloomLibraryActivity and hit "View books", we want to get rid of the
        // one in MainActivity when we resume that.
        // - If we were showing it, then switched to another app and back, we probably still want
        // to see it.
        // - If we were showing a single one in BloomLibraryActivity, and did several back presses
        // or Home to get to MainActivity, we probably still want to see it.
        // So, I'm currently thinking the right answer is to remove only non-book-ready views.
        // In the cases where they should be removed, the individual tasks do so.
        // Keeping this as a loop for now, though at present there is only at most one child.
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View child = getChildAt(i);
            if (!(child instanceof BookReadyView)) {
                removeViewAt(i);
            }
        }
        // we will rediscover any that are still running, and want to create new progress views for them.
        mDownloadsInProgress.clear();
        Cursor cursor = mDownloadManager.query(new DownloadManager.Query());
        if (cursor.moveToFirst()) {
            do {
                String uriString = CommonUtilities.getStringFromCursor(cursor, DownloadManager.COLUMN_LOCAL_URI);
                String path = "";
                try {
                    if (uriString != null) {
                        Uri uri = Uri.parse(uriString);
                        path = uri.getPath();
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    // Apart from logging, if it's not a parseable URI we'll just hope it's not one of ours.
                }
                if (!path.contains("/" + BL_DOWNLOADS + "/")) {
                    continue; // some unrelated download
                }
                String fileName = getFileNameFromUri(path);
                // We can't directly get a File from the download URI. However, we'll only use this if we're pretty sure
                // this is one of our downloads from the presence of the right subdirectory in the
                // path, so it should be pretty safe to use our standard destination.
                File downloadDest = new File(getDownloadDir(), fileName + ".bloompub");
                long downloadId = CommonUtilities.getLongFromCursor(cursor, DownloadManager.COLUMN_ID);
                int downloadStatus = CommonUtilities.getIntFromCursor(cursor, DownloadManager.COLUMN_STATUS);
                if (downloadId > 0 && downloadStatus > 0) {
                    switch (downloadStatus) {
                        default:
                            // if we see a failed download, for now we'll ignore it.
                            continue;
                            // But if it's running or paused or pending we want to show the status and allow it to complete.
                        case DownloadManager.STATUS_RUNNING:
                        case DownloadManager.STATUS_PAUSED:
                        case DownloadManager.STATUS_PENDING:
                            showDownloadProgress(downloadId, downloadDest);
                            break;
                        // And if one has finished since our last call of this method, even while
                        // our app was not running, we'll show the complete message.
                        case DownloadManager.STATUS_SUCCESSFUL:
                            String downloadDescription = CommonUtilities.getStringFromCursor(cursor, DownloadManager.COLUMN_DESCRIPTION);
                            handleDownloadComplete(downloadDest.getPath(), downloadId, downloadDescription);
                            break;
                    }
                }

            } while (cursor.moveToNext());
        }

        cursor.close();
        cleanupDownloadDirectory();
    }
    public void onDownloadStart(String url, String userAgent,
                                String contentDisposition, String mimetype,
                                long contentLength, String sourceUrl) {

        Uri Download_Uri = Uri.parse(url);
        DownloadManager.Request request = new DownloadManager.Request(Download_Uri);
        String fileName = getFileNameFromUri(Download_Uri.getPath());
        if (fileName == null || fileName.equals(""))
        {
            // If there isn't a .bloompub file at the location we requested,
            // we seem to get a meaningless url (probably from a 404 redirect).
            // We have changed things on the blorg side now so this should
            // not be possible; so it isn't worth localizing the error message.
            Toast toast = Toast.makeText(mContext, "A problem occurred while downloading that book.", Toast.LENGTH_LONG);
            toast.show();
            return;
        }
        String template = getContext().getString(R.string.downloading_file);
        request.setTitle(String.format(template, fileName));
        File downloadDest = new File(getDownloadDir(), fileName + ".bloompub");
        Uri target = Uri.fromFile(downloadDest);
        request.setDestinationUri(target);
        request.setDescription(sourceUrl);
        long downloadId = mDownloadManager.enqueue(request);
        showDownloadProgress(downloadId, downloadDest);
    }


    @Override
    public void onDetachedFromWindow() {
        mContext.unregisterReceiver(mDownloadReceiver);
        sInstances.remove(this);
        super.onDetachedFromWindow();
    }

    private void cleanupDownloadDirectory() {
        if (mDownloadsInProgress.size() > 0) {
            return; // unsafe to clean up, may be in use
        }
        File downloadDir = getDownloadDir();
        for (File leftover : downloadDir.listFiles())
        {
            leftover.delete();
        }
    }

    private void showDownloadProgress(long downloadId, File dest) {
        // We save the information about current downloads in a dictionary because it's possible that
        // the user has more than one download going on, and we need this information in our
        // downloadReceiver function that gets called when the download is complete. This also helps
        // keep track of how many downloads are in progress.
        if (mDownloadsInProgress.get(downloadId) != null) {
            // When initializing, we can find several references to the same download-in-progress.
            // (Or it may have just been that I was calling the function repeatedly. In any case,
            // a good precaution.)
            return;
        }
        if (mDownloadsInProgress.size() > 0) { // will be > 1, after we add this one
            mProgressView.setBook(""); // puts it in multiple downloads mode
            // remember that we've been in this state, it affects how we report completion.
            mRecentMultipleDownloads = true;
        } else {
            // we'll add one for the current single download.
            mProgressView = new DownloadProgressView(mContext, this, downloadId);
            mProgressView.setBook(dest.getPath());
            if (getChildCount() > 0) {
                removeViewAt(0); // maybe a previous message about a successful delivery
            }
            addView(mProgressView);
            updateLayoutForChangedChildList();
        }
        mDownloadsInProgress.put(downloadId, new DownloadData(dest.getPath()));
        if (mDownloadsInProgress.size() == 1) {
            startMonitoringDownloads();
        }
    }

    private BroadcastReceiver mDownloadReceiver = new BroadcastReceiver() {

        // Remember, this is a method of the BroadcastReceiver stored in downloadReceiver, not
        // of the parent class. We override this to receive messages when the download manager
        // broadcasts them.
        @Override
        public void onReceive(Context context, Intent intent) {

            //check if the broadcast message is for one of out our Enqueued downloads
            long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            DownloadData data = mDownloadsInProgress.get(downloadId);
            if (data != null) { // otherwise, for some reason we're getting a notification about a download we didn't start!
                String action = intent.getAction();
                if (action.equals(DownloadManager.ACTION_DOWNLOAD_COMPLETE)) {
                    Cursor cursor = mDownloadManager.query(new DownloadManager.Query().setFilterById(downloadId));
                    if (cursor.moveToFirst()) {
                        LinearLayout downloads = findViewById(R.id.download_books);
                        String downloadDescription = CommonUtilities.getStringFromCursor(cursor, DownloadManager.COLUMN_DESCRIPTION);
                        mDownloadsInProgress.remove(downloadId);
                        handleDownloadComplete(data.destPath, downloadId, downloadDescription);
                        if (mDownloadsInProgress.size() == 0) {
                            cleanupDownloadDirectory();
                            //  We may have just finished multiple ones, but if so, we already put
                            // up a BookReadyView in multiple mode. Any subsequent single download
                            // will count as a single.
                            mRecentMultipleDownloads = false;
                        }
                    }

                    cursor.close();
                }

            }
        }
    };

    private void handleDownloadComplete(String downloadDestPath, long downloadId, String downloadDescription) {
        File source = new File(downloadDestPath);
        if (!source.exists()) {
            // just ignore any download we think we got that didn't result in a file.
            // Also, if there is more than one DownloadsView in different activities,
            // presumably one of them will win and move the file; the other will not find it.
            // Review: do we need thread locking to ensure this?

            // We don't want to hear about this again! Later, we might be reloading
            // the list while we're also re-doing the download. Then we might think the
            // incomplete download was complete. This may also help our downloads, which are no
            // longer where the DownloadManager put them, from showing up in the device's
            // downloads list.
            mDownloadManager.remove(downloadId);
            return;
        }

        String fileName = DownloadsView.getFileNameFromUri(downloadDestPath);
        File dest = new File(BookCollection.getLocalBooksDirectory(), fileName + ".bloompub");
        IOUtilities.copyFile(source.getPath(), dest.getPath());

        ReportDownloadAnalytics(downloadDescription, dest);

        source.delete();
        // We need to copy the file before we tell the download manager to remove it, or the DM
        // will not just forget about it, but also delete the file!
        // See above for why we want to remove it.
        mDownloadManager.remove(downloadId);
        MainActivity.noteNewBookInPrivateDirectory(dest.getPath());
        // It's possible that during the download, we moved away from the activity that contains
        // the instance that initiated the download, but it still gets the notification.
        // We want to see the BookReadyView in whatever instance is actually visible.
        for (DownloadsView v: sInstances) {
            v.updateUiForNewInstance(dest.getPath());
        }
    }

    private void ReportDownloadAnalytics(String downloadDescription, File dest) {
        String bookDbId = "";
        String lang = "";
        if (downloadDescription != null) {
            // In the downloadDescription we capture the URL of the book instance page that requested the download.
            // Something like https://alpha.bloomlibrary.org/app-hosted-v1/language:af/book/FONq0aa85h?lang=af
            int lastSlash = downloadDescription.lastIndexOf('/');
            int queryIndex = downloadDescription.lastIndexOf('?');
            if (queryIndex < 0) {
                queryIndex = downloadDescription.length();
            }
            if (lastSlash >= 0 && queryIndex > lastSlash) {
                bookDbId = downloadDescription.substring(lastSlash + 1, queryIndex);
            }
            int lastEquals = downloadDescription.lastIndexOf("lang=");
            if (lastEquals > 0) {
                lang = downloadDescription.substring(lastEquals + 5);
            }
        }

        Properties props = new Properties();
        BloomFileReader reader = new BloomFileReader(mContext, dest.getPath());
        props.putValue("bookInstanceId", reader.getStringMetaProperty("bookInstanceId", ""));
        props.putValue("title", reader.getStringMetaProperty("title", ""));
        props.putValue("originalTitle", reader.getStringMetaProperty("originalTitle", ""));
        props.putValue("brandingProjectName", reader.getStringMetaProperty("brandingProjectName", ""));
        props.putValue("publisher", reader.getStringMetaProperty("publisher", ""));
        props.putValue("originalPublisher", reader.getStringMetaProperty("originalPublisher", ""));
        props.putValue("bookDbId", bookDbId);
        props.putValue("lang", lang);
        props.putValue("downloadSourceUrl", downloadDescription);
        BloomReaderApplication.ReportAnalyticsWithLocationIfPossible(mContext, "Download Book", props);
    }

    // Called by the ViewBooks button in the BookReady view.
    // If we downloaded just one book, open it. Otherwise, return to the main Books view.
    public void viewBooks(String bookPath) {
        // If we're closing this in one instance, we want to get rid of it in all of them.
        // Currently this is simplified since we only ever have one.
        for (DownloadsView instance : sInstances) {
            if (instance.getChildCount() > 0) {
                instance.removeViewAt(0);
            }
        }
        if (bookPath != "") {
            MainActivity.launchReader(mContext, bookPath, null);
        } else if (mContext instanceof BloomLibraryActivity) {
            // View Books button kicks us back to the main activity. Would need enhancing if BloomLibraryActivity
            // could be launched from elsewhere, or if DownloadsView were embedded in more places.
            ((BloomLibraryActivity) mContext).finish();
        }
    }

    private void updateUiForNewInstance(String bookPath) {
        if (mDownloadsInProgress.size() > 0) {
            return; // still downloading, keep the progress bar.
        }
        if (getChildCount() > 0) {
            removeViewAt(0); // previous progress view
        }

        BookReadyView brb = new BookReadyView(mContext, this);
        brb.setBook(mRecentMultipleDownloads ? "" : bookPath);
        addView(brb, 0);
        updateLayoutForChangedChildList();
    }
}

