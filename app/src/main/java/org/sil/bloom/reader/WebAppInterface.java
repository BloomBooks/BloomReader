package org.sil.bloom.reader;

import android.media.MediaPlayer;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import java.io.IOException;

import static android.media.AudioManager.STREAM_MUSIC;

/**
 * This class exists to provide methods that Javascript in a WebView can make callbacks on.
 * Since one of those callbacks is a request to play the book's audio narration, it also
 * has a media player which is used for playing narration. Note that it is static; there is
 * only one narration media player, though each WebView has its own WebAppInterface.
 * Since the app can also interact with the narration, this class has methods to pause/resume
 * and stop the narration.
 * Then, to keep all the audio stuff together, it also has the player that handles background audio.
 * Finally, since this class receives a notification from the Javascript when the page is
 * ready to receive commands, it also handles sending the command that starts the narration
 * playing.
 * Currently this class is attached using addJavascriptInterface(one of these, "Android");
 * as a result, the JavaScript can call the @JavascriptInterface methods of this class
 * as if they were methods of a global object called Android, e.g.,
 * Android.playAudio("audio/someguid.mp3"). Or in typescript, since I haven't figured out
 * a way to declare that this global exists and has these methods, we defeat two levels of
 * type-checking with (<any>(<any>(window)).Android).playAudio("audio/someguid.mp3").
 * For all this to work, we need a Javascript file
 * app\src\main\assets\book support files\bloomPagePlayer.js which is one of the outputs of
 * the BloomPlayer project. On TeamCity this should be configured as a dependency.
 * When building locally you will currently need to build BloomPlayer first and copy the file over,
 * or download the asset from TC if you don't need to change anything in it.
 */

public class WebAppInterface {
    // The reader activity that created this class to serve a particular web view.
    private ReaderActivity mContext;
    // The directory in which the book lives; play requests are relative to this directory.
    private String mHtmlDirPath;
    // The web view for which this is the javascript interface.
    private WebView mWebView;
    // Whether narration audio is paused or playing.
    private static boolean mPaused;
    // The one (shared) media player used for narration.
    private static MediaPlayer mp = new MediaPlayer();
    // And the one used for background audio
    private static MediaPlayer mpBackground = new MediaPlayer();

    // The background audio for the current page
    private static String backgroundAudioPath;
    // Set true when our Web View has loaded its document. This depends on the JavaScript
    // appropriately calling domContentLoaded().
    private boolean mDocLoaded = false;
    // Set true when we want to start narration, but haven't yet received domContentLoaded().
    // When we do receive the notification, narration will start at once.
    boolean shouldStartNarrationWhenDocLoaded = false;
    // Similarly if we need a postponed call to handleBeforeDocumentLoaded.
    boolean shouldPrepareDocumentWhenLoaded = false;
    // The index of the page we belong to. As well as being useful in debugging, it can be compared
    // with the index of the currently visible page, so we ignore playback completed messages
    // if this page isn't current (and so it's not our audio that just completed).
    int mPosition;

    WebAppInterface(ReaderActivity c, String htmlDirPath, WebView webView, int position) {
        mContext = c;
        mHtmlDirPath = htmlDirPath;
        mWebView = webView;
        mPosition = position;
    }

    public void reset(){
        mDocLoaded = false;
        shouldPrepareDocumentWhenLoaded = false;
        shouldStartNarrationWhenDocLoaded = false;
    }

    // Mainly this is to prevent the Paused state from persisting from one book to another
    public static void resetAll(){
        mPaused = false;
        mp = new MediaPlayer();
        mpBackground = new MediaPlayer();
    }

    // This can be helpful in debugging. It's not currently used in production.
    @JavascriptInterface
    public void showToast(String toast) {
        Toast.makeText(mContext, toast, Toast.LENGTH_SHORT).show();
    }

