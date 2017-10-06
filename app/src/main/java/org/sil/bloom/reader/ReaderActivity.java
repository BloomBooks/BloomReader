package org.sil.bloom.reader;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.webkit.WebView;
import android.widget.ImageView;

import com.segment.analytics.Analytics;
import com.segment.analytics.Properties;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sil.bloom.reader.models.Book;


public class ReaderActivity extends BaseActivity {

    private static final String TAG = "ReaderActivity";// https://developer.android.com/reference/android/util/Log.html
    private static final String sAssetsStylesheetLink = "<link rel=\"stylesheet\" href=\"file:///android_asset/book support files/assets.css\" type=\"text/css\"></link>";
    private static final String sAssetsBloomPlayerScript = "<script type=\"text/javascript\" src=\"file:///android_asset/book support files/bloomPagePlayer.js\"></script>";
    private static final Pattern sLayoutPattern = Pattern.compile("\\S+([P|p]ortrait|[L|l]andscape)\\b");
    private static final Pattern sHeadElementEndPattern = Pattern.compile("</head");
    // Matches a div with class bloom-page, that is, the start of the main content of one page.
    // (We're looking for the start of a div tag, then before finding the end wedge, we find
    // class<maybe space>=<maybe space><some sort of quote> and then bloom-page before we find
    // another quote.
    // Bizarre mismatches are possible...like finding the other sort of quote inside the class
    // value, or finding class='bloom-page' inside the value of some other attribute. But I think
    // it's good enough.)
    private static final Pattern sPagePattern = Pattern.compile("<div\\s+[^>]*class\\s*=\\s*['\"][^'\"]*bloom-page");

    private static final Pattern sClassAttrPattern = Pattern.compile("class\\s*=\\s*(['\"])(.*?)\\1");

    private ViewPager mPager;
    private BookPagerAdapter mAdapter;
    private String mBookName ="?";
    private BloomFileReader mFileReader;

    // Keeps track of whether we switched pages while audio paused. If so, we don't resume
    // the audio of the previously visible page, but start this page from the beginning.
    boolean mSwitchedPagesWhilePaused = false;
    // These variables support a minimum time on each page before we automatically switch to
    // the next (if the audio on this page is short or non-existent).
    private long mTimeLastPageSwitch;
    private Timer mNextPageTimer;
    private boolean mIsMultiMediaBook;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        //}

        setContentView(R.layout.activity_reader);
//        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
//        setSupportActionBar(toolbar);

