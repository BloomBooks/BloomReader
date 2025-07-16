package org.sil.bloom.reader;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.DownloadListener;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;

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

        // Adds back and home buttons
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null)
                actionBar.setDisplayHomeAsUpEnabled(true);

        // Allows remote debugging of the WebView content using Chrome over a USB cable.
        WebView.setWebContentsDebuggingEnabled(true);

        setContentView(R.layout.activity_bloom_library);
        mBrowser = this.findViewById(R.id.browser);

        // BL-7567 fixes "cross-pollination" of images.
        // This is only marginally necessary here, but the user CAN actually read a book directly in
        // the browser.
        mBrowser.clearCache(false);

        // At one point, this was being used to keep BloomLibrary from sending us the parse app ID header.
        // We had to do that when we were overriding BloomLibraryWebViewClient's shouldInterceptRequest.
        // But see notes there why we are not overriding it currently.
        //mBrowser.getSettings().setUserAgentString(mBrowser.getSettings().getUserAgentString() + ";sil-bloom");

        mAppInterface = new WebAppInterface(this);
        // See the class comment on WebAppInterface for how this allows Javascript in
        // the WebView to make callbacks to our receiveMessage method.
        mBrowser.addJavascriptInterface(mAppInterface, "ParentProxy");
        final WebSettings webSettings = mBrowser.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);

        setupCustomBackPressHandling();

        mDownloads = (DownloadsView) BloomLibraryActivity.this
                .findViewById(R.id.download_books);

        // Inform our download-handling component when the browser asks to download something.
        mBrowser.setDownloadListener(new DownloadListener() {
            public void onDownloadStart(String url, String userAgent,
                                        String contentDisposition, String mimetype,
                                        long contentLength) {

                mDownloads.onDownloadStart(url, userAgent, contentDisposition, mimetype, contentLength, mBrowser.getUrl());
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

            String host = "https://bloomlibrary.org";
            if (BuildConfig.DEBUG || BuildConfig.FLAVOR.equals("alpha"))
                host = "https://alpha.bloomlibrary.org";
            // Note: if you configure a host that needs to use the dev parse server,
            // you will need to add a case to the code in BloomLibraryWebViewClient.shouldInterceptRequest
            // which sets X-Parse-Application-Id, and have it set the appropriate key for dev.

            // Use this to test running local bloomlibrary in a BR emulator.
            // 10.0.2.2 is the host machine's localhost.
            //host = "http://10.0.2.2:3000";
            // Use something like this to test a real device with a local build.
            // Get the right IP address for your serving computer using ipconfig or similar;
            // the one that yarn start-performance reports ("On Your Network:") is WRONG.
            // Also enable this URL in res/xml/network_security_config.xml
            //host = "http://192.168.0.246:3000";

            // We start the browser showing this specialized page in BloomLibrary.
            mBrowser.loadUrl(host + "/app-hosted-v1/langs");

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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.bloom_library_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.bloom_library_home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    // Confusingly, this is the left arrow button on the action bar.
    public boolean onSupportNavigateUp() {
        handleBack();
        return true; // Handled
    }

    private void setupCustomBackPressHandling() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBack();
            }
        });
    }

    private void handleBack() {
        if (mBrowser != null && mBrowser.canGoBack()) {
            mBrowser.goBack();
        } else {
            finish();
        }
    }

    // Not actually needed except it's required to be a subclass of BaseActivity.
    @Override
    protected void onNewOrUpdatedBook(String fullPath) {

    }

    // This function can receive messages sent by Javascript in BloomLibrary, in a way explained
    // in WebAppInterface.
    @Override
    public void receiveMessage(String message) {
        if (message.equals("go_home")) {
            // This terminates the whole activity and takes us back to the main view.
            // We would need to do something more if this activity could be launched from somewhere
            // other than the home screen, but that isn't currently the case.
            finish();
        } else if (message.equals("close_keyboard")) {
            InputMethodManager imm = (InputMethodManager) this.getSystemService(Activity.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(mBrowser.getWindowToken(), 0);
        }
    }
}
