package org.sil.bloom.reader;

import android.app.Activity;
import android.content.Context;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;

// This class is used by BloomLibraryActivity to configure its WebView. It passes on the page title
// when the page is loaded and overrides another method to prevent the system from loading certain
// URLs in the default system browser instead of within the webview.
public class BloomLibraryWebViewClient extends WebViewClient {

    Activity mOwner;
    public BloomLibraryWebViewClient(Activity owner) {
        mOwner = owner;
    }

    // Without this, or at least without this class, we get an instance of Chrome loaded.
    // Review: are there any links that we want to launch a browser? Maybe if URL does not
    // start with bloomlibrary.org/reader we should return true? e.g., might we want to open the
    // Full details view in Chrome? Or some of the links in book details?
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        //return super.shouldOverrideUrlLoading(view, request);
        return false;
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        mOwner.setTitle(view.getTitle());
    }
}