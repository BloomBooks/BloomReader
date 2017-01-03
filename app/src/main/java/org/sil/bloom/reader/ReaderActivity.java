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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
        try {
            String zipPath = intent.getStringExtra("PATH");
            String bookStagingPath = unzipBook(zipPath);
            File bookFolder = new File(bookStagingPath).listFiles(new FileFilter() {
                @Override
                public boolean accept(File file) {
                    return file.isDirectory();
                }
            })[0]; // TODO check assumption that there is exactly one folder
            loadBook(bookFolder.getAbsolutePath());
        }
        catch(IOException err){

            Toast.makeText(this.getApplicationContext(), "There was an error showing that book: " + err, Toast.LENGTH_LONG);
        }
    }

    private String unzipBook(String zipPath) throws IOException {
        File bookStagingDir = this.getApplicationContext().getDir("currentbook", Context.MODE_PRIVATE);
        emptyDirectory(bookStagingDir);
        unzip(new File(zipPath), bookStagingDir);
        return bookStagingDir.getAbsolutePath();
    }

    void emptyDirectory(File dir) {
            for (File child : dir.listFiles())
                deleteFileOrDirectory(child);
    }
    void deleteFileOrDirectory(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory())
            for (File child : fileOrDirectory.listFiles())
                deleteFileOrDirectory(child);
        fileOrDirectory.delete();
    }

    private void loadBook(String path) {
        mBrowsers = new ArrayList<View>();

        File bookFolder = new File(path);
        File bookHtml = new File(path + File.separator + bookFolder.getName() + ".htm");
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


    //from http://stackoverflow.com/a/27050680
    public static void unzip(File zipFile, File targetDirectory) throws IOException {
        ZipInputStream zis = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(zipFile)));
        try {
            ZipEntry ze;
            int count;
            byte[] buffer = new byte[8192];
            while ((ze = zis.getNextEntry()) != null) {
                File file = new File(targetDirectory, ze.getName());
                File dir = ze.isDirectory() ? file : file.getParentFile();
                if (!dir.isDirectory() && !dir.mkdirs())
                    throw new FileNotFoundException("Failed to ensure directory: " +
                            dir.getAbsolutePath());
                if (ze.isDirectory())
                    continue;
                FileOutputStream fout = new FileOutputStream(file);
                try {
                    while ((count = zis.read(buffer)) != -1)
                        fout.write(buffer, 0, count);
                } finally {
                    fout.close();
                }
            /* if time should be restored as well
            long time = ze.getTime();
            if (time > 0)
                file.setLastModified(time);
            */
            }
        } finally {
            zis.close();
        }
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
