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

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;



public class ReaderActivity extends AppCompatActivity {

    private ViewPager mPager;
    private BookPagerAdapter mAdapter;
    private List<View> mBrowsers;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reader);
//        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
//        setSupportActionBar(toolbar);

        Intent intent = getIntent();
        String path = intent.getData().getPath();
        if (path.toLowerCase().endsWith(".bloom")) { //.bloom files are zip files
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
                WebView browser = new WebView(this);
                page.attr("style", "");
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
            container.addView(mBrowsers.get(position));
            return mBrowsers.get(position);
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            Log.d("Reader", "isViewFromObject = " + (view == object));
            return view == object;
        }
    }
}
