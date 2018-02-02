package org.sil.bloom.reader;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import org.apache.commons.io.FileUtils;
import org.sil.bloom.reader.models.BookOrShelf;

import java.io.File;
import java.io.IOException;

/**
 * Created by rick on 10/2/17.
 * Much of it borrowed from the SharingManager for Scripture/ReadingAppBuilder
 */

public class SharingManager {

    private Context mContext;

    public SharingManager(Context context) {
        mContext = context;
    }

    public void shareBook(BookOrShelf book){
        File bookFile = new File(book.path);
        String dialogTitle = String.format(mContext.getString(R.string.shareBook), book.name);
        // This is the recommended way to get a URI to a file in BR's private storage and make it
        // accessible to the sending code for a limited time.
        //Uri uri = FileProvider.getUriForFile(mContext, "org.sil.bloom.reader.fileprovider", bookFile);
        // But it does not work reliably; we've had some success (e.g., Bluetooth) but
        // ShareIt gives a mysterious message saying it doesn't know how to transfer this kind
        // of data, and Super Beam says "sorry, the file(s) you are trying to share are missing."
        // Since Bloom is keeping its books in a public folder in a common part of the phone's
        // storage, we don't need the special temporary-permission URI, and the one we get from the
        // line below seems to work much more reliably.
        Uri uri = Uri.fromFile(bookFile);
        shareFile(uri, "application/zip", dialogTitle);
    }

    public void shareApkFile() {
        try {
            File apkFile = copyApkToExternalStorageForSharing();
            // vnd.android.package-archive is the correct type but
            // bluetooth is not listed as an option for this on
            // older versions of android
            String type = "*/*";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
                type = "application/vnd.android.package-archive";

            shareFile(Uri.fromFile(apkFile), type, mContext.getString(R.string.share_app_via));
        }
        catch(IOException e){
            Log.e("BlReader/SharingManager", e.toString());
            Toast failToast = Toast.makeText(mContext, mContext.getString(R.string.failed_to_share_apk), Toast.LENGTH_LONG);
            failToast.show();
        }
    }

    public void shareLinkToAppOnGooglePlay() {
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");

            String appName = mContext.getString(R.string.app_name);

            intent.putExtra(Intent.EXTRA_SUBJECT, appName);

            String sAux = "\n" + mContext.getString(R.string.recommend_app, appName) + "\n\n" +
                    "https://play.google.com/store/search?q=%2B%22sil%20international%22%20%2B%22bloom%20reader%22&amp;c=apps" + " \n\n";
            intent.putExtra(Intent.EXTRA_TEXT, sAux);

            Intent chooser = Intent.createChooser(intent, mContext.getString(R.string.share_link_via));
            mContext.startActivity(chooser);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void shareBooks() {
        try {
            IOUtilities.makeBloomBundle(sharedBloomBundlePath());
            shareFile(Uri.fromFile(new File(sharedBloomBundlePath())), "application/zip", mContext.getString(R.string.share_books_via));
        }
        catch (Exception e) {
            Log.e("BlReader/SharingManager", e.toString());
            Toast failToast = Toast.makeText(mContext, mContext.getString(R.string.failed_to_share_books), Toast.LENGTH_LONG);
            failToast.show();
        }
    }

    // We have to stage the apk to share in public storage, and bloom bundles have to be
    // created somewhere.
    // This gets called now and then to delete the files there if they are more than a day old.
    public static void fileCleanup(){
        long yesterday = System.currentTimeMillis() - (1000 * 60 * 60 * 24);

        for (String filePath : new String[] {sharedApkPath(), sharedBloomBundlePath()}) {
            File file = new File(filePath);

            if (file.exists() && file.lastModified() < yesterday)
                file.delete();
        }
    }

    private void shareFile(Uri uri, String fileType, String dialogTitle){
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
        shareIntent.setType(fileType);

        mContext.startActivity(Intent.createChooser(shareIntent, dialogTitle));
    }

    private File copyApkToExternalStorageForSharing() throws IOException{
        String apkPath = mContext.getApplicationInfo().publicSourceDir;
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
