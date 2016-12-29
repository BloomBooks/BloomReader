package org.sil.bloom.reader;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.webkit.MimeTypeMap;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringBufferInputStream;


public class ReaderActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reader);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        Intent intent = getIntent();
        loadBook(intent.getStringExtra("PATH"));
    }

    private void loadBook(String path) {
        WebView browser = (WebView) findViewById(R.id.browser);
        browser.getSettings().setJavaScriptEnabled(true);
        File target = new File(path);
        updateSupportFiles(path);
        browser.loadUrl("file:///" + target.getAbsolutePath() + "/" + target.getName() + ".htm");
    }

    // Copy in files like bloomPlayer.js
    private void updateSupportFiles(String bookFolderPath) {
        AssetCopier.copyAssetFolder(this.getApplicationContext().getAssets(), "book support files", bookFolderPath);
    }
}
