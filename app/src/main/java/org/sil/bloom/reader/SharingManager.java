package org.sil.bloom.reader;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.widget.Toast;

import org.apache.commons.io.FileUtils;
import org.sil.bloom.reader.models.Book;

import java.io.File;
import java.io.IOException;

/**
 * Created by rick on 10/2/17.
 */

public class SharingManager {
    private static final String LOG_TAG_SHARING = "BloomReader-Sharing";

    private Context mContext;
//    private AppDefinition mApp;

    public SharingManager(Context context) {
        mContext = context;
//        mApp = app;
    }

    public void shareBook(Book book){
        File bookFile = new File(book.path);
        String dialogTitle = mContext.getString(R.string.share) + " " + book.name;
        Uri uri = FileProvider.getUriForFile(mContext, "org.sil.bloom.reader.fileprovider", bookFile);
//        shareFile(bookFile, "*/*", dialogTitle);
        shareFile(uri, "*/*", dialogTitle);
    }

    public void shareApkFile() {
        try {
//        File apk = new File(mContext.getApplicationInfo().publicSourceDir);
            String uri = mContext.getApplicationInfo().publicSourceDir;
//            // Copy APK and rename it to the original APK filename
//            // (The Android system often renames it to base.apk)

            File srcApk = new File(uri);
            File destApk = new File(sharedApkPath());

            if (destApk.exists() && destApk.isFile()) {
                // Delete destination file if it exists already
                destApk.delete();
                destApk = new File(sharedApkPath());
            }
            FileUtils.copyFile(srcApk, destApk);

//            if (FileUtils.fileExists(destFilename)) {

            // vnd.android.package-archive is the correct type but
            // bluetooth is not listed as an option for this on
            // older versions of android
            String type = "*/*";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
                type = "application/vnd.android.package-archive";

//            shareFile(destApk, type, mContext.getString(R.string.share_app_via));
            shareFile(Uri.fromFile(destApk), type, mContext.getString(R.string.share_app_via));
        }

        catch(IOException e){
            Toast failToast = Toast.makeText(mContext, "Failed to share apk file.", Toast.LENGTH_LONG);
            failToast.show();
        }
        //======================================================================

//        String appUri = mContext.getApplicationInfo().publicSourceDir;
//
////        if (StringUtils.isNotBlank(appUri)) {
//            // Copy APK and rename it to the original APK filename
//            // (The Android system often renames it to base.apk)
//            String destFolder = Environment.getExternalStorageDirectory().getAbsolutePath();
//            String destFilename = destFolder + "/" + "BloomReader.apk";
//
//            Log.i(LOG_TAG_SHARING, "From: " + appUri);
//            Log.i(LOG_TAG_SHARING, "To:   " + destFilename);
//
//            File srcApk  = new File(appUri);
//            File destApk = new File(destFilename);
//
//            if (destApk.exists() && destApk.isFile()) {
//                // Delete destination file if it exists already
//                destApk.delete();
//                destApk = new File(destFilename);
//            }
//
//            FileUtils.copyFile(srcApk, destApk);
//
//            if (FileUtils.fileExists(destFilename)) {
//                // Share...
//                Intent intent = new Intent(android.content.Intent.ACTION_SEND);
//                intent.putExtra(Intent.EXTRA_SUBJECT, mApp.getAppName());
//
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
//                    // This is the correct MIME type for APK files
//                    // Seems to work ok for Android 4.4 and above
//                    intent.setType("application/vnd.android.package-archive");
//                }
//                else {
//                    // If MIME type is set correctly for Gingerbread phones,
//                    // Bluetooth is not listed as an option, so use */* instead
//                    intent.setType("*/*");
//                }
//
//                intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(destApk));
//
//                Intent chooser = Intent.createChooser(intent, getString(CommonStringId.SHARE_APK_FILE_VIA)); // "Share App via"
//                mContext.startActivity(chooser);
//            }
//            else {
//                Log.e(LOG_TAG_SHARING, "File not found: " + destFilename);
//            }
//        }
    }

//    private void shareFile(File file, String fileType, String dialogTitle){
    private void shareFile(Uri uri, String fileType, String dialogTitle){
        //Uri uri = FileProvider.getUriForFile(mContext, "org.sil.bloom.reader.fileprovider", file);

        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
        shareIntent.setType(fileType);

        mContext.startActivity(Intent.createChooser(shareIntent, dialogTitle));
    }

    public static void fileCleanup(){
        File sharedApkFile = new File(sharedApkPath());
        long yesterday = System.currentTimeMillis() - (1000 * 60 * 60 * 24);
        if(sharedApkFile.exists() && sharedApkFile.lastModified() < yesterday)
            sharedApkFile.delete();
    }

    private static String sharedApkPath(){
        String sharedApkPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        sharedApkPath += File.separator + "BloomReader.apk";
        return sharedApkPath;
    }

    /**
     * Shares link to app on Google Play
     */
//    public void shareLinkToAppOnGooglePlay() {
//        try {
//            Intent intent = new Intent(Intent.ACTION_SEND);
//            intent.setType("text/plain");
//
//            intent.putExtra(Intent.EXTRA_SUBJECT, mApp.getAppName());
//
//            // "I can recommend this app:"
//            String sAux = "\n" + getString(CommonStringId.SHARE_APP_LINK_RECOMMEND) + "\n\n" +
//                    "https://play.google.com/store/apps/details?id=" + mApp.getPackageName() + " \n\n";
//            intent.putExtra(Intent.EXTRA_TEXT, sAux);
//
//            Intent chooser = Intent.createChooser(intent, getString(CommonStringId.SHARE_APP_LINK_VIA)); // "Share Link via"
//            mContext.startActivity(chooser);
//        }
//        catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

//    private String getString(final String id) {
//        return StringManager.INSTANCE.getString(id);
//    }

}
