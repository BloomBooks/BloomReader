package org.sil.bloom.reader;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Toast;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.sil.bloom.reader.models.Book;


public class ReaderActivity extends BaseActivity {

    private static final Pattern sLayoutPattern = Pattern.compile("\\S+([P|p]ortrait|[L|l]andscape)$");

    private ViewPager mPager;
    private BookPagerAdapter mAdapter;
    private List<View> mBrowsers;


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
                loadBook(bookFolder.getAbsolutePath());
            } catch (IOException err) {

                Toast.makeText(this.getApplicationContext(), "There was an error showing that book: " + err, Toast.LENGTH_LONG);
            }
        } else {
            loadBook(path); // during stylesheet development, it's nice to be able to work with a folder rather than a zip
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
        mBrowsers = new ArrayList<View>();

        File bookFolder = new File(path);
        File bookHtmlPath = new File(path + File.separator + bookFolder.getName() + ".htm");
        try {
            //review: so we need an overload of Jsoup.parse() that throws parse errors?
            Document doc = Jsoup.parse(bookHtmlPath, "UTF-8", "");
            // the first big div is metadata, not a page. Just throw it away.
            Element datadiv = doc.select("div#bloomDataDiv").first();
            datadiv.remove();

            //hide all the pages
            Elements pages = doc.select("div.bloom-page");
            for (Element page : pages) {
                page.attr("style", "display:none");
            }
            //make a browser for each remaining page. Enhance: that's probably not the
            //best way to go about this...
            for (Element page : pages) {
                WebView browser = new ScaledWebView(this);

                modifyLayout(page, "Device16x9Portrait");

                //makes it visible (removes display:none) and gets rid of the border
                page.attr("style", "border:0 !important");
                browser.loadDataWithBaseURL("file:///"+bookHtmlPath.getAbsolutePath(), doc.outerHtml(), "text/html", "utf-8", null);
                mBrowsers.add(browser);
                page.attr("style", "display:none"); // return to default hidden
            }
        } catch (IOException ex) {
            Log.e("Reader", "IO Error loading " + path + "  " + ex);
            return;
        } catch (Exception ex) {
            Log.e("Reader", "Error loading " + path + "  " + ex);
            return;
        }


        mAdapter = new BookPagerAdapter(mBrowsers);
        mPager = (ViewPager) findViewById(R.id.book_pager);
        mPager.setAdapter(mAdapter);
    }

    private void modifyLayout(Element page, String newLayout) {
        for (String className : page.classNames()) {
            if (sLayoutPattern.matcher(className).matches()) {
                page.removeClass(className);
                page.addClass(newLayout);
            }
        }
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

    private class BookPagerAdapter extends PagerAdapter {

        private List<View> mBrowsers;

        BookPagerAdapter(List<View> browsers) {
            mBrowsers = browsers;
        }

        @Override
        public void destroyItem(ViewGroup collection, int position, Object view) {
            Log.d("Reader", "destroyItem");
            collection.removeView((View) view);
        }


        @Override
        public int getCount() {
            //Log.d("Reader", "getCount = "+ mBrowsers.size());
            return mBrowsers.size();
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            Log.d("Reader", "instantiateItem");
            View browser = mBrowsers.get(position);
            container.addView(browser);
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
