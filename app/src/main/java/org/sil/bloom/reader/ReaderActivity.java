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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// This is the main class that displays a Bloom book, using a WebView containing an instance of bloom-player.
public class ReaderActivity extends BaseActivity implements MessageReceiver {

    private static final String TAG = "ReaderActivity";// https://developer.android.com/reference/android/util/Log.html

    private static boolean isNavBarShowing = false; // Hide nav bar unless/until the player tells us to show it

    private JSONObject mBookProgressReport; // to send when activity finishes, if not overwritten first
    WebView mBrowser;
    WebAppInterface mAppInterface;
    String mDistribution;

    private long mTimeStarted;

    public static boolean haveCurrentWebView(WebView browser) {
        String agent = browser.getSettings().getUserAgentString();

        // We're looking for something like
        // Mozilla/5.0 (Linux; Android 11; moto g(8) power Build/RPES31.Q4U-47-35-12; wv)...
        // If we don't find the wv at the end of the Mozilla section, we need a newer WebView.
        // Unfortunately, while I've found doc that says to look for this "wv" as characteristic
        // of Lollipop+ WebView, I haven't found instructions on exactly how to search for it.
        // Maybe a weaker search...simply for "wv"...is better? Perhaps the wv won't follow a semi-colon
        // and precede a parenthesis in some future version? Maybe it should be stronger...
        // try to check that it's  whole word in the Mozilla section? Not very easy to do, as
        // device names like g(8) complicate picking out the Mozilla section. Could there be an
        // old device whose name happens to contain "wv"? This seems like a reasonable compromise
        // subject to testing.
        // Also unknown: is it helpful, neutral, or harmful to search for "wv" in addition to
        // looking for the Chrome version below?
        if( agent.indexOf("; wv)") < 0)
            return false;
        // The wv test did not prove sufficient, so we also look for a minimum Chrome version. The difficulty
        // is to know what version we actually require. It probably isn't very important to get the
        // exact minimum. Android 5 (lollipop) is supposed to update WebView automatically. My test
        // device, for example, which has no SIM and was recently reset to factory, reports
        // Chrome 101, the current latest.
        // Here is my attempted research:
        // According to https://developer.chrome.com/docs/multidevice/user-agent/#webview_user_agent,
        // WebViews based on Chromium should include a string like Chrome/43.0.2357.65.
        // This apparently began with 4.4, before the 'wv'; the site above shows Chrome 30 in
        // Android 4.4. Based on this and earlier experiments, it seems Chrome 30 is not good enough.
        // According to https://www.androidpolice.com/2014/10/19/lollipop-feature-spotlight-webview-now-unbundled-android-free-auto-update-google-play/,
        // Android 4.4.3 had version 33 of Chromium, and the first developer preview of 5.0 had version 37.
        // I have done considerable google searching and cannot find any indication of the minimum
        // version of Chrome needed for Swiper, which IIRC is the component that forced us to require
        // Android 5. Based on the first article above, which reports version 43 with Android 5.5.1,
        // it seems likely that anything older than that ought to be updated.
        // Based on my own testing with emulators, I was able to find that a Pixel 3 running Android
        // 7.0 (api 24) reports Chrome 51 and works; a Pixel 4XL running Android 6 with API 23 reports
        // Chrome 44 and does not. I looked at CanIUse data for feature support between those two
        // versions and each adds something; it's not obvious that we can safely set a minimum
        // below 51 unless we can find a test device to confirm it is OK. Note that we're only
        // encouraging the user to update, they CAN just hit back and see if it works.
        // Given that any device with Android 5+ connected to the web will update to the
        // current version of Chrome, it's likely that most users have something much more recent
        // than 51, so I think it's reasonable to just use that until we have better data.
        Pattern p = Pattern.compile("Chrome/(\\d+)\\."); //"Chrome/(\\d*)\\."
        Matcher m = p.matcher(agent);
        if (!m.find())
            return false;
        int version = Integer.parseInt(m.group(1));
        if (version < 51)
            return false;
        return true;
    }

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

        if (!haveCurrentWebView(mBrowser)) {
            Intent intent = new Intent(this, NeedNewerWebViewActivity.class);
            startActivity(intent);
            // It's not obvious what we should do with THIS activity, which as things stand the
            // user can return to with the Back button from the warning activity.
            // We could instead, I think, replace this activity with the warning one, so that
            // 'back' returns the user to the main screen. (If we decide on that, we may need
            // to use a different 'context' to initialize the new activity.)
            // Or we could try to quit BR altogether, since it's not much use without a usable
            // WebView...but when exactly should it quit? We want the user to be able to read
            // the message. And there are SOME things the user can do without being able to read,
            // such as sharing the books to other devices.
            // There may also be some possibility that we are wrong...that the webview the user
            // currently has IS usable, even though we don't expect it to be.
            // Putting all these considerations together, I tentatively decided to just go on
            // with the normal initialization of this activity, which means that if the user
            // chooses 'back' he will be in the screen with "Loading Bloom Player..." (unless
            // things unexpectedly work, of course).
        }

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