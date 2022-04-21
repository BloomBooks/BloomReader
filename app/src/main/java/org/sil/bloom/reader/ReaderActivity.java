package org.sil.bloom.reader;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.Date;

// This is the main class that displays a Bloom book, using a WebView containing an instance of bloom-player.
public class ReaderActivity extends BaseActivity {

    private static final String TAG = "ReaderActivity";// https://developer.android.com/reference/android/util/Log.html

    private static boolean isNavBarShowing = false; // Hide nav bar unless/until the player tells us to show it

    private JSONObject mBookProgressReport; // to send when activity finishes, if not overwritten first
    WebView mBrowser;
    WebAppInterface mAppInterface;
    String mDistribution;

    private long mTimeStarted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mTimeStarted = new Date().getTime();

        // Allows remote debugging of the WebView content using Chrome over a USB cable.
        // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        WebView.setWebContentsDebuggingEnabled(true);
        // }

        setContentView(R.layout.activity_reader);
        mBrowser = this.findViewById(R.id.bloom_player);
        mBrowser.clearCache(false); // BL-7567 fixes "cross-pollination" of images
        mAppInterface = new WebAppInterface(this);
        // See the class comment on WebAppInterface for how this allows Javascript in
        // the
        // WebView to make callbacks to our receiveMessage method.
        mBrowser.addJavascriptInterface(mAppInterface, "ParentProxy");
        final WebSettings webSettings = mBrowser.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);

        // Prevent user settings from messing up the display of books. See BL-8101.
        webSettings.setTextZoom(100);

        // not quite clear on the difference between these or whether all are needed.
        // The goal is to allow the bloom-player javascript to make http calls to
        // retrieve
        // the various files that make up the book.
        // Todo: need to constrain this somehow to BloomReader's own files, or
        // preferably just this book's files.
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowFileAccessFromFileURLs(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);
        // I don't think we need this yet but some interactive pages may want it.
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);

        // In case we don't want the default placeholder WebView shows before a video starts playing,
        // we can reinstate this.
        // Instead, show this bitmap...a single transparent pixel!
