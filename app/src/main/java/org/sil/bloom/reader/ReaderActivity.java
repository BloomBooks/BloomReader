package org.sil.bloom.reader;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.webkit.WebView;

import java.io.File;


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
        File file = new File(path);
        WebView browser = (WebView) findViewById(R.id.browser);
        browser.getSettings().setJavaScriptEnabled(true);
        if (file.isDirectory()) {
            browser.loadUrl("file:///" + file.getAbsolutePath() + "/" + file.getName() + ".htm");
        } else { // just a dummy file thing
            browser.loadUrl("file:///" + file.getAbsolutePath());
        }
    }
}
