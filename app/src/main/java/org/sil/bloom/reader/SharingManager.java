package org.sil.bloom.reader;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import androidx.core.app.ShareCompat;
import androidx.core.content.FileProvider;
import android.util.Log;
import android.widget.Toast;

import org.apache.commons.io.FileUtils;
import org.sil.bloom.reader.models.BookOrShelf;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Created by rick on 10/2/17.
 * Much of it borrowed from the SharingManager for Scripture/ReadingAppBuilder
 */

public class SharingManager {

    private Activity mActivity;

    public SharingManager(Activity activity) {
        mActivity = activity;
    }

    public void shareBook(Context context, BookOrShelf book){
        File bookFile = book.inShareableDirectory() ? new File(book.path) : stageBook(context, book);
        if (bookFile == null) return;
        String dialogTitle = String.format(mActivity.getString(R.string.shareBook), book.name);
        shareFile(bookFile, "application/zip", dialogTitle);
    }

    private File stageBook(Context context, BookOrShelf book) {
        String bookFileName = new File(book.path).getName();
        String outPath = context.getCacheDir().getPath() + File.separator + bookFileName;
        if (IOUtilities.copyFile(book.path, outPath)) {
            return new File(outPath);
        }
        return null;
    }

    public void shareApkFile() {
        try {
            File apkFile = copyApkToExternalStorageForSharing();
            // vnd.android.package-archive is the correct type but
            // bluetooth is not listed as an option for this on
            // older versions of android
            String type = "*/*";

            // Note, as of master branch, Aug 2019, this does nothing because
            // our minSdkVersion is 21 anyway. But we vaguely hope to move it back to 19 one day...
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
                type = "application/vnd.android.package-archive";

            shareFile(apkFile, type, mActivity.getString(R.string.share_app_via));
        }
        catch(IOException e){
            Log.e("BlReader/SharingManager", e.toString());
            Toast failToast = Toast.makeText(mActivity, mActivity.getString(R.string.failed_to_share_apk), Toast.LENGTH_LONG);
            failToast.show();
        }
    }

    public void shareLinkToAppOnGooglePlay() {
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");

            String appName = mActivity.getString(R.string.app_name);

            intent.putExtra(Intent.EXTRA_SUBJECT, appName);

            String sAux = "\n" + mActivity.getString(R.string.recommend_app, appName) + "\n\n" +
                    "https://play.google.com/store/search?q=%2B%22sil%20international%22%20%2B%22bloom%20reader%22&amp;c=apps" + " \n\n";
            intent.putExtra(Intent.EXTRA_TEXT, sAux);

            Intent chooser = Intent.createChooser(intent, mActivity.getString(R.string.share_link_via));
            mActivity.startActivity(chooser);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public BundleTask shareShelf(List<BookOrShelf> booksAndShelves, final BundleTask.BundleTaskDoneListener taskDoneListener){
        File[] files = new File[booksAndShelves.size()];
        for (int i=0; i<booksAndShelves.size(); ++i)
            files[i] = new File(booksAndShelves.get(i).path);
        return bundleAndShare(files, taskDoneListener);
    }

    public BundleTask shareAllBooksAndShelves(final BundleTask.BundleTaskDoneListener taskDoneListener) {
        return bundleAndShare(null, taskDoneListener);
    }

    private BundleTask bundleAndShare(File[] files, final BundleTask.BundleTaskDoneListener taskDoneListener) {
        BundleTask bundleTask = new BundleTask(sharedBloomBundlePath(), new BundleTask.BundleTaskDoneListener() {
            @Override
            public void onBundleTaskDone(File bundleFile) {
                taskDoneListener.onBundleTaskDone(bundleFile); // Callback to dialog with spinner so it can close
                shareBloomBundle(bundleFile);
            }
        });
        bundleTask.execute(files);
        return bundleTask;
    }

    private void shareBloomBundle(File bundleFile) {
        if (bundleFile == null)
            Toast.makeText(mActivity, mActivity.getString(R.string.failed_to_share_books), Toast.LENGTH_LONG).show();
        else
            shareFile(bundleFile, "application/zip", mActivity.getString(R.string.share_books_via));
    }

    // We have to stage the apk to share in public storage, and bloom bundles have to be
    // created somewhere.
    // This gets called now and then to delete the files there if they are more than a day old.
    public static void fileCleanup(Context context){
        long yesterday = System.currentTimeMillis() - (1000 * 60 * 60 * 24);

        for (String filePath : new String[] {sharedApkPath(), sharedBloomBundlePath()}) {
            File file = new File(filePath);

            if (file.exists() && file.lastModified() < yesterday)
                file.delete();
        }

        // Clean up book files staged in cache dir
        String[] cacheItems = context.getCacheDir().list();
        if (cacheItems == null) { return; }
        for (int i=0; i<cacheItems.length; ++i) {
            if (IOUtilities.isBloomPubFile(cacheItems[i])) {
                File file = new File(context.getCacheDir() + File.separator + cacheItems[i]);
                if (file.lastModified() < yesterday)
                    file.delete();
            }
        }
    }

    private void shareFile(File file, String fileType, String dialogTitle){
        Uri uri = FileProvider.getUriForFile(mActivity, BuildConfig.APPLICATION_ID + ".fileprovider", file);
        Intent shareIntent = ShareCompat.IntentBuilder.from(mActivity)
                .setStream(uri)
                .getIntent()
                .setDataAndType(uri, fileType)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        mActivity.startActivity(Intent.createChooser(shareIntent, dialogTitle));
    }

    private File copyApkToExternalStorageForSharing() throws IOException{
        String apkPath = mActivity.getApplicationInfo().publicSourceDir;
        File srcApk = new File(apkPath);
        File destApk = new File(sharedApkPath());
        if (destApk.exists() && destApk.isFile()) {
            destApk.delete();
            destApk = new File(sharedApkPath());
        }
        FileUtils.copyFile(srcApk, destApk);
        return destApk;
    }

    private static String sharedFilePath(String fileName) {
        String sharedApkPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        sharedApkPath += File.separator + fileName;
        return sharedApkPath;
    }

    private static String sharedApkPath() {
        return sharedFilePath("BloomReader.apk");
    }

    private static String sharedBloomBundlePath() {
        String deviceName = BloomReaderApplication.getOurDeviceName();
        deviceName = (deviceName != null && !deviceName.isEmpty()) ? deviceName : "my";

        return sharedFilePath(deviceName + IOUtilities.BLOOM_BUNDLE_FILE_EXTENSION);
    }
}