    public void setPaused(boolean pause) {
        mPaused = pause;
        if (pause) {
            Log.d("JSEvent", "mp.pause && mpBackground.pause");
            mp.pause();
            mpBackground.pause();

            mContext.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.d("JSEvent", "pauseAnimation, page " + String.valueOf(mPosition));
                    mWebView.evaluateJavascript("Root.pauseAnimation()", null);
                }
            });
        } else {
            Log.d("JSEvent", "mp.start && mpBackground.start, page " + String.valueOf(mPosition));
            mp.start(); // Review: need to suppress if playback completed?
            if (backgroundAudioPath != null && backgroundAudioPath.length() > 0)
                mpBackground.start();
            mContext.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.d("JSEvent", "resumeAnimation, page " + String.valueOf(mPosition));
                    mWebView.evaluateJavascript("Root.resumeAnimation()", null);
                }
            });
        }
    }

    public static boolean isNarrationPaused() {
        return mPaused;
    }

    public static void stopNarration() {
        Log.d("JSEvent", "mp.stop");
        mp.stop();
    }

    // When our app no longer in foreground
    public static void stopAllAudio() {
        stopNarration();
        Log.d("JSEvent", "mpBackground.stop");
        mpBackground.stop();
    }

    public static void SetBackgroundAudio(String path, float volume) {
        if (path.equals(backgroundAudioPath))
            return;
        backgroundAudioPath = path;
        Log.d("JSEvent", "mpBackground stop && resest");
        mpBackground.stop();
        mpBackground.reset();
        if (backgroundAudioPath == null || backgroundAudioPath.length() == 0)
            return;
        mpBackground.setLooping(true);
        try {
            mpBackground.setDataSource(backgroundAudioPath);
            mpBackground.prepare();
            mpBackground.setVolume(volume, volume);
            if (!mPaused)
            {
                mpBackground.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Play an audio file from the webpage. The argument comes from the JavaScript function
    // and is a path relative to the location of the HTML file.
    @JavascriptInterface
    public void playAudio(String aud) {

        try {
            Log.d("JSEvent", "mp.stop && mp.reset && mp.setDataSource && mp.prepare, page " + String.valueOf(mPosition));
            mp.stop();
            mp.reset();
            mp.setDataSource(mHtmlDirPath + "/" + aud);
            mp.prepare();
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mediaPlayer) {
                    mContext.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (mContext.indexOfCurrentPage() != mPosition) {
                                // Only the currently active page should be notified of
                                // completion events.
                                return;
                            }
                            Log.d("JSEvent", "playbackCompleted, page " + String.valueOf(mPosition));
                            mWebView.evaluateJavascript("Root.playbackCompleted()", null);
                        }
                    });
                }
            });
            // There seems to be a race condition in which we pause somewhere around when one
            // sound completes, but the sound completion either just fired or fires after
            // the pause. The javascript then sends a request to start the next sound.
            // By not actually starting it, we are in the right state so that it WILL
            // start when the pause ends.
            if (!mPaused) {
                Log.d("JSEvent", "mp.start, page " + String.valueOf(mPosition));
                mp.start();
            }
        } catch (IllegalArgumentException e) {
            // All these shouldn't happen, but JavaScript demands we catch or declare them
            e.printStackTrace();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Notifies that we've finished the current page narration; typically called in response
    // to our own notification that the last narration is complete, but can also be called
    // at once in response to startNarration() if the page has no audio.
    @JavascriptInterface
    public void pageCompleted() {
        Log.d("JSEvent", "pageCompleted " + mPosition);
        mContext.pageAudioCompleted();
    }

    // Notifies Java that the JavaScript page is sufficiently loaded to receive messages,
    // particularly the startNarration() call. This is critical, because without it, we not
    // only don't know whether the page is loaded, but don't even know whether the actual
    // JavaScript code is loaded and the methods we expect are available to call. Our page
    // JavaScript must call this method at an appropriate point.
    @JavascriptInterface
    public void domContentLoaded() {
        Log.d("JSEvent", "domContentLoaded, page " + String.valueOf(mPosition));
        boolean shouldStart;
        boolean shouldPrepare;
        synchronized (this) {
            mDocLoaded = true;
            shouldStart = shouldStartNarrationWhenDocLoaded;
            shouldPrepare = shouldPrepareDocumentWhenLoaded;
        }
        if (shouldPrepare) {
            prepareDocument();
        }
        if (shouldStart) {
            startNarration();
        }
    }

    // Debugging convenience.
    @JavascriptInterface
    public void log(String message) {
        Log.d("Javascript XYX", message + " on page " + mPosition);
    }

    // Based on the domContentLoaded notification, starts the narration either at once if
    // things are ready, or as soon as we can.
    public void startNarrationWhenDocLoaded() {
        boolean shouldStart;
        synchronized (this) {
            shouldStart = mDocLoaded; // we can do it right away if already loaded
            if (!shouldStart)
                shouldStartNarrationWhenDocLoaded = true; // we can't, domContentLoaded should.
        }
        if (shouldStart) {
            startNarration();
        }
    }

    public void enableAnimation(boolean animate) {
        mWebView.evaluateJavascript("Root.enableAnimation(" +(animate? "true" : "false") + ")", null);
    }

    private void startNarration() {
        mContext.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d("JSEvent", "startNarration, page " + String.valueOf(mPosition));
                mWebView.evaluateJavascript("Root.startNarration()", null);
            }
        });
    }

    // Based on the domContentLoaded notification, prepares the document for animation either at once if
    // things are ready, or as soon as we can.
    public void prepareDocumentWhenDocLoaded() {
        boolean shouldPrepare;
        synchronized (this) {
            shouldPrepare = mDocLoaded; // we can do it right away if already loaded
            if (!shouldPrepare)
                shouldPrepareDocumentWhenLoaded = true; // we can't, domContentLoaded should.
        }
        if (shouldPrepare) {
            prepareDocument();
        }
    }

    private void prepareDocument() {
        mContext.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d("JSEvent", "handlePageBeforeVisible, page " + String.valueOf(mPosition));
                mWebView.evaluateJavascript("Root.handlePageBeforeVisible()", null);
            }
        });
    }
}