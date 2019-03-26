package org.sil.bloom.reader;

import android.media.MediaPlayer;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

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
    // Whether any media (audio or video) is paused or playing.
    private static boolean mVideoPaused;
    private static boolean mNarrationPaused;
    private static boolean mAnimationPaused;
    private static boolean mMusicPaused;
    private static boolean mVideoPlaying;
    private static boolean mNarrationPlaying;
    private static boolean mAnimationPlaying;
    private static boolean mMusicPlaying;
    // timestamp and elapsed time for animations
    private static long mAnimationStarted;  // millisecond uptime when started or resumed
    private static long mAnimationElapsed;  // millisecond elapsed time before pause (cumulative)
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
    private boolean shouldStartNarrationWhenDocLoaded = false;
    // Similarly if we need a postponed call to handleBeforeDocumentLoaded.
    private boolean shouldPrepareDocumentWhenLoaded = false;
    // The index of the page we belong to. As well as being useful in debugging, it can be compared
    // with the index of the currently visible page, so we ignore playback completed messages
    // if this page isn't current (and so it's not our audio that just completed).
    private int mPosition;
    // The multimedia state of the page at mPosition
    // When we respond to domContentLoaded, we ask the page to set this variable.
    public boolean mPageHasMultimedia = false;

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

    // Mainly this is to prevent the Paused state from persisting from one book to another.
    // Since the MediaPlayer docs say:
    // 1) you should call release() to free the resources, and
    // 2) if not released, too many MediaPlayer instances may result in an exception,
    // we release each MediaPlayer before creating a new one.
    public static void resetAll(){
        mVideoPaused = false;
        mNarrationPaused = false;
        mAnimationPaused = false;
        mMusicPaused = false;
        mVideoPlaying = false;
        mNarrationPlaying = false;
        mAnimationPlaying = false;
        mMusicPlaying = false;
        mp.release();
        mp = new MediaPlayer();
        mpBackground.release();
        mpBackground = new MediaPlayer();
    }

    // This can be helpful in debugging. It's not currently used in production.
    @JavascriptInterface
    public void showToast(String toast) {
        Toast.makeText(mContext, toast, Toast.LENGTH_SHORT).show();
    }

    // Pause the video at its current location.
    public void pauseVideo(final WebView webView) {
        mContext.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d("JSEvent", "pauseVideo, page " + String.valueOf(mPosition));
                webView.evaluateJavascript("Root.pauseVideo()", null);
            }
        });
    }

    // Don't just pause playing the video, also reset it to the beginning.
    public void stopVideo(final WebView webView) {
        mContext.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d("JSEvent", "stopVideo, page " + String.valueOf(mPosition));
                webView.evaluateJavascript("Root.stopVideo()", null);
            }
        });
    }

    public void playVideo(final WebView webView, final int milliDelay) {
        mContext.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d("JSEvent", "playVideo, page " + String.valueOf(mPosition));

                if (milliDelay > 0) {
                    String delay = Integer.toString(milliDelay);

                    // play, pause, wait, play
                    // If this looks like a hack, that's because it is!
                    // We want to delay the playback in situations like page load so a sign language "reader" doesn't miss some signs at the beginning.
                    // However, if we simply delay, we don't see the first frame but rather a placeholder image which looks horrible.
                    // We should be able to set preload="metadata" to solve this, but Android doesn't seem to do anything with it.
                    webView.evaluateJavascript("Root.playVideo();Root.pauseVideo();setTimeout(function(){Root.playVideo();}, " + delay + ");", null);
                } else {
                    webView.evaluateJavascript("Root.playVideo();", null);
                }
            }
        });
    }

    public void initializeCurrentPage() {
        Log.d("JSEvent", "initializeCurrentPage(), page "+String.valueOf(mPosition));
        mNarrationPaused = false;
        mVideoPaused = false;
        mMusicPaused = false;
        mAnimationPaused = false;
        // These may or may not be true depending on whether the media exists.
        // But they'll be made correct if the user touches the screen to pause/resume/restart.
        mNarrationPlaying = true;
        mMusicPlaying = hasMusic();
        mAnimationPlaying = true;
        mVideoPlaying = true;

        Log.d("JSEvent", "mpBackground.start, playVideo/delayed, resumeAnimation, page " + String.valueOf(mPosition));
        // startNarration callback playAudio calls mp.start.
        if (hasMusic())
            mpBackground.start();

        playVideo(mWebView, 1000);  // wait one second when first displaying page

        resumeAnimation();  // ensures animation plays in the absence of narration
    }

    private void pauseAnimation() {
        mContext.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d("JSEvent", "pauseAnimation, page " + String.valueOf(mPosition));
                mWebView.evaluateJavascript("Root.pauseAnimation()", null);
            }
        });
    }

    private void resumeAnimation() {
        mContext.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d("JSEvent", "resumeAnimation, page " + String.valueOf(mPosition));
                mWebView.evaluateJavascript("Root.resumeAnimation()", null);
            }
        });
    }

    // If the user touches the screen after narration, animation, or video finishes, don't make him touch it
    // twice to restart whatever multimedia exists.  See https://issues.bloomlibrary.org/youtrack/issue/BL-7003.
    // On the other hand, if only one has finished, don't start it when pausing or resuming one of the others.
    // Background sound is paused whenever anything else is paused, and resumed whenever anything else is
    // resumed.  Since it plays continually, the only time it is paused and resumed independently is when
    // none of the other multimedia is present.
    public void toggleAudioOrVideoPaused() {
        mContext.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d("JSEvent", "toggleAudioOrVideoPaused(): page " + String.valueOf(mPosition));
                mWebView.evaluateJavascript("Root.getMultiMediaStatus()", new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String status) {
                        Log.d("JSEvent","toggleAudioOrVideoPaused(): getMultiMediaStatus callback value=" + status);
                        if (!UpdatePlayingAndPausedState(status))
                            return;

                        Log.d("JSEvent", "toggleAudioOrVideoPaused(): mMusicPaused="+mMusicPaused+", mNarrationPaused="+ mNarrationPaused +", mVideoPaused="+mVideoPaused+", mAnimationPaused="+mAnimationPaused);
                        Log.d("JSEvent", "toggleAudioOrVideoPaused(): mMusicPlaying="+mMusicPlaying+", mNarrationPlaying="+ mNarrationPlaying+", mVideoPlaying="+mVideoPlaying+", mAnimationPlaying="+mAnimationPlaying);
                        if (mVideoPlaying || mNarrationPlaying || mAnimationPlaying || mMusicPlaying) {
                            // If anything is actively playing, pause it and mark it paused.
                            if (mVideoPlaying) {
                                pauseVideo(mWebView);
                                mVideoPaused = true;
                                mVideoPlaying = false;
                            }
                            if (mNarrationPlaying) {
                                mp.pause();
                                mNarrationPaused = true;
                                mNarrationPlaying = false;
                            }
                            if (mAnimationPlaying) {
                                pauseAnimation();
                                mAnimationPaused = true;
                                mAnimationPlaying = false;
                            }
                            if (mMusicPlaying) {
                                mpBackground.pause();
                                mMusicPaused = true;
                                mMusicPlaying = false;
                            }
                        } else if (mVideoPaused || mNarrationPaused || mAnimationPaused) {
                            // If anything is marked paused, let it continue and mark it as playing.
                            // Don't test for music to enter this block because if music and one of the other
                            // media exists, that would prevent the user from restarting any of those other
                            // media once they had finished.  Music (background audio to be precise) never
                            // finishes: it loops endlessly.
                            if (mVideoPaused) {
                                playVideo(mWebView, 0);
                                mVideoPaused = false;
                                mVideoPlaying = true;
                            }
                            if (mNarrationPaused) {
                                mp.start();
                                mNarrationPaused = false;
                                mNarrationPlaying = true;
                            }
                            if (mAnimationPaused) {
                                resumeAnimation();
                                mAnimationStarted = SystemClock.uptimeMillis();
                                mAnimationPaused = false;
                                mAnimationPlaying = true;
                            }
                            if (mMusicPaused) {
                                mpBackground.start();
                                mMusicPaused = false;
                                mMusicPlaying = true;
                            }
                        } else {
                            // Video, Narration, and/or Animation have all finished: restart them (if
                            // they exist). The next two methods will set whatever state variables need
                            // to be set, and are harmless if the corresponding media doesn't exist.
                            playVideo(mWebView, 0);
                            startNarration();   // will also start any animation
                            if (mMusicPaused) {
                                mpBackground.start();
                                mMusicPaused = false;
                                mMusicPlaying = true;
                            }
                        }
                    }
                });
            }
        });

    }

    private boolean UpdatePlayingAndPausedState(String status) {
        // Update the state variables based on the status information received from javascript land.
        boolean hasNarration;
        boolean hasVideo;
        boolean hasAnimation;
        double animationDuration;
        String jsonString = dequoteJSONString(status);
        try {
            JSONObject mediaStatus = new JSONObject(jsonString);
            hasNarration = mediaStatus.getBoolean("hasNarration");
            hasVideo = mediaStatus.getBoolean("hasVideo");
            mVideoPlaying = mediaStatus.getBoolean("videoIsPlaying");
            hasAnimation = mediaStatus.getBoolean("hasAnimation");
            animationDuration = mediaStatus.getDouble("pageDuration");
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
        if (!hasNarration) {
            mNarrationPlaying = false;
            mNarrationPaused = false;
        }
        if (!hasVideo) {
            mVideoPlaying = false;
            mVideoPaused = false;
        }
        if (!hasAnimation) {
            mAnimationPlaying = false;
            mAnimationPaused = false;
        } else {
            try {
                // Determine whether animation has finished by comparing elapsed uptime to the returned
                // duration value.
                if (mAnimationPlaying) {
                    mAnimationElapsed = mAnimationElapsed + (SystemClock.uptimeMillis() - mAnimationStarted);
                }
                double totalElapsedTime = (double) mAnimationElapsed / 1000.0;
                if (totalElapsedTime > (animationDuration + 0.5)) { // allow 1/2 second slop in measuring
                    mAnimationPlaying = false;
                    mAnimationPaused = false;
                }
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }
        if (!hasMusic()) {
            mMusicPaused = false;
            mMusicPlaying = false;
        }
        return true;
    }

    @NonNull
    // Remove a layer of quoting added in transmission from javascript to java.
    // 1. we know it's a string, it doesn't need an extra layer of double quotes surrounding the content.
    // 2. without the extra layer of double quotes, internal double quotes don't need a \ prefixed.
    private String dequoteJSONString(String s) {
        if (s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1);
            s = s.replace("\\\"", "\"");
        }
        return s;
    }

    public static boolean isMediaPaused() {
        return mNarrationPaused || mVideoPaused || mMusicPaused || mAnimationPaused;
    }

    public static void stopNarration() {
        Log.d("JSEvent", "stopNarration");
        if (mp.isPlaying())
            mp.stop();
        mp.reset();     // we no longer have valid data to play (BL-6925)
    }

    // When our app no longer in foreground
    public static void stopAllAudio() {
        Log.d("JSEvent", "stopAllAudio");
        stopNarration();
        if (mpBackground.isPlaying())
            mpBackground.stop();
    }

    private static boolean hasMusic() {
        return backgroundAudioPath != null && backgroundAudioPath.length() > 0;
    }

    public static void SetBackgroundAudio(String path, float volume) {
        if (path.equals(backgroundAudioPath))
            return;
        backgroundAudioPath = path;
        Log.d("JSEvent", "mpBackground stop && reset");

        if (mpBackground.isPlaying())
            mpBackground.stop();
        mpBackground.reset();
        if (!hasMusic())
            return;
        mpBackground.setLooping(true);
        try {
            mpBackground.setDataSource(backgroundAudioPath);
            mpBackground.prepare();
            mpBackground.setVolume(volume, volume);
            if (!isMediaPaused()) {
                mpBackground.start();
                mMusicPlaying = true;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Return true if the necessary file exists so that playAudio with the same argument will
    // actually play something. The argument comes from the JavaScript function
    // and is a path relative to the location of the HTML file.
    @JavascriptInterface
    public boolean audioExists(String aud) {
        String dataSource = mHtmlDirPath + "/" + aud;
        boolean fileExists = new File(dataSource).exists();
        Log.d("JSEvent", "audioExists("+aud+") => "+fileExists);
        return fileExists;
    }

    // Play an audio file from the webpage. The argument comes from the JavaScript function
    // and is a path relative to the location of the HTML file.
    @JavascriptInterface
    public void playAudio(String aud) {

        try {
            Log.d("JSEvent", "mp.stop && mp.reset && mp.setDataSource && mp.prepare && mp.start, page " + String.valueOf(mPosition));
            if (mp.isPlaying())
                mp.stop();
            mp.reset();
            String dataSource = mHtmlDirPath + "/" + aud;
            if (!new File(dataSource).exists()) {
                // Usually we expect audioExists() to be called and this function NOT to be called
                // if the audio doesn't exist. But in case it is, it MIGHT be helpful to the client
                // to indicate immediately that the audio is done, at least in the sense that
                // there is nothing yet to happen in connection with this play attempt.
                mContext.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Log.d("JSEvent", "fake playback completed for missing audio, page " + String.valueOf(mPosition));
                        mWebView.evaluateJavascript("Root.playbackCompleted()", null);
                    }
                });
            }
            mp.setDataSource(dataSource);
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
            if (!isMediaPaused()) {
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
        Log.d("JSEvent", "pageCompleted " + String.valueOf(mPosition));
        // This can be called from preloading a page before it is visible.
        if (mPosition == mContext.indexOfCurrentPage()) {
            mContext.pageAudioCompleted();
            mNarrationPlaying = false;
            mNarrationPaused = false;
        }
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
            initializePageHasMultimediaAsync();
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
                // This should only ever be called on the currently visible page!
                if (mPosition == mContext.indexOfCurrentPage()) {
                    mNarrationPlaying = true;
                    mNarrationPaused = false;
                    // startNarration restarts the animation if it exists even if there's no narration.
                    mAnimationPlaying = true;
                    mAnimationPaused = false;
                    mAnimationStarted = SystemClock.uptimeMillis();
                    mAnimationElapsed = 0L;
                } else {
                    Log.e("JSEvent","startNarration called on page "+mPosition+" but current page is "+mContext.indexOfCurrentPage());
                }
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

    public void initializePageHasMultimediaAsync() {
        mContext.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d("JSEvent", "requestPageMultimediaState, page " + String.valueOf(mPosition));
                mWebView.evaluateJavascript("Root.requestPageMultimediaState()", new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String s) {
                        Log.d("JSEvent","requestPageMultimediaState, page " + String.valueOf(mPosition)
                                + " callback=" + s);
                        // onReceiveValue returns some form of JSON according to
                        // https://stackoverflow.com/questions/19788294/how-does-evaluatejavascript-work
                        // Here we only expect a single value, but that still means we get an extra
                        // set of quotes.
                        // Enhance: JH says we're actually going to need the state of various types
                        // of multimedia eventually, so we'll probably need the JSON parser in the long run.
                        mPageHasMultimedia = s.equals("\"true\"");
                    }
                });
            }
        });
    }
}