        new Loader().execute();
    }

    @Override
    protected void onPause() {
        if (isFinishing()) {
           ReportPagesRead();
        }
        super.onPause();
        WebAppInterface.stopPlaying();
    }

    private void ReportPagesRead()
    {
        try {
            // TODO: I think it will be too complicated to analyze the last-page read.

            // TODO: let's differentiate between pages read and audio book pages read: (BL-5082)
//            Properties p = new Properties();
//            p.putValue("title", mBookName);
//            p.putValue("audioPages", mAdapter.mAudioPagesPlayed);
//            p.putValue("nonAudioPages", mAdapter.mNonAudioPagesShown);
//            Analytics.with(BloomReaderApplication.getBloomApplicationContext()).track("Pages Read", p);

            Analytics.with(BloomReaderApplication.getBloomApplicationContext()).track("Pages Read",
                    new Properties().putValue("title", mBookName).putValue("lastPage", mAdapter.mThisPageIndex));
        } catch (Exception e) {
            Log.e(TAG, "Pages Read", e);
            BloomReaderApplication.VerboseToast("Error reporting Pages Read");
        }
    }

    // class to run loadBook in the background (so the UI thread is available to animate the progress bar)
    private class Loader extends AsyncTask<Void, Integer, Long> {

        @Override
        protected Long doInBackground(Void... v) {
            loadBook();
            return 0L;
        }
    }

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

        // For now, we have decided we want to force the user to initiate page turning always (BL-5067).
        if (true)
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
        if (!mIsMultiMediaBook)
            return;
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
        if (!mIsMultiMediaBook)
            return; // no media visual effects.
        final ImageView view = (ImageView)findViewById(R.id.playPause);
        if (WebAppInterface.isNarrationPaused()) {
            clearNextPageTimer(); // any pending automatic page flip should be prevented.
            view.setImageResource(R.drawable.pause_on_circle); // black circle around android.R.drawable.ic_media_pause);
        } else {
            view.setImageResource(R.drawable.play_on_circle);
            if(mSwitchedPagesWhilePaused) {
                mAdapter.startNarrationForPage(mPager.getCurrentItem());
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
            mBookName = filenameWithExtension.substring(0, filenameWithExtension.length() - Book.BOOK_FILE_EXTENSION.length());
            Analytics.with(BloomReaderApplication.getBloomApplicationContext()).track("Book opened", new Properties().putValue("title", mBookName));
        } catch (Exception error) {
            Log.e("Reader", "Error reporting load of " + path + ".  "+ error);
            BloomReaderApplication.VerboseToast("Error reporting load of "+path);
        }
    }

    private void loadBook() {
        String path = getIntent().getData().getPath();
        reportLoadBook(path);
        mFileReader = new BloomFileReader(getApplicationContext(), path);
        try {
            File bookHtmlFile = mFileReader.getHtmlFile();
            String html = IOUtilities.FileToString(bookHtmlFile);
            // Enhance: eventually also look for images with animation data.
            // This is a fairly crude search, we really want the doc to have spans with class
            // audio-sentence; but I think it's a sufficiently unlikely string to find elsewhere
            // that this is good enough.
            mIsMultiMediaBook = html.indexOf("audio-sentence") >= 0;
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
                startFrame = addAssetsStylesheetLink(startFrame);
                int startPage = firstPageIndex;
                while (matcher.find()) {
                    pages.add(html.substring(startPage, matcher.start()));
                    startPage = matcher.start();
                }
                int endBody = html.indexOf("</body>", startPage);
                pages.add(html.substring(startPage, endBody));
                // We can leave out the bloom player JS altogether if not needed.
                endFrame = (mIsMultiMediaBook ? sAssetsBloomPlayerScript : "")
                        + html.substring(endBody, html.length());
            }

            mAdapter = new BookPagerAdapter(pages, this, bookHtmlFile, startFrame, endFrame);

        } catch (Exception ex) {
            Log.e("Reader", "Error loading " + path + "  " + ex);
            return;
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mPager = (ViewPager) findViewById(R.id.book_pager);
                mPager.setAdapter(mAdapter);
                final ViewPager.SimpleOnPageChangeListener listener = new ViewPager.SimpleOnPageChangeListener() {
                    @Override
                    public void onPageSelected(int position) {
                        super.onPageSelected(position);
                        clearNextPageTimer(); // in case user manually moved to a new page while waiting
                        mTimeLastPageSwitch = System.currentTimeMillis();
                        mSwitchedPagesWhilePaused = WebAppInterface.isNarrationPaused();
                        WebAppInterface.stopPlaying(); // don't want to hear rest of anything on another page
                        if (!WebAppInterface.isNarrationPaused() && mIsMultiMediaBook) {
                            mAdapter.startNarrationForPage(position);
                        }
                    }
                };
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
        });

    }

    private String addAssetsStylesheetLink(String htmlSnippet) {
        final Matcher matcher = sHeadElementEndPattern.matcher(htmlSnippet);
        if (matcher.find()) {
            return htmlSnippet.substring(0, matcher.start()) + sAssetsStylesheetLink + htmlSnippet.substring(matcher.start());
        }
        return htmlSnippet;
    }

    // Forces the layout we want into the class, and inserts the specified style.
    // assumes some layout is already present in classes attribute, and no style already exists.
    private String modifyPage(String page, String newLayout, String style) {
        // Get the content of the class attribute and its position
        Matcher matcher = sClassAttrPattern.matcher(page);
        if (!matcher.find())
            return page; // don't think this can happen, we create pages by finding class attr.
        int start = matcher.start(2);
        int end = matcher.end(2);
        String classNames = matcher.group(2);
        String newClassNames = sLayoutPattern.matcher(classNames).replaceFirst(newLayout);
        return page.substring(0,start) // everything up to the opening quote in class="
                + newClassNames
                + matcher.group(1) // proper matching closing quote ends class attr
                + " style="
                + matcher.group(1) // we need to use the same quote for style...
                + style
                + page.substring(end, page.length()); // because this includes the original closing quote from class attr
    }

    private int getPageScale(int viewWidth, int viewHeight){
        //we'll probably want to read or calculate these at some point...
        //but for now, they are the width and height of the Device16x9Portrait layout
        int bookPageWidth = 378;
        int bookPageHeight = 674;

        Double widthScale = new Double(viewWidth)/new Double(bookPageWidth);
        Double heightScale = new Double(viewHeight)/new Double(bookPageHeight);
        Double scale = Math.min(widthScale, heightScale);
        scale = scale * 100d;
        return scale.intValue();
    }

    // Copy in files like bloomPlayer.js
    private void updateSupportFiles(String bookFolderPath) {
        IOUtilities.copyAssetFolder(this.getApplicationContext().getAssets(), "book support files", bookFolderPath);
    }

    // Class that provides individual page views as needed.
    // possible enhancement: can we reuse the same browser, just change which page is visible?
    private class BookPagerAdapter extends PagerAdapter {
        // Each item is the full HTML text of one page div (div with class bloom-page).
        // the concatenation of mHtmlBeforeFirstPageDiv, mHtmlPageDivs, and mHtmlAfterLastPageDiv
        // is the whole book HTML file. (The code here assumes there is nothing in the body
        // of the document except page divs.)
        // The concatenation of mHtmlBeforeFirstPageDiv, one item from mHtmlPageDivs, and
        // mHtmlAfterLastPageDiv is the content we put in a browser representing a single page.
        private List<String> mHtmlPageDivs;
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
                         ReaderActivity parent,
                         File bookHtmlPath,
                         String htmlBeforeFirstPageDiv,
                         String htmlAfterLastPageDiv) {
            mHtmlPageDivs = htmlPageDivs;
            mParent = parent;
            mBookHtmlPath = bookHtmlPath;
            mHtmlBeforeFirstPageDiv = htmlBeforeFirstPageDiv;
            mHtmlAfterLastPageDiv = htmlAfterLastPageDiv;
            mLastPageIndex = -1;
            mThisPageIndex = -1;
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

            WebView browser = MakeBrowserForPage(position);

            assert(container.getChildCount() == 0);
            container.addView(browser);
            return browser;
        }

        // position should in fact be the position of the pager.
        public ScaledWebView getActiveView(int position) {
            return mActiveViews.get(position);
        }

        public void startNarrationForPage(int position) {
            WebView pageView = mActiveViews.get(position);
            if (pageView == null) {
                Log.d("startNarration", "can't find page for " + position);
                return;
            }
            WebAppInterface appInterface = (WebAppInterface)pageView.getTag();
            appInterface.startNarrationWhenDocLoaded();
        }

        private WebView MakeBrowserForPage(int position) {
            ScaledWebView browser = null;
            try {
                browser = new ScaledWebView(mParent);
                mActiveViews.put(position, browser);
                if (mIsMultiMediaBook) {
                    browser.getSettings().setJavaScriptEnabled(true); // allow Javascript for audio player
                    WebAppInterface appInterface = new WebAppInterface(this.mParent, mBookHtmlPath.getParent(), browser, position);
                    browser.addJavascriptInterface(appInterface, "Android");
                    // Save the WebAppInterface in the browser's tag because there's no simple
                    // way to get from the browser to the object we set as the JS interface.
                    browser.setTag(appInterface);
                }
                String page = mHtmlPageDivs.get(position);
                // Inserts the layout class we want and forces no border.
                page = modifyPage(page, "Device16x9Portrait", "border:0 !important");
                String doc = mHtmlBeforeFirstPageDiv + page + mHtmlAfterLastPageDiv;

                browser.loadDataWithBaseURL("file:///" + mBookHtmlPath.getAbsolutePath(), doc, "text/html", "utf-8", null);
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

        public ScaledWebView(Context context) {
            super(context);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {

            // if width is zero, this method will be called again
            if (w != 0) {
                setInitialScale(getPageScale(w, h));
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
                    WebAppInterface.playPause(!WebAppInterface.isNarrationPaused());
                    narrationPausedChanged();
                }
            }
            return super.onTouchEvent(event);
        }
    }
}
