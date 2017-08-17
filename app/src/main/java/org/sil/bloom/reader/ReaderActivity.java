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

    private static final Pattern sLayoutPattern = Pattern.compile("\\S+([P|p]ortrait|[L|l]andscape)$");
    // Matches a div with class bloom-page, that is, the start of the main content of one page.
    // (We're looking for the start of a div tag, then before finding the end wedge, we find
    // class<maybe space>=<maybe space><some sort of quote> and then bloom-page before we find
    // another quote.
    // Bizarre mismatches are possible...like finding the other sort of quote inside the class
    // value, or finding class='bloom-page' inside the value of some other attribute. But I think
    // it's good enough.)
    private static final Pattern sPagePattern = Pattern.compile("<div\\s+[^>]*class\\s*=\\s*['\"][^'\"]*bloom-page");

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
                File bookFolder = new File(bookStagingPath).listFiles(new FileFilter() {
                    @Override
                    public boolean accept(File file) {
                        return file.isDirectory();
                    }
                })[0]; // TODO check assumption that there is exactly one folder
                new Loader().execute(bookFolder.getAbsolutePath());
            } catch (IOException err) {

                Toast.makeText(this.getApplicationContext(), "There was an error showing that book: " + err, Toast.LENGTH_LONG);
            }
        } else {
            new Loader().execute(path); // during stylesheet development, it's nice to be able to work with a folder rather than a zip
        }
    }

    // class to run loadBook in the background (so the UI thread is available to animate the progress bar)
    private class Loader extends AsyncTask<String, Integer, Long> {

        @Override
        protected Long doInBackground(String... paths) {
            loadBook(paths[0]);
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


    private void loadBook(String path) {

        File bookFolder = new File(path);
        File bookHtmlPath = new File(path + File.separator + bookFolder.getName() + ".htm");
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
        // For efficiency we assume a simple class attribute delimited with double quote.
        // If this is in doubt we will need to either improve Bloom to enforce it
        // or make this code smarter. But I think it's how Tidy formats things.
        String classMarker = "class=\"";
        int start = page.indexOf(classMarker) + classMarker.length();
        int end = page.indexOf('"', start);
        String classNames = page.substring(start, end);
        String newClassNames = sLayoutPattern.matcher(classNames).replaceFirst(newLayout);
        return page.substring(0,start) + newClassNames + "\" style=\"" + style + page.substring(end, page.length());
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
        String mFolderPath;
        WebView mNextPageContentControl;
        int mNextPageIndex;
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
            mNextPageIndex = -1;
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

            WebView browser = null;
            synchronized (this) { // lock all access to these two variables.
                if (position == mNextPageIndex) {
                    browser = mNextPageContentControl;
                }
            }
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
                synchronized (this) {
                    // We just went back a page. Keep the current page as 'next'.
                    if (mThisPageIndex == position + 1) {
                        mNextPageIndex = mThisPageIndex;
                        mNextPageContentControl = mThisPageContentControl;
                    }
                }
            }
            mThisPageContentControl = browser;
            mThisPageIndex = position;

            // If relevant start a process to get the next page the user is likely to want.
            boolean needToGetNextPage = false;
            synchronized (this) {
                needToGetNextPage = mNextPageIndex != position + 1 && position < mHtmlPageDivs.size() - 1;
            }
            if (needToGetNextPage) {
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
                        synchronized (BookPagerAdapter.this) {
                            if (mNextPageIndex != position) {
                                mNextPageIndex = position;
                                mNextPageContentControl = browser;
                            }
                        }
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
