package org.sil.bloom.reader;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.constraint.ConstraintLayout;
import android.support.constraint.ConstraintSet;
import android.support.annotation.Nullable;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.webkit.WebView;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.segment.analytics.Analytics;
import com.segment.analytics.Properties;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.sil.bloom.reader.models.BookOrShelf;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class ReaderActivity extends BaseActivity {

    private static final String TAG = "ReaderActivity";// https://developer.android.com/reference/android/util/Log.html
    private static final String sAssetsStylesheetLink = "<link rel=\"stylesheet\" href=\"file:///android_asset/book support files/assets.css\" type=\"text/css\"></link>";
    private static final String sAssetsBloomPlayerScript = "<script type=\"text/javascript\" src=\"file:///android_asset/book support files/bloomPagePlayer.js\"></script>";
    private static final Pattern sLayoutPattern = Pattern.compile("\\S+([P|p]ortrait|[L|l]andscape)\\b");
    private static final Pattern sHeadElementEndPattern = Pattern.compile("</head");
    private static final Pattern sMainLangauge = Pattern.compile("<div data-book=\"contentLanguage1\"[^>]*>\\s*(\\S*)");
    // Matches a div with class bloom-page, that is, the start of the main content of one page.
    // (We're looking for the start of a div tag, then before finding the end wedge, we find
    // class<maybe space>=<maybe space><some sort of quote> and then bloom-page before we find
    // another quote.
    // Bizarre mismatches are possible...like finding the other sort of quote inside the class
    // value, or finding class='bloom-page' inside the value of some other attribute. But I think
    // it's good enough.)
    private static final Pattern sPagePattern = Pattern.compile("<div\\s+[^>]*class\\s*=\\s*['\"][^'\"]*bloom-page");

    // For searching in a page string to see whether it's a back-matter page. Looks for bloom-backmatter
    // in a class attribute in the first opening tag.
    private static final Pattern sBackPagePattern = Pattern.compile("[^>]*class\\s*=\\s*['\"][^'\"]*bloom-backMatter");

    // Matches a page div with the class numberedPage...that string must occur in a class attribute before the
    // close of the div element.
    private static final Pattern sNumberedPagePattern = Pattern.compile("[^>]*?class\\s*=\\s*['\"][^'\"]*numberedPage");

    private static final Pattern sBackgroundAudio = Pattern.compile("[^>]*?data-backgroundaudio\\s*=\\s*['\"]([^'\"]*)?['\"]");
    private static final Pattern sBackgroundVolume = Pattern.compile("[^>]*?data-backgroundaudiovolume\\s*=\\s*['\"]([^'\"]*)?['\"]");
    private static final Pattern sClassAttrPattern = Pattern.compile("class\\s*=\\s*(['\"])(.*?)\\1");

    private static final Pattern sContentLangDiv = Pattern.compile("<div [^>]*?data-book=\"contentLanguage1\"[^>]*?>\\s*(\\S+)");
    private static final Pattern sBodyPattern = Pattern.compile("<body [^>]*?>");

    private static final Pattern sAutoAdvance = Pattern.compile("data-bfautoadvance\\s*?=\\s*?\"[^\"]*?\\bbloomReader\\b.*?\"");

    private ViewPager mPager;
    private BookPagerAdapter mAdapter;
    private String mBookName ="?";
    private int mAudioPagesPlayed = 0;
    private int mNonAudioPagesShown = 0;
    private int mLastNumberedPageIndex = -1;
    private int mNumberedPageCount = 0;
    private boolean mLastNumberedPageRead = false;
    private boolean mAutoAdvance = false; // automatically advance to next page at end of narration
    private boolean mPlayMusic = true; // play background music if present
    private int mOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    private boolean mPlayAnimation = true; // play animations (pan and zoom) if present.
    private String mContentLang1 = "unknown";
    private String mBodyTag;
    int mFirstQuestionPage;
    int mCountQuestionPages;
    ScaledWebView mCurrentView;
    String[] mBackgroundAudioFiles;
    float[] mBackgroundAudioVolumes;

    // Keeps track of whether we switched pages while audio paused. If so, we don't resume
    // the audio of the previously visible page, but start this page from the beginning.
    boolean mSwitchedPagesWhilePaused = false;
    // These variables support a minimum time on each page before we automatically switch to
    // the next (if the audio on this page is short or non-existent).
    private long mTimeLastPageSwitch;
    private Timer mNextPageTimer;
    private boolean mIsMultiMediaBook;
    private boolean mRTLBook;
    private String mBrandingProjectName;
    private String mFailedToLoadBookMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        //}

        setContentView(R.layout.activity_reader);
//        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
//        setSupportActionBar(toolbar);

        mBrandingProjectName = getIntent().getStringExtra("brandingProjectName");

        new Loader().execute();
    }

    @Override
    protected void onPause() {
        if (isFinishing()) {
            ReportPagesRead();
        }
        super.onPause();
        WebAppInterface.stopAllAudio();
    }

    @Override
    protected void onResume(){
        super.onResume();
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN + View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private void ReportPagesRead()
    {
        try {
            // let's differentiate between pages read and audio book pages read: (BL-5082)
            Properties p = new Properties();
            p.putValue("title", mBookName);
            p.putValue("audioPages", mAudioPagesPlayed);
            p.putValue("nonAudioPages", mNonAudioPagesShown);
            p.putValue("totalNumberedPages", mNumberedPageCount);
            p.putValue("lastNumberedPageRead", mLastNumberedPageRead);
            p.putValue("questionCount", mAdapter.mQuiz.numberOfQuestions());
            p.putValue("contentLang", mContentLang1);
            if (mBrandingProjectName != null) {
                p.putValue("brandingProjectName", mBrandingProjectName);
            }
            Analytics.with(BloomReaderApplication.getBloomApplicationContext()).track("Pages Read", p);
        } catch (Exception e) {
            Log.e(TAG, "Pages Read", e);
            // I doubt this message will help the user, and will probably just be confusing when it
            // comes up because a book failed to open in the first place.
            // BloomReaderApplication.VerboseToast("Error reporting Pages Read");
        }
    }

    // class to run loadBook in the background (so the UI thread is available to animate the progress bar)
    private class Loader extends AsyncTask<Void, Integer, String> {

        @Override
        protected String doInBackground(Void... v) {
            final String path = getIntent().getStringExtra("bookPath");
            BloomFileReader fileReader = new BloomFileReader(getApplicationContext(), path);
            String bookDirectory;
            try {
                final File bookHtmlFile = fileReader.getHtmlFile();
                bookDirectory = bookHtmlFile.getParent();
                String html = IOUtilities.FileToString(bookHtmlFile);
                mIsMultiMediaBook = isMultiMediaBook(html);
                WebAppInterface.resetAll();
                // Break the html into everything before the first page, a sequence of pages,
                // and the bit after the last. Note: assumes there is nothing but the </body> after
                // the last page, that is, that pages are the direct children of <body> and
                // nothing follows the last page.
                final Matcher matcher = sPagePattern.matcher(html);
                String startFrame = "";
                String endFrame = "";
                ArrayList<String> pages = new ArrayList<String>();

                // if we don't find even one start of page, we have no pages, and don't need startFrame, endFrame, etc.
                if (matcher.find()) {
                    int firstPageIndex = matcher.start();
                    startFrame = html.substring(0, firstPageIndex);
                    Matcher match = sContentLangDiv.matcher(startFrame);
                    if (match.find()) {
                        mContentLang1 = match.group(1);
                    }
                    startFrame = addAssetsStylesheetLink(startFrame);
                    int startPage = firstPageIndex;
                    while (matcher.find()) {
                        final String pageContent = html.substring(startPage, matcher.start());
                        AddPage(pages, pageContent);
                        startPage = matcher.start();
                    }
                    mFirstQuestionPage = pages.size();
                    for (; mFirstQuestionPage > 0; mFirstQuestionPage--) {
                        String pageContent = pages.get(mFirstQuestionPage-1);
                        if (!sBackPagePattern.matcher(pageContent).find()) {
                            break;
                        }
                    }
                    int endBody = html.indexOf("</body>", startPage);
                    AddPage(pages, html.substring(startPage, endBody));
                    // We can leave out the bloom player JS altogether if not needed.
                    endFrame = (mIsMultiMediaBook ? sAssetsBloomPlayerScript : "")
                            + html.substring(endBody, html.length());
                }

                boolean hasEnterpriseBranding = mBrandingProjectName != null && !mBrandingProjectName.toLowerCase().equals("default");
                Quiz quiz = new Quiz();
                if (hasEnterpriseBranding) {
                    String primaryLanguage = getPrimaryLanguage(html);
                    quiz = Quiz.readQuizFromFile(fileReader, primaryLanguage);

                    mCountQuestionPages = quiz.numberOfQuestions();
                    for (int i = 0; i < mCountQuestionPages; i++) {
                        // insert all these pages just before the final 'end' page.
                        pages.add(mFirstQuestionPage, "Q");
                    }
                }

                mBackgroundAudioFiles = new String[pages.size()];
                mBackgroundAudioVolumes = new float[pages.size()];
                String currentBackgroundAudio = "";
                float currentVolume = 1.0f;
                for (int i = 0; i < pages.size(); i++) {
                    if (pages.get(i) == "Q") {
                        mBackgroundAudioFiles[i] = "";
                        currentBackgroundAudio = "";
                        continue;
                    }
                    Matcher bgMatcher =sBackgroundAudio.matcher(pages.get(i));
                    if (bgMatcher.find()) {
                        currentBackgroundAudio = bgMatcher.group(1);
                        if (currentBackgroundAudio == null) // may never happen?
                            currentBackgroundAudio = "";
                        // Getting a new background file implies full volume unless specified.
                        currentVolume = 1.0f;
                    }
                    Matcher bgvMatcher =sBackgroundVolume.matcher(pages.get(i));
                    if (bgvMatcher.find()) {
                        try {
                            currentVolume = Float.parseFloat(bgvMatcher.group(1));
                        }
                        catch (NumberFormatException e) {
                            e.printStackTrace();
                        }
                    }
                    mBackgroundAudioFiles[i] = currentBackgroundAudio;
                    mBackgroundAudioVolumes[i] = currentVolume;
                }

                mRTLBook = fileReader.getBooleanMetaProperty("isRtl", false);
                // The body tag captured here is parsed in setFeatureEffects after we know our orientation.
                Matcher bodyMatcher = sBodyPattern.matcher(startFrame);
                if (bodyMatcher.find()){
                    mBodyTag = bodyMatcher.group(0);
                } else {
                    mBodyTag = "<body>"; // a trivial default, saves messing with nulls.
                }

                mAdapter = new BookPagerAdapter(pages, quiz, ReaderActivity.this, bookHtmlFile, startFrame, endFrame);

                reportLoadBook(path);
            } catch (IOException ex) {
                Log.e("Reader", "Error loading " + path + "  " + ex);
                mFailedToLoadBookMessage = (ex.getMessage() != null && ex.getMessage().contains("ENOSPC")) ? getString(R.string.device_storage_is_full) : getString(R.string.failed_to_open_book);
                return null;
            }
            return bookDirectory;
        }

        @Override
        protected void onPostExecute(String bookDirectory){
            if(bookDirectory == null){
                if(mFailedToLoadBookMessage != null)
                    Toast.makeText(ReaderActivity.this, mFailedToLoadBookMessage, Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            final String audioDirectoryPath = bookDirectory + "/audio/";
            mPager = (ViewPager) findViewById(R.id.book_pager);
            if(mRTLBook)
                mPager.setRotationY(180);
            mPager.setAdapter(mAdapter);
            final BloomPageChangeListener listener = new BloomPageChangeListener(audioDirectoryPath);
            mPager.addOnPageChangeListener(listener);
            // Now we're ready to display the book, so hide the 'progress bar' (spinning circle)
            findViewById(R.id.loadingPanel).setVisibility(View.GONE);

            // A design flaw in the ViewPager is that its onPageSelected method does not
            // get called for the page that is initially displayed. But we want to do all the
            // same things to the first page as the others. So we will call it
            // manually. Using post delays this until everything is initialized and the view
            // starts to process events. I'm not entirely sure why this should be done;
            // I copied this from something on StackOverflow. Probably it means that the
            // first-page call happens in a more similar situation to the change-page calls.
            // It might work to just call it immediately.
            mPager.post(new Runnable()
            {
                @Override
                public void run()
                {
                    listener.onPageSelected(mPager.getCurrentItem());
                }
            });
        }
    }

    private boolean isMultiMediaBook(String html) {
        // Enhance: eventually also look for images with animation data?

        return sAutoAdvance.matcher(html).find() ||
        // This is a fairly crude search, we really want the doc to have spans with class
        // audio-sentence; but I think it's a sufficiently unlikely string to find elsewhere
        // that this is good enough.
        html.indexOf("audio-sentence") >= 0;
    }

    private class BloomPageChangeListener extends ViewPager.SimpleOnPageChangeListener {
        String audioDirectoryPath;

        BloomPageChangeListener(String audioDirectoryPath){
            this.audioDirectoryPath = audioDirectoryPath;
        }

        @Override
        public void onPageSelected(int position) {
            super.onPageSelected(position);
            clearNextPageTimer(); // in case user manually moved to a new page while waiting
            WebView oldView = mCurrentView;
            mCurrentView = mAdapter.getActiveView(position);
            mTimeLastPageSwitch = System.currentTimeMillis();

            stopAndStartVideos(oldView, mCurrentView);

            if (oldView != null)
                oldView.clearCache(false); // Fix for BL-5555

            if (mIsMultiMediaBook) {
                mSwitchedPagesWhilePaused = WebAppInterface.isNarrationPaused();
                WebAppInterface.stopNarration(); // don't want to hear rest of anything on another page
                String backgroundAudioPath = "";
                if (mPlayMusic) {
                    if (mBackgroundAudioFiles[position].length() > 0) {
                        backgroundAudioPath = audioDirectoryPath + mBackgroundAudioFiles[position];
                    }
                    WebAppInterface.SetBackgroundAudio(backgroundAudioPath, mBackgroundAudioVolumes[position]);
                }
                // This new page may not be in the correct paused state.
                // (a) maybe we paused this page, moved to another, started narration, moved
                // back to this (adapter decided to reuse it), this one needs to not be paused.
                // (b) maybe we moved to another page while not paused, paused there, moved
                // back to this one (again, reused) and old animation is still running
                if (mCurrentView != null && mCurrentView.getWebAppInterface() != null) {
                    WebAppInterface appInterface = mCurrentView.getWebAppInterface();
                    appInterface.setPaused(WebAppInterface.isNarrationPaused());
                    if (!WebAppInterface.isNarrationPaused() && mIsMultiMediaBook) {
                        appInterface.enableAnimation(mPlayAnimation);
                        // startNarration also starts the animation (both handled by the BloomPlayer
                        // code) iff we passed true to enableAnimation().
                        mAdapter.startNarrationForPage(position);
                        // Note: this isn't super-reliable. We tried to narrate this page, but it may not
                        // have any audio. All we know is that it's part of a book which has
                        // audio (or animation) somewhere, and we tried to play any audio it has.
                        mAudioPagesPlayed++;
                    } else {
                        mNonAudioPagesShown++;
                    }
                }
            }
            else {
                mNonAudioPagesShown++;
            }
            if (position == mLastNumberedPageIndex)
                mLastNumberedPageRead = true;
        }
    };

    // Minimum time a page must be visible before we automatically switch to the next
    // (if playing audio...usually this only affects pages with no audio)
    final int MIN_PAGE_SWITCH_MILLIS = 3000;

    // This is the routine that is invoked as a result of a call-back from javascript indicating
    // that the current page is complete (which in turn typically follows a notification from
    // Java that a sound finished playing...but it is the JS that knows it is the last audio
    // on the page). Basically it is responsible for flipping to the next page (see goToNextPageNow).
    // But, we get the notification instantly if there is NO audio on the page.
    // So we do complicated things with a timer to make sure we don't flip the page too soon...
    // the minimum delay is specified in MIN_PAGE_SWITCH_MILLIS.
    public void pageAudioCompleted() {
        if (!mAutoAdvance)
            return;

        clearNextPageTimer();
        long millisSinceLastSwitch = System.currentTimeMillis() - mTimeLastPageSwitch;
        if (millisSinceLastSwitch >= MIN_PAGE_SWITCH_MILLIS) {
            goToNextPageNow();
            return;
        }
        // If nothing else happens, we will go to next page when the minimum time has elapsed.
        // Unfortunately, the only way to stop scheduled events in a Java timer is to destroy it.
        // So that's what clearNextPageTimer() does (e.g., if the user manually changes pages).
        // Consequently, we have to make a new one each time.
        synchronized (this) {
            mNextPageTimer = new Timer();
            mNextPageTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    goToNextPageNow();
                }
            }, MIN_PAGE_SWITCH_MILLIS - millisSinceLastSwitch);
        }
    }

    void clearNextPageTimer() {
        synchronized (this) {
            if (mNextPageTimer == null)
                return;
            mNextPageTimer.cancel();
            mNextPageTimer = null;
        }
    }

    private void goToNextPageNow() {
        clearNextPageTimer();
        runOnUiThread(new Runnable() {
              @Override
              public void run() {
                  // In case some race condition has this getting called while we are paused,
                  // don't let it happen.
                  if (WebAppInterface.isNarrationPaused()) {
                      return;
                  }
                  int current = mPager.getCurrentItem();
                  if (current < mAdapter.getCount() - 1) {
                      mPager.setCurrentItem(current + 1);
                  }
              }
          }
        );
    }

    public int indexOfCurrentPage() {
        return mPager.getCurrentItem();
    }

    public void narrationPausedChanged() {
        final ImageView view = (ImageView)findViewById(R.id.playPause);
        if (WebAppInterface.isNarrationPaused()) {
            clearNextPageTimer(); // any pending automatic page flip should be prevented.
            view.setImageResource(R.drawable.pause_on_circle); // black circle around android.R.drawable.ic_media_pause);
        } else {
            view.setImageResource(R.drawable.play_on_circle);
            if(mSwitchedPagesWhilePaused) {
                final int position = mPager.getCurrentItem();
                mAdapter.startNarrationForPage(position); // also starts animation if any
            }
        }
        mSwitchedPagesWhilePaused = false;
        final Animation anim = AnimationUtils.loadAnimation(this, R.anim.grow_and_fade);
        // Make the view hidden when the animation finishes. Otherwise it returns to full visibility.
        anim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                // required method for abstract class
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                view.setVisibility(View.INVISIBLE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
                // required method for abstract class
            }
        });
        view.setVisibility(View.VISIBLE);
        view.startAnimation(anim);
    }

    @Override
    protected void onNewOrUpdatedBook(String fullPath) {
        ((BloomReaderApplication)this.getApplication()).setBookToHighlight(fullPath);
        Intent intent = new Intent(this, MainActivity.class);
        // Clears the history so now the back button doesn't take from the main activity back to here.
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void reportLoadBook(String path)
    {
        try {
            String filenameWithExtension = new File(path).getName();
            // this mBookName is used by subsequent analytics reports
            mBookName = filenameWithExtension.substring(0, filenameWithExtension.length() - IOUtilities.BOOK_FILE_EXTENSION.length());
            Properties p = new Properties();
            p.putValue("title", mBookName);
            p.putValue("totalNumberedPages", mNumberedPageCount);
            p.putValue("contentLang", mContentLang1);
            p.putValue("questionCount", mAdapter.mQuiz.numberOfQuestions());
            if (mBrandingProjectName != null) {
                p.putValue("brandingProjectName", mBrandingProjectName);
            }
            Analytics.with(BloomReaderApplication.getBloomApplicationContext()).track("BookOrShelf opened", p);
        } catch (Exception error) {
            Log.e("Reader", "Error reporting load of " + path + ".  "+ error);
            BloomReaderApplication.VerboseToast("Error reporting load of "+path);
        }
    }

    // Given something like <body data-bfplaymusic="landscape;bloomReader"> for bodyTag
    // and a request to find the playmusic feature, returns landscape.
    // defValue is returned if
    // - the relevant attribute is not found
    // - bloomReader does not occur in the value (after the semi-colon)
    private String getFeatureValue(String featureName, String defValue) {
        final Pattern featurePattern = Pattern.compile("data-bf" + featureName + "\\s*=\\s*['\"]([^'\"]*?);([^'\"]*?)bloomReader");
        Matcher matcher = featurePattern.matcher(mBodyTag);
        if (!matcher.find()) {
            return defValue;
        }
        return matcher.group(1);
    }

    private boolean getBooleanFeature(String featureName, boolean defValue, boolean inLandscape) {
        String rawValue = getFeatureValue(featureName, defValue ? "allOrientations" : "never");
        if (rawValue.equals("allOrientations"))
            return true;
        if (inLandscape && rawValue.equals("landscape"))
            return true;
        if (!inLandscape && rawValue.equals("portrait"))
            return true;
        return false;
    }

    private void setFeatureEffects(boolean inLandscape) {
        mAutoAdvance = getBooleanFeature("autoadvance", false, inLandscape);
        mPlayMusic = getBooleanFeature("playmusic", true, inLandscape);
        mPlayAnimation = getBooleanFeature("playanimations", true, inLandscape);
    }

    private void stopAndStartVideos(WebView oldView, WebView currentView){
        // Selects the first (and presumably only) video on the page if any exists
        String videoSelector = "document.getElementsByTagName('video')[0]";

        if(oldView != null)
            oldView.evaluateJavascript("if(" + videoSelector + ") {" + videoSelector + ".pause();}", null);
        if(currentView != null)
            currentView.evaluateJavascript("if(" + videoSelector + ") {" + videoSelector + ".play();}", null);
    }

    private void AddPage(ArrayList<String> pages, String pageContent) {
        pages.add(pageContent);
        if (sNumberedPagePattern.matcher(pageContent).find()) {
            mLastNumberedPageIndex = pages.size() - 1;
            mNumberedPageCount++;
        }
    }

    private String getPrimaryLanguage(String html) {
        Matcher matcher = sMainLangauge.matcher(html);
        if (!matcher.find())
            return "en";
        return matcher.group(1);
    }

    private String addAssetsStylesheetLink(String htmlSnippet) {
        final Matcher matcher = sHeadElementEndPattern.matcher(htmlSnippet);
        if (matcher.find()) {
            return htmlSnippet.substring(0, matcher.start()) + sAssetsStylesheetLink + htmlSnippet.substring(matcher.start());
        }
        return htmlSnippet;
    }

    // Prefer to base device landscape on mOrientation if it's determinative
    private boolean isDeviceInLandscape() {
        switch (mOrientation) {
            case ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:
                return true;
            case ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT:
                return false;
            case ActivityInfo.SCREEN_ORIENTATION_SENSOR:
            default:
                return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        }
    }

    private int getPageOrientationAndRotateScreen(String page){
        // default: fixed in portrait mode
        mOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT;
        Matcher matcher = sClassAttrPattern.matcher(page);
        if (matcher.find()) {
            String classNames = matcher.group(2);
            if (classNames.contains("Landscape"))
                mOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE; // fixed landscape
        }
        if (getFeatureValue("canrotate", "never").equals("allOrientations"))
            mOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR; // change as rotated

        setRequestedOrientation(mOrientation);
        return mOrientation;
    }

    // Transforms [x]Portrait or [x]Landscape class to Device16x9Portrait / Device16x9Landscape
    private String pageUsingDeviceLayout(String page) {
        // Get the content of the class attribute and its position
        Matcher matcher = sClassAttrPattern.matcher(page);
        if (!matcher.find())
            return page; // don't think this can happen, we create pages by finding class attr.
        int start = matcher.start(2);
        int end = matcher.end(2);
        String classNames = matcher.group(2);
        String replacementClass = "Device16x9$1";
        String newClassNames = sLayoutPattern.matcher(classNames).replaceFirst(replacementClass);
        return page.substring(0,start) // everything up to the opening quote in class="
                + newClassNames
                + page.substring(end, page.length()); // because this includes the original closing quote from class attr
    }

    private int getPageScale(int viewWidth, int viewHeight){
        // 378 x 674 are the dimensions of the Device16x9 layouts in pixels

        int longSide = (viewWidth > viewHeight) ? viewWidth : viewHeight;
        int shortSide = (viewWidth > viewHeight) ? viewHeight : viewWidth;
        Double longScale = new Double(longSide / new Double(674));
        Double shortScale = new Double(shortSide / new Double(378));
        Double scale = Math.min(longScale, shortScale);
        scale = scale * 100d;
        return scale.intValue();
    }

    // Copy in files like bloomPlayer.js
    private void updateSupportFiles(String bookFolderPath) {
        IOUtilities.copyAssetFolder(this.getApplicationContext().getAssets(), "book support files", bookFolderPath);
    }

    enum pageAnswerState {
        unanswered,
        firstTimeCorrect,
        secondTimeCorrect,
        wrongOnce,
        wrong
    }

    // Class that provides individual page views as needed.
    // possible enhancement: can we reuse the same browser, just change which page is visible?
    private class BookPagerAdapter extends PagerAdapter implements QuestionAnsweredHandler {
        // Each item is the full HTML text of one page div (div with class bloom-page).
        // the concatenation of mHtmlBeforeFirstPageDiv, mHtmlPageDivs, and mHtmlAfterLastPageDiv
        // is the whole book HTML file. (The code here assumes there is nothing in the body
        // of the document except page divs.)
        // The concatenation of mHtmlBeforeFirstPageDiv, one item from mHtmlPageDivs, and
        // mHtmlAfterLastPageDiv is the content we put in a browser representing a single page.
        private List<String> mHtmlPageDivs;
        Quiz mQuiz;
        pageAnswerState[] mAnswerStates;
        boolean mQuestionAnalyticsSent;
        private String mHtmlBeforeFirstPageDiv;
        private String mHtmlAfterLastPageDiv;
        ReaderActivity mParent;
        File mBookHtmlPath;

        int mLastPageIndex;
        int mThisPageIndex;
        // This map allows us to convert from the page index we get from the ViewPager to
        // the actual child WebView on that page. There ought to be a way to get the actual
        // current child control from the ViewPager, but I haven't found it yet.
        // Note that it only tracks WebViews for items that have been instantiated and not
        // yet destroyed; this is important to allow others to be garbage-collected.
        private HashMap<Integer, ScaledWebView> mActiveViews = new HashMap<Integer, ScaledWebView>();

        BookPagerAdapter(List<String> htmlPageDivs,
                         Quiz quiz,
                         ReaderActivity parent,
                         File bookHtmlPath,
                         String htmlBeforeFirstPageDiv,
                         String htmlAfterLastPageDiv) {
            mHtmlPageDivs = htmlPageDivs;
            mQuiz = quiz;
            mAnswerStates = new pageAnswerState[mQuiz.numberOfQuestions()];
            for (int i = 0; i < mAnswerStates.length; i++) {
                mAnswerStates[i] = pageAnswerState.unanswered;
            }
            mParent = parent;
            mBookHtmlPath = bookHtmlPath;
            mHtmlBeforeFirstPageDiv = htmlBeforeFirstPageDiv;
            mHtmlAfterLastPageDiv = htmlAfterLastPageDiv;
        }

        @Override
        public void destroyItem(ViewGroup collection, int position, Object view) {
            Log.d("Reader", "destroyItem " + position);
            collection.removeView((View) view);
            mActiveViews.remove(position);
        }


        @Override
        public int getCount() {
            //Log.d("Reader", "getCount = "+ mHtmlPageDivs.size());
            return mHtmlPageDivs.size();
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            Log.d("Reader", "instantiateItem " + position);
            assert(container.getChildCount() == 0);
            String page = mHtmlPageDivs.get(position);

            View pageView;
            if (page.startsWith("<")) {
                // normal content page
                pageView = MakeBrowserForPage(position);
            }
            else {
                final int questionIndex = position - mFirstQuestionPage;
                QuestionPageGenerator questionPageGenerator = new QuestionPageGenerator(ReaderActivity.this);
                pageView = questionPageGenerator.generate(mQuiz.getQuizQuestion(questionIndex), mQuiz.numberOfQuestions(), this);
            }

            container.addView(pageView);
            return pageView;
        }

        public void questionAnswered(int questionIndex, boolean correct) { ReaderActivity.pageAnswerState oldAnswerState = mAnswerStates[questionIndex];
            if (correct) {
                if (oldAnswerState == ReaderActivity.pageAnswerState.wrongOnce)
                    mAnswerStates[questionIndex] = ReaderActivity.pageAnswerState.secondTimeCorrect;
                else if (oldAnswerState == ReaderActivity.pageAnswerState.unanswered)
                    mAnswerStates[questionIndex] = ReaderActivity.pageAnswerState.firstTimeCorrect;
                // if they already got it wrong twice they get no credit.
                // if they already got it right no credit for clicking again.
            } else {
                if (oldAnswerState == ReaderActivity.pageAnswerState.unanswered)
                    mAnswerStates[questionIndex] = ReaderActivity.pageAnswerState.wrongOnce;
                else if (oldAnswerState == ReaderActivity.pageAnswerState.wrongOnce)
                    mAnswerStates[questionIndex] = ReaderActivity.pageAnswerState.wrong;
                // if they previously got it right we won't hold it against them
                // that they now get it wrong.
            }
            if (!mQuestionAnalyticsSent) {
                boolean allAnswered = true;
                int rightFirstTime = 0;
                for (int i = 0; i < mAnswerStates.length; i++) {
                    if (mAnswerStates[i] == ReaderActivity.pageAnswerState.unanswered) {
                        allAnswered = false;
                        break;
                    } else if (mAnswerStates[i] == ReaderActivity.pageAnswerState.firstTimeCorrect) {
                        rightFirstTime++;
                    }
                }
                if (allAnswered) {
                    Properties p = new Properties();
                    p.putValue("title", mBookName);
                    p.putValue("questionCount", mAnswerStates.length);
                    p.putValue("rightFirstTime", rightFirstTime);
                    p.putValue("percentRight", rightFirstTime * 100 / mAnswerStates.length);
                    if (mBrandingProjectName != null) {
                        p.putValue("brandingProjectName", mBrandingProjectName);
                    }
                    Analytics.with(BloomReaderApplication.getBloomApplicationContext()).track("Questions correct", p);
                    // Don't send again unless they re-open the book and start over.
                    mQuestionAnalyticsSent = true;
                }
            }
        }

        // position should in fact be the position of the pager.
        public ScaledWebView getActiveView(int position) {
            return mActiveViews.get(position);
        }


        public void prepareForAnimation(int position) {
            final ScaledWebView pageView = mActiveViews.get(position);
            if (pageView == null) {
                Log.d("prepareForAnimation", "can't find page for " + position);
                return;
            }

            if (pageView.getWebAppInterface() != null)
                pageView.getWebAppInterface().prepareDocumentWhenDocLoaded();
        }

        public void startNarrationForPage(int position) {
            ScaledWebView pageView = mActiveViews.get(position);
            if (pageView == null) {
                Log.d("startNarration", "can't find page for " + position);
                return;
            }
            if (pageView.getWebAppInterface() != null)
                pageView.getWebAppInterface().startNarrationWhenDocLoaded();
        }

        private WebView MakeBrowserForPage(int position) {
            ScaledWebView browser = null;
            boolean inLandscape;
            try {
                String page = mHtmlPageDivs.get(position);
                if(position == 0) {
                    getPageOrientationAndRotateScreen(page);
                    inLandscape = isDeviceInLandscape();
                    setFeatureEffects(inLandscape); // depends on what orientation we're actually in, so can't do sooner.
                }
                else {
                    inLandscape = isDeviceInLandscape();
                }
                browser = new ScaledWebView(mParent, position);
                mActiveViews.put(position, browser);
                if (mIsMultiMediaBook) {
                    WebAppInterface appInterface = new WebAppInterface(this.mParent, mBookHtmlPath.getParent(), browser, position);
                    browser.setWebAppInterface(appInterface);
                }
                // Styles to force 0 border and to vertically center books
                String moreStyles = "<style>html{ height: 100%; }  body{ min-height:100%; display:flex; align-items:center; } div.bloom-page { border:0 !important; }</style>\n";
                String doc = mHtmlBeforeFirstPageDiv + moreStyles + pageUsingDeviceLayout(page) + mHtmlAfterLastPageDiv;

                browser.loadDataWithBaseURL("file:///" + mBookHtmlPath.getAbsolutePath(), doc, inLandscape);
                prepareForAnimation(position);
            }catch (Exception ex) {
                Log.e("Reader", "Error loading " + mBookHtmlPath.getAbsolutePath() + "  " + ex);
            }
            return browser;
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            Log.d("Reader", "isViewFromObject = " + (view == object));
            return view == object;
        }
    }

    private class ScaledWebView extends WebView {
        private String data;
        private String baseUrl;
        private int page;
        private int scale = 0;

        public ScaledWebView(Context context, int page) {
            super(context);
            this.page = page;
            if(mRTLBook)
                setRotationY(180);
            getSettings().setJavaScriptEnabled(true);
            getSettings().setMediaPlaybackRequiresUserGesture(false);
        }

        public void loadDataWithBaseURL(String baseUrl, String data, boolean inLandscape){
            this.baseUrl = baseUrl;
            if (inLandscape)
                data = data.replace("Device16x9Portrait", "Device16x9Landscape");
            else
                data = data.replace("Device16x9Landscape", "Device16x9Portrait");
            this.data = data;
            loadDataWithBaseURL(baseUrl, data, "text/html", "utf-8", null);
        }

        public void setWebAppInterface(WebAppInterface appInterface){
            addJavascriptInterface(appInterface, "Android");
            // Save the WebAppInterface in the browser's tag because there's no simple
            // way to get from the browser to the object we set as the JS interface.
            setTag(appInterface);
        }

        @Nullable
        public WebAppInterface getWebAppInterface(){
            if(getTag() instanceof WebAppInterface)
                return (WebAppInterface) getTag();
            return null;
        }

        // This method will be called on all Webviews in memory when orientation changes
        public void reloadPage(boolean inLandscape){
            setFeatureEffects(inLandscape);
            loadDataWithBaseURL(baseUrl, data, inLandscape);
            resetMultiMedia();
        }

        // This method will be called on all Webviews in memory when orientation changes
        // Actions that should only run once are in the if block that checks the current page
        private void resetMultiMedia(){
            WebAppInterface appInterface = getWebAppInterface();
            if (appInterface != null) {
                appInterface.reset();
                appInterface.prepareDocumentWhenDocLoaded();
            }

            if(page == mPager.getCurrentItem()){
                mTimeLastPageSwitch = System.currentTimeMillis();
                clearNextPageTimer();
                WebAppInterface.stopAllAudio();
                if (appInterface != null)
                    appInterface.startNarrationWhenDocLoaded();
            }
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh){
            // if width is zero, this method will be called again
            if (w != 0) {
                if (scale == 0) {
                    scale = getPageScale(w, h);
                    setInitialScale(scale);
                }
                // boolean deviceInLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;  // This method seems to lag and give wrong results
                boolean deviceInLandscape = w > h;
                boolean pageInLandscape = data.contains("Device16x9Landscape");
                if (deviceInLandscape != pageInLandscape) {
                    reloadPage(deviceInLandscape);

                    // The viewport ratio can change with orientation changes when the Back/Home/Recent menu moves to a different side
                    // Unfortunately we can't rescale a Webview once it's been scaled, so if we want to fix this, we'll have to make a new Webview
                    if (scale > getPageScale(w, h))
                        Log.w("BloomScale", "The viewport size has changed and now the page is too big. Some elements may go off-screen.");
                }
            }
            super.onSizeChanged(w, h, ow, oh);
        }

        private float mXLocationForActionDown;
        private float mYLocationForActionDown;

        // After trying many things this is the only approach that worked so far for detecting
        // a tap on the window. Things I tried:
        // - setOnClickListener on the ViewPager. This is known not to work, e.g.,
        // https://stackoverflow.com/questions/21845779/onclick-on-view-pager-in-android-does-not-work-in-my-code
        // - the ClickableViewPager described as an answer there (captures move events, but for
        // no obvious reason does not capture down and up or detect clicks)
        // - setOnClickListener on the WebView. This is also known not to work.
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                mXLocationForActionDown = event.getX();
                mYLocationForActionDown = event.getY();
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                ViewConfiguration viewConfiguration = ViewConfiguration.get(getContext());
                if (Math.abs(event.getX() - mXLocationForActionDown) > viewConfiguration.getScaledTouchSlop() ||
                        Math.abs(event.getY() - mYLocationForActionDown) > viewConfiguration.getScaledTouchSlop()) {
                    // We don't want to register a touch if the user is swiping.
                    // Without this, we had some bizarre behavior wherein the user could swipe slightly more
                    // vertical distance than horizontal and cause a play/pause event.
                    // See https://issues.bloomlibrary.org/youtrack/issue/BL-5068.
                } else if (event.getEventTime() - event.getDownTime() < viewConfiguration.getJumpTapTimeout()) {
                    if (mCurrentView != null && mCurrentView.getWebAppInterface() != null) {
                        WebAppInterface appInterface = mCurrentView.getWebAppInterface();
                        appInterface.setPaused(!WebAppInterface.isNarrationPaused());
                        narrationPausedChanged();

                    }
                }
            }
            return super.onTouchEvent(event);
        }
    }
}