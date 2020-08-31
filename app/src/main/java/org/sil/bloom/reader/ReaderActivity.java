package org.sil.bloom.reader;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;

import com.segment.analytics.Properties;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.Iterator;

// This is the main class that displays a Bloom book, using a WebView containing an instance of bloom-player.
public class ReaderActivity extends BaseActivity {

    private static final String TAG = "ReaderActivity";// https://developer.android.com/reference/android/util/Log.html

    private static boolean isNavBarShowing = false; // Hide nav bar unless/until the player tells us to show it

    private JSONObject mBookProgressReport; // to send when activity finishes, if not overwritten first
    WebView mBrowser;
    WebAppInterface mAppInterface;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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

            // enhance: possibly this should happen asynchronously, in a Loader like the
            // original.
            // enhance: possibly show and hide the wait view.
            final BloomFileReader reader = new BloomFileReader(getApplicationContext(), path);
            final File bookHtmlFile = reader.getHtmlFile();
            String bookFolder = new File(bookHtmlFile.getCanonicalPath()).getParent();
            mBrowser.setWebViewClient(new ReaderWebViewClient("file://" + bookFolder));
            mBrowser.setWebChromeClient(new ReaderWebChromeClient(this));

            // The url determines the content of the WebView, which is the bloomplayer.htm
            // file
            // shipped with this program, combined with a param pointing to the book we just
            // decompressed.
            final String url = "file:///android_asset/bloom-player/bloomplayer.htm?url=file:///"
                    + bookHtmlFile.getAbsolutePath()+"&showBackButton=true&allowToggleAppBar=true&initiallyShowAppBar=false"
                    + "&centerVertically=true&hideFullScreenButton=true&independent=false&host=bloomreader";

            mBrowser.loadUrl(url);
        } catch (Exception e) {
            Log.e("Load", e.getMessage());
        }
    }

    @Override
    protected void onPause() {
        postMessageToPlayer("{\"messageType\":\"control\", \"pause\":true}");

        if (isFinishing()) {
            MakeFinalReport();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();

        hideSystemUI(isNavBarShowing);

        postMessageToPlayer("{\"messageType\":\"control\", \"resume\":true}");
    }

    @Override
    protected void onDestroy() {
        mBrowser.destroy();
        mBrowser = null;
        super.onDestroy();
    }

    // When a session is finishing, if we've received data to send as the final
    // analytics report
    // for this book (typically PagesRead), send it now.
    private void MakeFinalReport() {
        if (mBookProgressReport != null)
            sendAnalytics(mBookProgressReport);
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
                    sendAnalytics(data);
                    break;
                case "updateBookProgressReport":
                    mBookProgressReport = data;
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
        LocationManager lm = null;
        if(MainActivity.haveLocationPermission(this)) {
            lm = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        }
        // This should be roughly equivalent to
        //Analytics.with(BloomReaderApplication.getBloomApplicationContext()).track(event, p);
        // However reports sent like that have sometimes gotten lost when sent as the activity
        // is closing down. We hope that sending them from a distinct thread may help.
        new ReportAnalyticsTask().execute(new ReportAnalyticsTaskParams(event, p, lm ));
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
    protected void onNewOrUpdatedBook(String fullPath) {
        ((BloomReaderApplication) this.getApplication()).setBookToHighlight(fullPath);
        Intent intent = new Intent(this, MainActivity.class);
        // Clears the history so now the back button doesn't take from the main activity
        // back to here.
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}