package org.sil.bloom.reader;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sil.bloom.reader.models.Book;


public class ReaderActivity extends BaseActivity {

    private static final Pattern sLayoutPattern = Pattern.compile("\\S+([P|p]ortrait|[L|l]andscape)\\b");
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


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        //}

        setContentView(R.layout.activity_reader);
//        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
//        setSupportActionBar(toolbar);

        Intent intent = getIntent();
        String path = intent.getData().getPath();
        if (path.toLowerCase().endsWith(Book.BOOK_FILE_EXTENSION)) { //.bloomd files are zip files
            try {

                String bookStagingPath = unzipBook(path);
                String filenameWithExtension = new File(path).getName();
                // strip off the extension (which we already know exactly)
                String bookName = filenameWithExtension.substring(0, filenameWithExtension.length() - Book.BOOK_FILE_EXTENSION.length());

                new Loader().execute(bookStagingPath, bookName);
            } catch (IOException err) {

                Toast.makeText(this.getApplicationContext(), "There was an error showing that book: " + err, Toast.LENGTH_LONG);
            }
        } else {
            new Loader().execute(path, new File(path).getName()); // during stylesheet development, it's nice to be able to work with a folder rather than a zip
        }
    }

    // class to run loadBook in the background (so the UI thread is available to animate the progress bar)
    private class Loader extends AsyncTask<String, Integer, Long> {

        @Override
        protected Long doInBackground(String... args) {
            loadBook(args[0], args[1]);
            return 0L;
        }
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

    private String unzipBook(String zipPath) throws IOException {
        File bookStagingDir = this.getApplicationContext().getDir("currentbook", Context.MODE_PRIVATE);
        IOUtilities.emptyDirectory(bookStagingDir);
        IOUtilities.unzip(new File(zipPath), bookStagingDir);
        return bookStagingDir.getAbsolutePath();
    }


    private void loadBook(String path, String bookName) {

        File bookFolder = new File(path);
        File bookHtmlPath = new File(path + File.separator + bookName + ".htm");
        try {
            long start = System.currentTimeMillis();
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(bookHtmlPath),"UTF-8"));
            // an infuriatingly inefficient way to read the file into a string.
            StringBuilder sb = new StringBuilder((int)bookHtmlPath.length());
            try {
                String line = reader.readLine();
                while (line != null) {
                    sb.append(line);
                    sb.append("\n");
                    line = reader.readLine();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            String html = sb.toString();
            long endTime = System.currentTimeMillis();
            long timeToReadFile = endTime - start;

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
                int startPage = firstPageIndex;
                while (matcher.find()) {
                    pages.add(html.substring(startPage, matcher.start()));
                    startPage = matcher.start();
                }
                int endBody = html.indexOf("</body>", startPage);
                pages.add(html.substring(startPage, endBody));
                endFrame = html.substring(endBody, html.length());
            }
            long endTime2 = System.currentTimeMillis();
            long timeToParse = endTime2 - endTime;

            mAdapter = new BookPagerAdapter(pages, this, bookHtmlPath, startFrame, endFrame, path);

        } catch (IOException ex) {
            Log.e("Reader", "IO Error loading " + path + "  " + ex);
            return;
        } catch (Exception ex) {
            Log.e("Reader", "Error loading " + path + "  " + ex);
            return;
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mPager = (ViewPager) findViewById(R.id.book_pager);
                mPager.setAdapter(mAdapter);
                // Now we're ready to display the book, so hide the 'progress bar' (spinning circle)
                findViewById(R.id.loadingPanel).setVisibility(View.GONE);
            }
        });

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

    // Wraps access to what would otherwise be two variables of BookPagerAdapter and ensures
    // all access to them is synchronized. This is necessary because we access them both in the
    // UI thread and in a background thread used to create the next page view while
    // displaying the current one.
    private class NextPageWrapper
    {
        WebView mNextPageContentControl;
        int mNextPageIndex = -1; // flag value indicating we don't have a next page.

        // IF the known next page is the one wanted, return it. Otherwise null.
        WebView getBrowserIfForPage(int position) {
            synchronized (this) {
                if (position == mNextPageIndex) {
                    return mNextPageContentControl;
                }
            }
            return null;
        }

        void setNextPage(WebView nextPageControl, int nextPageIndex) {
            synchronized (this) {
                if (mNextPageIndex == nextPageIndex)
                    return; // already have page for this index, no need to replace it.
                mNextPageContentControl = nextPageControl;
                mNextPageIndex = nextPageIndex;
            }
        }

        // It's reasonable to get this without synchronization as long as there's no
        // independent access to the page control that assumes another thread hasn't changed
        // it meanwhile. For example, to decide whether to kick off creation of a next page control.
        // Please do NOT add a similar method that gives unsynchronized access to the control,
        // it's asking for trouble to access that without synchronizing access to the page index.
        int getNextPageIndex() {
            return mNextPageIndex;
        }
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
        String mFolderPath;
        NextPageWrapper mNextPageWrapper = new NextPageWrapper();
        WebView mLastPageContentControl;
        int mLastPageIndex;
        int mThisPageIndex;
        WebView mThisPageContentControl;

        BookPagerAdapter(List<String> htmlPageDivs, ReaderActivity parent, File bookHtmlPath, String htmlBeforeFirstPageDiv, String htmlAfterLastPageDiv, String folderPath) {
            mHtmlPageDivs = htmlPageDivs;
            mParent = parent;
            mBookHtmlPath = bookHtmlPath;
            mHtmlBeforeFirstPageDiv = htmlBeforeFirstPageDiv;
            mHtmlAfterLastPageDiv = htmlAfterLastPageDiv;
            mFolderPath = folderPath;
            mLastPageIndex = -1;
            mThisPageIndex = -1;
        }

        @Override
        public void destroyItem(ViewGroup collection, int position, Object view) {
            Log.d("Reader", "destroyItem");
            collection.removeView((View) view);
        }


        @Override
        public int getCount() {
            //Log.d("Reader", "getCount = "+ mHtmlPageDivs.size());
            return mHtmlPageDivs.size();
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            Log.d("Reader", "instantiateItem");

            WebView browser = mNextPageWrapper.getBrowserIfForPage(position); // leaves null if not already created
            if (browser == null && position == mLastPageIndex) {
                browser = mLastPageContentControl;
            } else if (position == mThisPageIndex) {
                browser = mThisPageContentControl;
            } else {
                browser = MakeBrowserForPage(position);
            }
            if (mThisPageIndex == position - 1) {
                // we just advanced; current 'this' page becomes last page
                mLastPageIndex = mThisPageIndex;
                mLastPageContentControl = mThisPageContentControl;
            } else {
                // We just went back a page. Keep the current page as 'next'.
                if (mThisPageIndex == position + 1) {
                    mNextPageWrapper.setNextPage(mThisPageContentControl, mThisPageIndex);
                }
            }
            mThisPageContentControl = browser;
            mThisPageIndex = position;

            // If relevant start a process to get the next page the user is likely to want.
            if ( mNextPageWrapper.getNextPageIndex() != position + 1 && position < mHtmlPageDivs.size() - 1) {
                new PageMaker().execute(position + 1);
            }

            container.addView(browser);
            return browser;
        }

        // class to manage async execution of work to get new page.
        // unfortunately it isn't very async since most of the work has to be done on the UI thread.
        // So at this point we might be better served by just posting the task.
        // But at some point we may figure out parts of this that can really be done in the
        // background. Then again, now that we're making pages that don't contain the
        // whole book with all but one page hidden we might not need to.
        private class PageMaker extends AsyncTask<Integer, Integer, Long> {

            @Override
            protected Long doInBackground(Integer[] positions) {
                final int position = positions[0]; // expect to be passed exactly one
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        WebView browser =  MakeBrowserForPage(position);
                        BookPagerAdapter.this.mNextPageWrapper.setNextPage(browser, position);
                    }
                });
                return 0L;
            }
        }

        private WebView MakeBrowserForPage(int position) {
            WebView browser = null;
            try {
                browser = new ScaledWebView(mParent);
                String page = mHtmlPageDivs.get(position);
                // Inserts the layout class we want and forces no border.
                page = modifyPage(page, "Device16x9Portrait", "border:0 !important");
                String doc = mHtmlBeforeFirstPageDiv + page + mHtmlAfterLastPageDiv;

                browser.loadDataWithBaseURL("file:///" + mBookHtmlPath.getAbsolutePath(), doc, "text/html", "utf-8", null);

            }catch (Exception ex) {
                Log.e("Reader", "Error loading " + mFolderPath + "  " + ex);
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
    }
}