//        final Bitmap defaultPoster = Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_4444);
//        Canvas canvas = new Canvas(defaultPoster);
//        canvas.drawARGB(0,0,0,0);
//
//        mBrowser.setWebChromeClient(new WebChromeClient(){
//            @Nullable
//            @Override
//            public Bitmap getDefaultVideoPoster() {
//                return defaultPoster;
//            }
//        });

        try {
            final String path = getIntent().getStringExtra("bookPath");
            final String uriString = getIntent().getStringExtra("bookUri");
            final Uri uri = uriString == null ? null : Uri.parse(uriString);

            // enhance: possibly this should happen asynchronously, in a Loader like the
            // original.
            // enhance: possibly show and hide the wait view.
            final BloomFileReader fileReader = new BloomFileReader(getApplicationContext(), path, uri);
            final File bookHtmlFile = fileReader.getHtmlFile();
            String bookFolder = new File(bookHtmlFile.getCanonicalPath()).getParent();
            mBrowser.setWebViewClient(new ReaderWebViewClient(bookFolder, fileReader));
            mBrowser.setWebChromeClient(new ReaderWebChromeClient(this));

            // The url determines the content of the WebView, which is the bloomplayer.htm
            // file
            // shipped with this program, combined with a param pointing to the book we just
            // decompressed.
            final String url = "file:///android_asset/bloom-player/bloomplayer.htm?url=file:///"
                    + bookHtmlFile.getAbsolutePath() + "&showBackButton=true&allowToggleAppBar=true&initiallyShowAppBar=false"
                    + "&centerVertically=true&hideFullScreenButton=true&independent=false&host=bloomreader";

            // I'm not sure if this really a helpful setting or if we are actually just working around a bug in Android Webview...
            // Randomly, some devices started having display issues with videos. They would be very jerky or skip.
            // It seemed to be related to a recent version of Android Webview as uninstalling updates seemed to fix it
            // and reinstalling seemed to break it. But we could never prove it definitively.
            // But then adding this line seems to make the problem go away. See https://issues.bloomlibrary.org/youtrack/issue/BL-9727.
            mBrowser.setLayerType(View.LAYER_TYPE_HARDWARE, null);

            mBrowser.loadUrl(url);
        } catch (Exception e) {
            Log.e("Load", e.getMessage());
        }
    }

    @Override
    protected void onPause() {
        Settings settings = Settings.load(this);
        settings.setBookReadDuration(new Date().getTime() - mTimeStarted);
        settings.setBookBeingRead(getIntent().getStringExtra("bookPath"));
        settings.save(this);

        updateReadDuration();

        postMessageToPlayer("{\"messageType\":\"control\", \"pause\":true}");

        // If you want to perform an action when isFinishing() == true,
        // consider using onDestroy instead. There is at least one case
        // where onDestroy happens even though onPause is never called
        // with isFinishing() == true, namely when an intent launches
        // a book directly and the ReaderActivity was already the top
        // activity (but was paused).

        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();

        Settings settings = Settings.load(this);
        String bookBeingRead = settings.getBookBeingRead();
        if (bookBeingRead != null && bookBeingRead.equals(getIntent().getStringExtra("bookPath"))) {
            mTimeStarted = new Date().getTime() - settings.getBookReadDuration();
        }

        hideSystemUI(isNavBarShowing);

        postMessageToPlayer("{\"messageType\":\"control\", \"resume\":true}");
    }

    @Override
    protected void onDestroy() {
        mBrowser.destroy();
        mBrowser = null;
        MakeFinalReport();
        super.onDestroy();
    }

    // When a session is finishing, if we've received data to send as the final
    // analytics report
    // for this book (typically PagesRead), send it now.
    // If changes are made here, check for needed changes in MainActivity.checkForPendingReadBookAnalyticsEvent
    private void MakeFinalReport() {
        if (mBookProgressReport != null) {
            addNonPlayerAnalyticsInfo(mBookProgressReport);
            sendAnalytics(mBookProgressReport);

            // We just sent this report; clear it to ensure we don't send
            // it again when the app starts.
            Settings settings = Settings.load(this);
            settings.setBookReadDuration(0);
            settings.setPendingProgressReport(null);
            settings.save(this);
        }
    }

    // Fill in the readDuration param in the book progress report
    // to reflect the current length of the time the user has been reading.
    // BloomPlayer tracks duration itself, but we can't use it in BloomReader
    // because the blur and focus events it uses do not happen in the Android WebView.
    private void updateReadDuration() {
        try {
            if (mBookProgressReport == null)
                return;
            JSONObject params = (JSONObject) mBookProgressReport.get("params");
            // Purposely make it an integer (whole seconds)
            params.put("readDuration", String.valueOf((new Date().getTime() - mTimeStarted) / 1000));
        } catch (JSONException je) {
            je.printStackTrace();
        }
    }

    private final int flagsForShowingNavBar =
            // We don't want the System to grab swipes from the edge
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    // Hide the status bar
                    | View.SYSTEM_UI_FLAG_FULLSCREEN;

    private final int flagsForHidingNavBar =
            flagsForShowingNavBar
                    // Hide the nav bar
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;

    private void hideSystemUI(final boolean showNavBar) {
        this.runOnUiThread(new Runnable() {
            public void run() {
                View decorView = getWindow().getDecorView();
                decorView.setSystemUiVisibility(showNavBar ? flagsForShowingNavBar : flagsForHidingNavBar);
                isNavBarShowing = showNavBar;
            }
        });
    }

    // Receive a message. Ideally we would like this to be simply a handler for when
    // Javascript in the webview sends a message with postMessage().
    // Haven't found a way to do that yet, so instead, we arrange for the
    // bloom-player
    // to call the receiveMessage method in the WebAppInterface, which calls this.
    public void receiveMessage(String message) {
        try {
            JSONObject data = new JSONObject(message);
            String messageType = data.getString("messageType");
            switch (messageType) {
                case "backButtonClicked":
                    finish();
                    break;
                case "sendAnalytics":
                    addNonPlayerAnalyticsInfo(data);
                    sendAnalytics(data);
                    break;
                case "updateBookProgressReport":
                    mBookProgressReport = data;
                    updateReadDuration();
                    addNonPlayerAnalyticsInfo(mBookProgressReport);
                    Settings settings = Settings.load(this);
                    settings.setPendingProgressReport(mBookProgressReport);
                    settings.save(this);
                    break;
                case "reportBookProperties":
                    setDeviceOrientation(data);
                    break;
                case "showNavBar":
                    hideSystemUI(true);
                    break;
                case "hideNavBar":
                    hideSystemUI(false);
                    break;
                case "logError":
                    Log.e("Error message received", data.getString("message"));
                    break;
                default:
                    Log.e("receiveMessage", "Unexpected message: " + messageType);
            }
        } catch (JSONException e) {
            Log.e("receiveMessage", e.getMessage());
        }
    }

    // Add any analytics info which should be in every event reported
    // but which doesn't come from BloomPlayer.
    private void addNonPlayerAnalyticsInfo(JSONObject data) {
        // currently nothing to do.
        //        JSONObject params;
        //        try {
        //            params = data.getJSONObject("params");
        //            // Add some more data to params
        //        } catch (JSONException e) {
        //            e.printStackTrace();
        //        }
    }

    // Given a JSONObject, obtained by parsing a JSON string of book properties sent
    // by BloomPlayer, set the device orientation appropriately. The data object is expected to
    // contain a "params" field, an object containing a "canRotate" boolean and a
    // "landscape" boolean.
    void setDeviceOrientation(JSONObject data) {
        JSONObject params;
        boolean canRotate;
        boolean isLandscape;
        try {
            params = data.getJSONObject("params");
            canRotate = params.getBoolean("canRotate");
            isLandscape = params.getBoolean("landscape");
        } catch (JSONException e) {
            Log.e("setDeviceOrientation", "reportBookProperties message missing params");
            return;
        }
        if (canRotate) return; // Let the user rotate the device and book to their hearts content.

        if (isLandscape) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        }
    }

    void postMessageToPlayer(final String json) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mBrowser.evaluateJavascript("window.BloomPlayer.receiveMessage(\"" + json.replace("\"", "\\\"") + "\")",
                        null);
            }
        });
    }

    @Override
    protected void onNewOrUpdatedBook(String pathOrUri) {
        ((BloomReaderApplication) this.getApplication()).setBookToHighlight(pathOrUri);
        Intent intent = new Intent(this, MainActivity.class);
        // Clears the history so now the back button doesn't take from the main activity
        // back to here.
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}