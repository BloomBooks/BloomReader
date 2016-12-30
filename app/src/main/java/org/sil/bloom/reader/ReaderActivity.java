package org.sil.bloom.reader;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import java.io.File;
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
        loadBook(intent.getStringExtra("PATH"));
    }


    private void loadBook(String path) {
        mBrowsers = new ArrayList<View>();

        File bookFolder = new File(path);
        File bookHtml = new File(path + "/" + bookFolder.getName() + ".htm");
        try {
            Document doc = Jsoup.parse(bookHtml, "UTF-8", "");
            Element datadiv = doc.select("div#bloomDataDiv").first();
            datadiv.remove();
            Elements pages = doc.select("div.bloom-page");
            //hide all the pages
            for (Element page : pages) {
                page.attr("style", "display:none");
            }
            for (Element page : pages) {
                WebView browser = new WebView(this);
                page.attr("style", "");
                browser.loadDataWithBaseURL("file:///"+bookHtml.getAbsolutePath(), doc.outerHtml(), "text/html", "utf-8", null);
                mBrowsers.add(browser);
                page.attr("style", "display:none"); // return to default hidden
            }
        } catch (IOException ex) {
            Log.e("Reader", "Error loading " + path + "  " + ex);
            return;
        }


        mAdapter = new BookPagerAdapter(mBrowsers);
        mPager = (ViewPager) findViewById(R.id.book_pager);
        mPager.setAdapter(mAdapter);
    }



    // Copy in files like bloomPlayer.js
    private void updateSupportFiles(String bookFolderPath) {
        AssetCopier.copyAssetFolder(this.getApplicationContext().getAssets(), "book support files", bookFolderPath);
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
