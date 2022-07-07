package org.sil.bloom.reader;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.webkit.DownloadListener;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import org.sil.bloom.reader.models.BookCollection;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// BloomLibraryActivity is used to interact with the online BloomLibrary. It uses special URLs
// with a leading app-hosted-v1 element which result in a layout optimized for downloading books
// to a device. Most of the content is a WebView hosting BloomLibrary.org. A small view at the
// bottom can show progress and results of downloads.
public class BloomLibraryActivity extends BaseActivity implements MessageReceiver {

    WebView mBrowser;
    WebAppInterface mAppInterface;
    DownloadsView mDownloads;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Allows remote debugging of the WebView content using Chrome over a USB cable.
        WebView.setWebContentsDebuggingEnabled(true);

        setContentView(R.layout.activity_bloom_library);
        mBrowser = this.findViewById(R.id.browser);

        //mBrowser.clearCache(false); // BL-7567 fixes "cross-pollination" of images
        mAppInterface = new WebAppInterface(this);
        // See the class comment on WebAppInterface for how this allows Javascript in
        // the WebView to make callbacks to our receiveMessage method.
        mBrowser.addJavascriptInterface(mAppInterface, "ParentProxy");
        final WebSettings webSettings = mBrowser.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);

        mDownloads = (DownloadsView) BloomLibraryActivity.this
                .findViewById(R.id.download_books);

        // Inform our download-handling component when the browser asks to download something.
        mBrowser.setDownloadListener(new DownloadListener() {
            public void onDownloadStart(String url, String userAgent,
                                        String contentDisposition, String mimetype,
                                        long contentLength) {

                mDownloads.onDownloadStart(url, userAgent, contentDisposition, mimetype, contentLength);
            }
        });

        try {
            mBrowser.setWebViewClient(new BloomLibraryWebViewClient(this));
            mBrowser.setWebChromeClient(new BloomLibraryWebChromeClient(this));

            // I'm not sure if this really a helpful setting or if we are actually just working around a bug in Android Webview...
            // Randomly, some devices started having display issues with videos. They would be very jerky or skip.
            // It seemed to be related to a recent version of Android colorWebview as uninstalling updates seemed to fix it
            // and reinstalling seemed to break it. But we could never prove it definitively.
            // But then adding this line seems to make the problem go away. See https://issues.bloomlibrary.org/youtrack/issue/BL-9727.
            // (This was copied from the init of the WebView for BloomPlayer. Probably not needed here, but harmless AFAIK.)
            mBrowser.setLayerType(View.LAYER_TYPE_HARDWARE, null);

            // We start the browser showing this specialized page in BloomLibrary.
            mBrowser.loadUrl("https://alpha.bloomlibrary.org/app-hosted-v1/langs");
        } catch (Exception e) {
            Log.e("Load", e.getMessage());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Set the bottom panel to indicate any downloads that are still in progress.
        mDownloads.updateUItoCurrentState();
    }

    // At one point this seemed to be necessary to make the back button or gesture useful within
    // the web page rather than always immediately ending the BL activity. Later experiments made
    // me more doubtful...something else may have been preventing the browser from going back.
    // But it's the recommended way to do this so I'm leaving it in.
    @Override
    public void onBackPressed() {
        if (mBrowser.canGoBack()) {
            mBrowser.goBack();
        } else {
            super.onBackPressed();
        }
    }

    // Not actually needed except it's required to be a subclass of BaseActivity.
    @Override
    protected void onNewOrUpdatedBook(String fullPath) {

    }

    // This function can receive messages sent by Javascript in BloomLibrary, in a way explained
    // in WebAppInterace.
    @Override
    public void receiveMessage(String message) {
        if (message.equals("go_home")) {
            // Unexpectedly, this takes us back to the home page in the webview.
            //super.onBackPressed();
            // This terminates the whole activity and takes us back to the main view.
            // We would need to do something more if this activity could be launched from somewhere
            // other than the home screen, but that isn't currently the case.
            finish();
        }
    }
}
