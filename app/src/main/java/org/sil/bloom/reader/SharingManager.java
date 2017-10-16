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
 * Much of it borrowed from the SharingManager for Scripture/ReadingAppBuilder
 */

public class SharingManager {

    private Context mContext;

    public SharingManager(Context context) {
        mContext = context;
    }

    public void shareBook(Book book){
        File bookFile = new File(book.path);
        String dialogTitle = String.format(mContext.getString(R.string.shareBook), book.name);
        Uri uri = FileProvider.getUriForFile(mContext, "org.sil.bloom.reader.fileprovider", bookFile);
        shareFile(uri, "application/vm.sil.bloomd", dialogTitle);
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

            intent.putExtra(Intent.EXTRA_SUBJECT, "Bloom Reader");

            String sAux = "\n" + mContext.getString(R.string.recommend_app) + "\n\n" +
                    "https://play.google.com/store/search?q=%2B%22sil%20international%22%20%2B%22bloom%20reader%22&amp;c=apps" + " \n\n";
            intent.putExtra(Intent.EXTRA_TEXT, sAux);

            Intent chooser = Intent.createChooser(intent, mContext.getString(R.string.share_link_via));
            mContext.startActivity(chooser);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    // We have to stage the apk file in public storage for sharing.
    // This gets called now and then to delete the file there if it's more than a day old.
    public static void fileCleanup(){
        File sharedApkFile = new File(sharedApkPath());
        long yesterday = System.currentTimeMillis() - (1000 * 60 * 60 * 24);
        if(sharedApkFile.exists() && sharedApkFile.lastModified() < yesterday)
            sharedApkFile.delete();
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

    private static String sharedApkPath(){
        String sharedApkPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        sharedApkPath += File.separator + "BloomReader.apk";
        return sharedApkPath;
    }
}
